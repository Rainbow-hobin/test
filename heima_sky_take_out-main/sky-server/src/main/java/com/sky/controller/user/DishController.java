package com.sky.controller.user;

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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController("userDishController")
@RequestMapping("/user/dish")
@Slf4j
@Api(tags = "C端-菜品浏览接口")
public class DishController {
    @Autowired
    private DishService dishService;

    // 本地简单缓存，练习项目不依赖 Redis
    private static final Map<String, Object> DISH_CACHE = new HashMap<>();

    /**
     * 清理本地菜品缓存（管理端修改菜品后调用）
     */
    public static void clearCache() {
        DISH_CACHE.clear();
    }

    /**
     * 根据分类id查询菜品
     *
     * @param categoryId
     * @return
     */
    @GetMapping("/list")
    @ApiOperation("根据分类id查询菜品")
    public Result<List<DishVO>> list(Long categoryId) {
        // 查询本地缓存中是否有菜品数据
        String key = "dish_" + categoryId;
        List<DishVO> list = (List<DishVO>) DISH_CACHE.get(key);
        if (list != null && !list.isEmpty()) {
            // 如果存在直接返回数据，不查询数据库
            return Result.success(list);
        }

        // 本地缓存中不存在，先从数据库中查询
        Dish dish = new Dish();
        dish.setCategoryId(categoryId);
        dish.setStatus(StatusConstant.ENABLE);//查询起售中的菜品

        list = dishService.listWithFlavor(dish);

        // 将查询的数据放入本地缓存中
        DISH_CACHE.put(key, list);

        return Result.success(list);
    }

}
