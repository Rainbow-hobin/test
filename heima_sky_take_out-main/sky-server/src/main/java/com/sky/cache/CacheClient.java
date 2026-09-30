package com.sky.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Redis 缓存统一防护组件（Cache-Aside 增强版）
 * <p>
 * 同时解决三类经典缓存问题：
 * <ul>
 *     <li>缓存穿透：DB 查不到数据时写入短 TTL 的空值占位符，拦截对不存在 key 的重复请求；
 *     调用方仍应在入口做参数合法性校验作为第一道防线。</li>
 *     <li>缓存击穿：缓存失效瞬间用 SET NX 互斥锁保证只有一个线程回源重建，
 *     其余线程短暂等待后重试读缓存；锁带 TTL 防死锁，Lua 脚本按 owner 安全释放。</li>
 *     <li>缓存雪崩：正常缓存 TTL 增加 0~10% 随机抖动，避免大量 key 同时过期；
 *     Redis 不可用时所有操作自动降级，业务请求直接查 DB，不向上抛错。</li>
 * </ul>
 */
@Component
@Slf4j
public class CacheClient {

    /** 空值占位符（与业务数据类型不同，命中后由调用方按"空结果"处理） */
    private static final String EMPTY_PLACEHOLDER = "CACHE_EMPTY";

    /** 空值占位符 TTL：2 分钟，既挡住穿透流量，又不会让数据晚于 2 分钟可见 */
    private static final Duration EMPTY_TTL = Duration.ofMinutes(2);

