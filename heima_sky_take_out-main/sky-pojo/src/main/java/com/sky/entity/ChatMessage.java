package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户与商家客服的聊天消息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    //会话归属用户id（一个用户与商家客服的全部消息共用同一个 user_id）
    private Long userId;

    //发送方类型：1用户 2商家
    private Integer senderType;

    //发送方id（用户id 或 员工id）
    private Long senderId;

    //消息类型：1文本 2订单卡片
    private Integer msgType;

    //文本内容 或 订单快照JSON
    private String content;

    //订单卡片关联的订单id（文本消息为null）
    private Long orderId;

    //是否已读：0未读 1已读（对接收方而言）
    private Integer isRead;

    private LocalDateTime createTime;
}
