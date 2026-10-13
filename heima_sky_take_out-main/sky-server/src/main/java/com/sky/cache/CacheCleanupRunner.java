package com.sky.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * sky-server 启动后自动清理"业务缓存"（dish/setmeal/report/shop）。
 * <p>
 * 为什么需要：Redis 中的业务缓存键（dish_1、setmealCache:1 等）在 sky-server
 * 重启后不会自动清除——如果有人在服务停机期间直接改过数据库（SQL 脚本/迁移工具/
 * DBA 操作），新实例启动后第一个用户请求会命中旧缓存，读到过期的 image 路径
 * 等脏数据。
 * <p>
 * 保留的业务数据键（不清）：cart:{userId}、order:seq:{date}、order:expire、
 * order:submit:lock:*、login:lock:*、chat:*——这些是购物车/订单序号/登录锁定等
 * 有实际业务意义的数据，不能在启动时抹掉。
 * <p>
 * 通过 sky.cache.cleanup-on-startup=false 可关闭（application.yml 里配置），
 * 方便测试环境或需要 Redis 跨重启持久化缓存的场景。
 */
@Slf4j
@Component
@Order(1) // 在其他 CommandLineRunner 之前执行
public class CacheCleanupRunner implements CommandLineRunner {

    @Autowired
    private CacheClient cacheClient;

    @org.springframework.beans.factory.annotation.Value("${sky.cache.cleanup-on-startup:true}")
    private boolean cleanupOnStartup;

    @Override
    public void run(String... args) {
        if (!cleanupOnStartup) {
            log.info("[启动清缓存] 已通过配置 sky.cache.cleanup-on-startup=false 关闭");
            return;
        }
        try {
            cacheClient.clearAllBusinessCaches();
        } catch (Exception e) {
            // 清缓存失败不阻塞启动（Redis 可能还没就绪），让熔断兜底
            log.warn("[启动清缓存] 执行异常（不影响启动）：{}", e.getMessage());
        }
    }
}
