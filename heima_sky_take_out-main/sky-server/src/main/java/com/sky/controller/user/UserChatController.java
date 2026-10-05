package com.sky.controller.user;

import com.sky.context.BaseContext;
import com.sky.entity.ChatMessage;
import com.sky.mapper.ChatMessageMapper;
import com.sky.result.Result;
import com.sky.vo.ChatConversationVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户端聊天接口（用户 ↔ 商家客服）
 */
@RestController("userChatController")
@RequestMapping("/user/chat")
@Api(tags = "用户端聊天接口")
@Slf4j
public class UserChatController {

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    /**
     * 会话列表：用户只有一个商家客服会话
     */
    @GetMapping("/conversations")
    @ApiOperation("会话列表")
    public Result<List<ChatConversationVO>> conversations() {
        Long userId = BaseContext.getCurrentId();
        List<ChatConversationVO> result = new ArrayList<>();
        ChatMessage last = chatMessageMapper.getLastByUserId(userId);
        ChatConversationVO vo = ChatConversationVO.builder()
                .userId(userId)
                .userName("商家客服")
                .unreadCount(chatMessageMapper.countUnreadForUser(userId))
                .build();
        if (last != null) {
            vo.setLastMsgType(last.getMsgType());
            vo.setLastContent(last.getContent());
            vo.setLastTime(last.getCreateTime());
        }
        result.add(vo);
        return Result.success(result);
    }

    /**
     * 与商家的历史消息
     */
    @GetMapping("/history")
    @ApiOperation("聊天历史")
    public Result<List<ChatMessage>> history() {
        return Result.success(chatMessageMapper.listByUserId(BaseContext.getCurrentId()));
    }

    /**
     * 打开聊天页：商家发来的消息全部置已读
     */
    @PutMapping("/read")
    @ApiOperation("标记已读")
    public Result<String> read() {
        chatMessageMapper.markReadByUser(BaseContext.getCurrentId());
        return Result.success();
    }

    /**
     * 未读数（底部消息tab红点）
     */
    @GetMapping("/unreadCount")
    @ApiOperation("未读数")
    public Result<Integer> unreadCount() {
        return Result.success(chatMessageMapper.countUnreadForUser(BaseContext.getCurrentId()));
    }
}
