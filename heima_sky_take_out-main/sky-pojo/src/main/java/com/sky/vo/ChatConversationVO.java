package com.sky.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 会话列表项（商家端看到的联系人 / 用户端看到的商家会话）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatConversationVO implements Serializable {

    private static final long serialVersionUID = 1L;

    //会话用户id（用户端固定会话时为自己的id）
    private Long userId;

    //用户名
    private String userName;

    //用户账号
    private String username;

    //最后一条消息类型：1文本 2订单卡片
    private Integer lastMsgType;

    //最后一条消息内容（订单卡片为快照JSON）
    private String lastContent;

    //最后消息时间
    private LocalDateTime lastTime;

    //未读数
    private Integer unreadCount;
}
