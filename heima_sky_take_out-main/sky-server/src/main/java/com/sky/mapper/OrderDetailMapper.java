package com.sky.mapper;

import com.sky.entity.OrderDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OrderDetailMapper {
    /**
     * 批量插入订单明细数据
     * @param orderDetailList
     */
    void insertBatch(List<OrderDetail> orderDetailList);

    /**
     * 根据订单id查询订单明细
     * @param orderId
     * @return
     */
    @Select("select * from order_detail where order_id = #{orderId}")
    List<OrderDetail> getByOrderId(Long orderId);

    /**
     * 已完成订单中销量最高的菜品（用于客服推荐）
     * @param limit 条数
     */
    @Select("select od.name as name, sum(od.number) as number " +
            "from order_detail od join orders o on od.order_id = o.id " +
            "where o.status = 5 group by od.name order by number desc limit #{limit}")
    List<OrderDetail> topSold(@Param("limit") int limit);
}
