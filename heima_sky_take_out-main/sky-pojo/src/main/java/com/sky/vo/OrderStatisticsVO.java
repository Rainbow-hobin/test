package com.sky.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class OrderStatisticsVO implements Serializable {
    //待接单数量
    private Integer toBeConfirmed;

    //待派送数量
    private Integer confirmed;

    //派送中数量
    private Integer deliveryInProgress;

    //已完成数量（网页版商家端展示）
    private Integer completedOrders;

    //全部订单数量（网页版商家端展示）
    private Integer allOrders;
}
