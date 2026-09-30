package com.sky.controller.admin;

import com.sky.cache.CacheClient;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.mapper.DishMapper;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/dish")
@Api(tags = "菜品相关接口")
@Slf4j
public class DishController {
    @Autowired
    private DishService dishService;
    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private CacheClient cacheClient;

    @PostMapping
    @ApiOperation("新增菜品")
    public Result save(@RequestBody DishDTO dishDTO) {
        log.info("新增菜品：{}", dishDTO);
        // 延迟双删：先删缓存 → 更新数据库 → 延迟再删，防止读到旧数据
        String cacheKey = "dish_" + dishDTO.getCategoryId();
        cacheClient.delete(cacheKey);
        dishService.saveWithFlavor(dishDTO);
        cacheClient.deleteDelayed(cacheKey);
        return Result.success();
    }

    @GetMapping("/page")
    @ApiOperation("菜品分页查询")
    public Result<PageResult> page(DishPageQueryDTO dishPageQueryDTO) {
        log.info("菜品分页查询：{}", dishPageQueryDTO);
//        dishPageQueryDTO.setPageSize(100000);
        return Result.success(dishService.pageQuery(dishPageQueryDTO));
    }


    /**
     * 启用或停用菜品
     *
     * @param status
     * @param id
     * @return
     */
    @PostMapping("/status/{status}")
    public Result<String> startOrStop(@PathVariable Integer status, Long id) {
        // 1. 启用 0. 停用
        log.info("启用或停用菜品：{}", id);
        // 延迟双删：先删缓存 → 更新数据库 → 延迟再删
        cacheClient.deleteByPattern("dish_*");
        dishService.startOrStop(status, id);
        cacheClient.deleteByPatternDelayed("dish_*");
        return Result.success();
    }

    @DeleteMapping
    @ApiOperation("删除菜品")
    public Result delete(@RequestParam Long[] ids) {//@RequestParam
        // 延迟双删：先删缓存 → 更新数据库 → 延迟再删（SCAN 游标，避免 KEYS 阻塞 Redis）
        cacheClient.deleteByPattern("dish_*");
        dishService.deleteBatch(ids);
        cacheClient.deleteByPatternDelayed("dish_*");

        return Result.success();
    }

    @GetMapping("/{id}")
    @ApiOperation("根据ID查询指定菜品")
    public Result<DishVO> getByIdWithFlavor(@PathVariable Long id) {
        log.info("根据ID查询指定菜品：{}", id);
        return Result.success(dishService.getByIdWithFlavor(id));
    }

    @PutMapping
    @ApiOperation("更新菜品信息")
    public Result update(@RequestBody DishDTO dishDTO) {
        log.info("更新菜品信息：{}", dishDTO);

        // 延迟双删：先删缓存 → 更新数据库 → 延迟再删，防止更新间隙读回旧数据
        String cacheKey = "dish_" + dishDTO.getCategoryId();
        cacheClient.delete(cacheKey);
        dishService.updateWithFlavor(dishDTO);
        cacheClient.deleteDelayed(cacheKey);
        return Result.success();
    }

    /**
     * 根据分类id查询菜品
     *
     * @param categoryId
     * @return
     */
    @GetMapping("/list")
    public Result<List<Dish>> list(Long categoryId) {
        List<Dish> dishList = dishService.list(categoryId);
        return Result.success(dishList);
    }
}
