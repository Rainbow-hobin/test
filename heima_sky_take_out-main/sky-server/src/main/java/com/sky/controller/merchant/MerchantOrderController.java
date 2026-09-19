package com.sky.controller.merchant;

import com.sky.dto.OrdersConfirmDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.OrderService;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 商家端订单管理接口（网页版）
 */
@RestController
@RequestMapping("/merchant/order")
@Api(tags = "商家端-订单接口")
@Slf4j
public class MerchantOrderController {

    @Autowired
    private OrderService orderService;

    @GetMapping("/page")
    @ApiOperation("分页查询订单（商家端）")
    public Result<PageResult<OrderVO>> page(int page, int pageSize, Integer status) {
        return Result.success(orderService.conditionSearch(buildQuery(page, pageSize, status)));
    }

    @GetMapping("/statistics")
    @ApiOperation("各状态订单数量统计")
    public Result<OrderStatisticsVO> statistics() {
        return Result.success(orderService.statistics());
    }

    @GetMapping("/details/{id}")
    @ApiOperation("订单详情")
    public Result<OrderVO> details(@PathVariable Long id) {
        return Result.success(orderService.details(id));
    }

    @PutMapping("/confirm")
    @ApiOperation("接单")
    public Result confirm(@RequestBody OrdersConfirmDTO ordersConfirmDTO) {
        orderService.confirm(ordersConfirmDTO);
        return Result.success();
    }

    @PutMapping("/delivery/{id}")
    @ApiOperation("派送订单")
    public Result delivery(@PathVariable Long id) {
        orderService.delivery(id);
        return Result.success();
    }

    @PutMapping("/complete/{id}")
    @ApiOperation("完成订单")
    public Result complete(@PathVariable Long id) {
        orderService.complete(id);
        return Result.success();
    }

    private OrdersPageQueryDTO buildQuery(int page, int pageSize, Integer status) {
        OrdersPageQueryDTO dto = new OrdersPageQueryDTO();
        dto.setPage(page);
        dto.setPageSize(pageSize);
        dto.setStatus(status);
        return dto;
    }
}
