package com.sky.controller.user;

import com.sky.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C端-店铺状态接口（网页版：内存保存，不依赖 Redis）
 */
@RestController
@RequestMapping("/user/shop")
@Api(tags = "C端-店铺接口")
@Slf4j
public class ShopController {

    // 店铺营业状态，1营业 0打烊（默认营业）
    public static volatile Integer SHOP_STATUS = 1;

    @GetMapping("/status")
    @ApiOperation("获取店铺营业状态")
    public Result<Integer> getStatus() {
        return Result.success(SHOP_STATUS);
    }
}
