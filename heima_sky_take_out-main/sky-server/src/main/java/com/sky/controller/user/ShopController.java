package com.sky.controller.user;

import com.sky.cache.ShopStatusLocalCache;
import com.sky.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C端-店铺状态接口
 */
@RestController("userShopController")
@RequestMapping("/user/shop")
@Api(tags = "C端-店铺接口")
@Slf4j
public class ShopController {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ShopStatusLocalCache shopStatusLocalCache;

    @GetMapping("/status")
    @ApiOperation("获取店铺营业状态")
    public Result<Integer> getStatus() {
        // 先读 JVM 本地缓存（10秒TTL），命中直接返回
        Integer status = shopStatusLocalCache.get();
        if (status != null) {
            return Result.success(status);
        }
        try {
            // 本地未命中，回源 Redis 并回填
            status = (Integer) redisTemplate.opsForValue().get("SHOP_STATUS");
            // 未设置时默认营业中
            if (status == null) {
                status = 1;
            }
            shopStatusLocalCache.put(status);
            log.info("本地缓存未命中，回源Redis获取店铺营业状态：{}", status == 1 ? "营业中" : "已打烊");
        } catch (Exception e) {
            // Redis 不可用时降级：使用本地陈旧值兜底，实在没有则默认营业，不让C端报错
            log.error("Redis不可用，店铺状态降级为本地陈旧值/默认营业", e);
            status = shopStatusLocalCache.getStale();
            if (status == null) {
                status = 1;
            }
        }
        return Result.success(status);
    }
}
