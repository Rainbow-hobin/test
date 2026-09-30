package com.sky.controller.user;

import com.sky.cache.CacheClient;
import com.sky.constant.StatusConstant;
import com.sky.entity.Dish;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

@RestController("userDishController")
@RequestMapping("/user/dish")
@Slf4j
@Api(tags = "C端-菜品浏览接口")
public class DishController {

    /** 菜品列表缓存基础 TTL：1小时（实际写入附加 0~10% 随机抖动），管理端改菜时会主动失效 */
    private static final Duration DISH_CACHE_TTL = Duration.ofHours(1);

    @Autowired
    private DishService dishService;

    @Autowired
    private CacheClient cacheClient;

    /**
     * 根据分类id查询菜品
     *
     * @param categoryId
     * @return
     */
    @GetMapping("/list")
    @ApiOperation("根据分类id查询菜品")
    public Result<List<DishVO>> list(Long categoryId) {
        // 防穿透第一道防线：非法分类直接返回空，不访问缓存和数据库
        if (categoryId == null || categoryId <= 0) {
            return Result.success(Collections.emptyList());
        }

        String key = "dish_" + categoryId;
        List<DishVO> list = cacheClient.getWithProtection(key, DISH_CACHE_TTL, () -> {
            Dish dish = new Dish();
            dish.setCategoryId(categoryId);
            dish.setStatus(StatusConstant.ENABLE);//查询起售中的菜品
            return dishService.listWithFlavor(dish);
        });

        return Result.success(list == null ? Collections.emptyList() : list);
    }

}
