package com.sky.cache;

import org.springframework.stereotype.Component;

/**
 * 店铺营业状态 JVM 进程内短缓存（TTL 10秒）
 * <p>
 * 店铺状态是全局热点 key，C 端每次进页面都会读取。用本地缓存挡住绝大多数请求，
 * 避免高频访问 Redis；10 秒 TTL 作为兜底，保证外部直接改 Redis 时也能最终一致。
 * 管理端通过本应用修改状态时会同步刷新该缓存，单实例下立即生效。
 */
@Component
public class ShopStatusLocalCache {

    /** 本地缓存存活时间：10秒 */
    private static final long TTL_MILLIS = 10_000L;

    private volatile Integer status;

    private volatile long expireAt;

    /**
     * 读取本地缓存，未初始化或已过期返回 null（调用方需回源 Redis）
     */
    public Integer get() {
        Integer current = status;
        if (current != null && System.currentTimeMillis() < expireAt) {
            return current;
        }
        return null;
    }

    /**
     * 读取本地陈旧值（忽略 TTL），Redis 不可用时的降级兜底
     */
    public Integer getStale() {
        return status;
    }

    /**
     * 刷新本地缓存
     */
    public void put(Integer status) {
        this.status = status;
        this.expireAt = System.currentTimeMillis() + TTL_MILLIS;
    }
}
