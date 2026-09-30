package com.sky.task;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Component
@Slf4j
public class OrderTask {

    // 订单超时延时队列 ZSet key，与 OrderServiceImpl 中保持一致
    private static final String ORDER_EXPIRE_KEY = "order:expire";

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 处理超时订单（主路径）：扫描 Redis ZSet 延时队列，每 5 秒执行一次
     * member=订单id，score=到期时间戳；score <= 当前时间 即为已超时
     */
    @Scheduled(cron = "0/5 * * * * ?")
    public void processTimeoutOrders() {
        Set<Object> expireOrderIds;
        try {
            expireOrderIds = redisTemplate.opsForZSet()
                    .rangeByScore(ORDER_EXPIRE_KEY, 0, System.currentTimeMillis());
        } catch (Exception e) {
            // Redis 异常时不做处理，由 DB 兜底任务负责取消
            log.error("扫描订单超时延时队列失败，等待DB兜底：", e);
            return;
        }
        if (expireOrderIds == null || expireOrderIds.isEmpty()) {
            return;
        }

        for (Object member : expireOrderIds) {
            Long orderId = Long.valueOf(member.toString());

            // ZREM 原子抢占：多实例/多轮询下只有一个实例删除成功（返回1），由它处理，天然防重复
            Long removed;
            try {
                removed = redisTemplate.opsForZSet().remove(ORDER_EXPIRE_KEY, member);
            } catch (Exception e) {
                log.error("移除延时队列成员失败，等待DB兜底：orderId={}", orderId, e);
                continue;
            }
            if (removed == null || removed == 0L) {
                continue;
            }

            // 二次校验：用户可能在扫描的瞬间完成了支付或手动取消，只有待付款订单才取消
            Orders order = orderMapper.getById(orderId);
            if (order == null || !Orders.PENDING_PAYMENT.equals(order.getStatus())) {
                continue;
            }

            order.setStatus(Orders.CANCELLED);
            order.setCancelReason("订单超时，自动取消");
            order.setCancelTime(LocalDateTime.now());
            orderMapper.update(order);
            log.info("订单超时自动取消（延时队列）：orderId={}", orderId);
        }
    }

    /**
     * 兜底任务：直接查数据库，每 5 分钟执行一次
     * 作用：Redis 宕机、数据丢失、历史脏数据等情况下保证超时订单一定被取消
     */
    @Scheduled(cron = "0 */5 * * * ?")
    public void processTimeoutOrdersByDb() {
        List<Orders> orderList = orderMapper.getByStatusAndOrderTimeLT(
                Orders.PENDING_PAYMENT, LocalDateTime.now().plusMinutes(-15));
        if (orderList == null || orderList.isEmpty()) {
            return;
        }
        log.info("DB兜底任务发现超时订单 {} 笔", orderList.size());
        orderList.forEach(order -> {
            order.setStatus(Orders.CANCELLED);
            order.setCancelReason("订单超时，自动取消");
            order.setCancelTime(LocalDateTime.now());
            orderMapper.update(order);
            // 同步清理 ZSet 残留；Redis 异常不影响 DB 取消结果
            try {
                redisTemplate.opsForZSet().remove(ORDER_EXPIRE_KEY, order.getId().toString());
            } catch (Exception ignored) {
            }
        });
    }

    /**
     * 处理一直处于派送中的
     */
    @Scheduled(cron = "0 0 1 * * ? ")   // 每天凌晨一点触发
    public void processDeliveryOrders() {
        log.info("处理派送中的订单");
        // 获取派送中的订单
        LocalDateTime time = LocalDateTime.now().plusMinutes(-60);
        List<Orders> orderList = orderMapper.getByStatusAndOrderTimeLT(Orders.DELIVERY_IN_PROGRESS, time);
        if (orderList != null && !orderList.isEmpty()) {
            orderList.forEach(order -> {
                order.setStatus(Orders.COMPLETED);
                order.setDeliveryTime(LocalDateTime.now());
                orderMapper.update(order);
            });
        }
    }
}
