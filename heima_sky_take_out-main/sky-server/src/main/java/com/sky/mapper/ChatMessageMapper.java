package com.sky.mapper;

import com.sky.entity.ChatMessage;
import com.sky.vo.ChatConversationVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ChatMessageMapper {

    /**
     * 保存消息
     */
    void insert(ChatMessage chatMessage);

    /**
     * 查询某用户与商家的历史消息（升序，最多200条）
     */
    List<ChatMessage> listByUserId(Long userId);

    /**
     * 商家端：会话列表（每个用户一行：最后消息 + 未读数）
     */
    List<ChatConversationVO> listConversations();

    /**
     * 用户端：与商家会话的最后一条消息
     */
    ChatMessage getLastByUserId(Long userId);

    /**
     * 用户未读数（商家发给该用户、未读）
     */
    int countUnreadForUser(Long userId);

    /**
     * 商家总未读数（所有用户发给商家、未读）
     */
    int countUnreadForMerchant();

    /**
     * 用户打开聊天页：商家发的消息置已读
     */
    int markReadByUser(Long userId);

    /**
     * 商家打开某用户会话：该用户发的消息置已读
     */
    int markReadByMerchant(Long userId);

    /**
     * 按消息id置已读（智能客服答复后，用户消息无需商家处理）
     */
    int markReadById(Long id);
}
