package com.sky.controller.admin;

import com.sky.cache.ShopStatusLocalCache;
import com.sky.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/shop")
@Api(tags = "店铺相关接口")
@Slf4j
public class ShopController {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ShopStatusLocalCache shopStatusLocalCache;

    /**
     * 修改店铺营业状态
     * @param status
     * @return
     */
    @PutMapping("/{status}")
    @ApiOperation("设置店铺营业状态")
    public Result setStatus(@PathVariable Integer status) {
        log.info("设置店铺营业状态:{}", status == 1 ? "营业" : "打烊");
        redisTemplate.opsForValue().set("SHOP_STATUS", status);
        // 同步刷新 JVM 本地缓存，单实例下 C 端无需等待 TTL 即可读到最新状态
        shopStatusLocalCache.put(status);
        return Result.success();
    }

    @GetMapping("/status")
    @ApiOperation("获取店铺营业状态")
    public Result<Integer> getStatus() {
        Integer status = (Integer) redisTemplate.opsForValue().get("SHOP_STATUS");
        log.info("获取店铺营业状态:{}", status == 1 ? "营业" : "打烊");
        return Result.success(status);
    }
}