    /** 重建锁 TTL：持锁线程异常退出时自动释放 */
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);

    /** 等锁最大重试次数与间隔（最多等待约 1 秒） */
    private static final int LOCK_RETRY_TIMES = 20;
    private static final Duration LOCK_RETRY_INTERVAL = Duration.ofMillis(50);

    /** TTL 随机抖动比例上限：0 ~ 10% */
    private static final double TTL_JITTER_RATIO = 0.1;

    /** 锁 key 前缀 */
    private static final String LOCK_PREFIX = "lock:";

    /** 故障熔断时长：Redis 操作失败后 5 秒内直接跳过 Redis 走降级，避免每个请求都等待超时 */
    private static final long CIRCUIT_BREAK_MILLIS = 5_000L;

    /** 延迟双删的第二次删除延迟：略大于一次回源写缓存耗时，避免删后又读回旧值 */
    private static final long DELAYED_DELETE_MILLIS = 500L;

    /** 熔断截止时间戳：当前时间小于该值视为熔断打开中（0 表示关闭） */
    private volatile long redisDownUntil = 0L;

    /** 安全释放锁：仅当 value 等于自己的 owner 标识时才删除 */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final RedisTemplate<String, Object> redisTemplate;

    public CacheClient(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 带穿透/击穿/雪崩三重防护的缓存查询。
     *
     * @param key      缓存 key
     * @param ttl      正常数据的基础 TTL（实际写入时附加 0~10% 随机抖动）
     * @param dbLoader 缓存未命中时的回源逻辑
     * @return 缓存/DB 中的数据；DB 查无结果（null 或空集合）时返回 null
     */
    @SuppressWarnings("unchecked")
    public <T> T getWithProtection(String key, Duration ttl, Supplier<T> dbLoader) {
        // 1. 先查缓存（Redis 异常时返回 null，走降级）
        Object cached = safeGet(key);
        if (cached != null) {
            if (EMPTY_PLACEHOLDER.equals(cached)) {
                // 命中空值占位符：这是一个"确认不存在"的结果，直接返回
                return null;
            }
            return (T) cached;
        }

        // 2. 未命中，抢互斥锁防止击穿
        String lockKey = LOCK_PREFIX + key;
        String owner = UUID.randomUUID().toString();
        boolean locked = false;
        try {
            locked = tryLock(lockKey, owner);
            if (!locked) {
                // 没抢到锁：说明别的线程正在重建，等待并重试读缓存
                for (int i = 0; i < LOCK_RETRY_TIMES; i++) {
                    sleep(LOCK_RETRY_INTERVAL.toMillis());
                    cached = safeGet(key);
                    if (cached != null) {
                        return EMPTY_PLACEHOLDER.equals(cached) ? null : (T) cached;
                    }
                }
                // 等待超时（可能重建较慢）：降级直查 DB，不能把业务请求卡死
                log.warn("等待缓存重建锁超时，降级直查数据库：{}", key);
                return dbLoader.get();
            }

            // 3. 拿到锁后双重检查，避免排队期间已被其他线程重建
            cached = safeGet(key);
            if (cached != null) {
                return EMPTY_PLACEHOLDER.equals(cached) ? null : (T) cached;
            }

            // 4. 回源数据库
            T dbValue = dbLoader.get();

            // 5. 写缓存：空结果写短 TTL 占位符防穿透；正常值写抖动 TTL 防雪崩
            if (isEmpty(dbValue)) {
                safeSet(key, EMPTY_PLACEHOLDER, EMPTY_TTL);
            } else {
                safeSet(key, dbValue, jitterTtl(ttl));
            }
            return dbValue;
        } finally {
            if (locked) {
                unlock(lockKey, owner);
            }
        }
    }

    /**
     * 删除单个缓存 key（管理端主动失效用），Redis 异常不影响主流程
     */
    public void delete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.error("删除缓存失败：{}", key, e);
        }
    }

    /**
     * 按通配符批量删除缓存，使用 SCAN 游标实现，避免 KEYS 命令阻塞 Redis。
     */
    public void deleteByPattern(String pattern) {
        try {
            Set<String> keys = redisTemplate.execute((RedisCallback<Set<String>>) connection -> {
                Set<String> result = new HashSet<>();
                ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();
                try (Cursor<byte[]> cursor = connection.scan(options)) {
                    while (cursor.hasNext()) {
                        result.add(new String(cursor.next(), StandardCharsets.UTF_8));
                    }
                }
                return result;
            });
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("批量删除缓存 {} 个key，pattern={}", keys.size(), pattern);
            }
        } catch (Exception e) {
            log.error("批量删除缓存失败：{}", pattern, e);
        }
    }

    /**
     * 延迟双删：第二次删除（单 key），异步延迟执行，不阻塞请求线程。
     * 配合「先删缓存 → 更新数据库」使用，防止更新间隙有读请求把旧库数据写回缓存。
     */
    public void deleteDelayed(String key) {
        CompletableFuture.runAsync(() -> {
            sleep(DELAYED_DELETE_MILLIS);
            delete(key);
        });
    }

    /**
     * 延迟双删：第二次删除（按通配符），异步延迟执行。
     */
    public void deleteByPatternDelayed(String pattern) {
        CompletableFuture.runAsync(() -> {
            sleep(DELAYED_DELETE_MILLIS);
            deleteByPattern(pattern);
        });
    }

    private boolean tryLock(String lockKey, String owner) {
        if (circuitOpen()) {
            // 熔断打开：不加锁直接放行查库，不等待 Redis
            return true;
        }
        try {
            Boolean ok = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, owner, LOCK_TTL);
            return Boolean.TRUE.equals(ok);
        } catch (Exception e) {
            // Redis 不可用时降级：打开熔断、不加锁直接放行查库，保证业务可用
            markCircuitOpen();
            log.error("获取缓存重建锁失败，降级为无锁直查：{}", lockKey, e);
            return true;
        }
    }

    private void unlock(String lockKey, String owner) {
        if (circuitOpen()) {
            return;
        }
        try {
            redisTemplate.execute(UNLOCK_SCRIPT, java.util.Collections.singletonList(lockKey), owner);
        } catch (Exception e) {
            markCircuitOpen();
            log.error("释放缓存重建锁失败：{}", lockKey, e);
        }
    }

    private Object safeGet(String key) {
        if (circuitOpen()) {
            // 熔断打开：当作未命中，直接走数据库降级
            return null;
        }
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            // Redis 故障降级：打开熔断并当作未命中处理，后续直查数据库
            markCircuitOpen();
            log.error("读取缓存失败，降级直查数据库：{}", key, e);
            return null;
        }
    }

    private void safeSet(String key, Object value, Duration ttl) {
        if (circuitOpen()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
        } catch (Exception e) {
            markCircuitOpen();
            log.error("写入缓存失败（不影响本次响应）：{}", key, e);
        }
    }

    /**
     * 熔断是否打开
     */
    private boolean circuitOpen() {
        return System.currentTimeMillis() < redisDownUntil;
    }

    /**
     * 打开熔断：接下来 {@link #CIRCUIT_BREAK_MILLIS} 毫秒内跳过 Redis；
     * 时间过后进入"半开"，下一次请求会试探 Redis，成功则自动恢复。
     */
    private void markCircuitOpen() {
        redisDownUntil = System.currentTimeMillis() + CIRCUIT_BREAK_MILLIS;
    }

    private Duration jitterTtl(Duration base) {
        long baseSeconds = base.getSeconds();
        long jitter = ThreadLocalRandom.current().nextLong(0, (long) (baseSeconds * TTL_JITTER_RATIO) + 1);
        return base.plusSeconds(jitter);
    }

    private boolean isEmpty(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof Collection) {
            return ((Collection<?>) value).isEmpty();
        }
        return false;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
