package com.sky.controller.admin;

import com.sky.cache.CacheClient;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.SetmealService;
import com.sky.vo.SetmealVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/setmeal")
@Api(tags = "套餐相关接口")
@Slf4j
public class SetmealController {

    @Autowired
    private SetmealService setmealService;

    @Autowired
    private CacheClient cacheClient;

    /**
     * 新增套餐
     *
     * @param setmealDTO
     * @return
     */
    @PostMapping
    public Result<String> save(@RequestBody SetmealDTO setmealDTO) {
        log.info("新增套餐：{}", setmealDTO);
        // 延迟双删：先删缓存 → 更新数据库 → 延迟再删；新套餐只影响其所属分类
        String cacheKey = "setmealCache:" + setmealDTO.getCategoryId();
        cacheClient.delete(cacheKey);
        setmealService.saveWithDish(setmealDTO);
        cacheClient.deleteDelayed(cacheKey);
        return Result.success();
    }

    /**
     * 分页查询
     *
     * @param setmealPageQueryDTO
     * @return
     */
    @GetMapping("/page")
    public Result<PageResult> page(SetmealPageQueryDTO setmealPageQueryDTO) {
        log.info("分页查询套餐列表，请求参数：{}", setmealPageQueryDTO);
        PageResult pageResult = setmealService.pageQuery(setmealPageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     * 删除套餐
     *
     * @param ids
     * @return
     */
    @DeleteMapping
    @ApiOperation("批量删除套餐")
    public Result<String> delete(@RequestParam List<Long> ids) {
        log.info("删除套餐，ids：{}", ids);
        // 延迟双删：先删缓存 → 更新数据库 → 延迟再删；可能影响多个分类，按前缀批量删除
        cacheClient.deleteByPattern("setmealCache*");
        setmealService.deleteBatch(ids);
        cacheClient.deleteByPatternDelayed("setmealCache*");
        return Result.success();
    }

    /**
     * 根据id查询套餐
     *
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    @ApiOperation("根据id查询套餐")
    public Result<SetmealVO> getById(@PathVariable Long id) {
        log.info("根据id查询套餐，id：{}", id);
        SetmealVO setmealVO = setmealService.getById(id);
        return Result.success(setmealVO);
    }

    /**
     * 修改套餐
     *
     * @param setmealDTO
     * @return
     */
    @PutMapping
    @ApiOperation("修改套餐")
    public Result<String> update(@RequestBody SetmealDTO setmealDTO) {
        log.info("修改套餐，请求参数：{}", setmealDTO);
        // 延迟双删：先删缓存 → 更新数据库 → 延迟再删
        cacheClient.deleteByPattern("setmealCache*");
        setmealService.update(setmealDTO);
        cacheClient.deleteByPatternDelayed("setmealCache*");
        return Result.success();
    }

    /**
     * 启用或停用套餐
     *
     * @param status
     * @param id
     * @return
     */
    @PostMapping("/status/{status}")
    @ApiOperation("启用或停用套餐")
    public Result<String> startOrStop(@PathVariable Integer status, Long id) {
        log.info("启用或停用套餐，status：{}，id：{}", status, id);
        // 延迟双删：先删缓存 → 更新数据库 → 延迟再删
        cacheClient.deleteByPattern("setmealCache*");
        setmealService.startOrStop(status, id);
        cacheClient.deleteByPatternDelayed("setmealCache*");
        return Result.success();
    }
}
