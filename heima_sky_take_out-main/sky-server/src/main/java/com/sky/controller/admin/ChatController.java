package com.sky.controller.admin;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商家客服端聊天接口
 */
@RestController("adminChatController")
@RequestMapping("/admin/chat")
@Api(tags = "商家客服聊天接口")
@Slf4j
public class ChatController {

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    /**
     * 会话列表：每个聊过天的用户一行（最后消息 + 未读数）
     */
    @GetMapping("/conversations")
    @ApiOperation("客服会话列表")
    public Result<List<ChatConversationVO>> conversations() {
        return Result.success(chatMessageMapper.listConversations());
    }

    /**
     * 与某用户的历史消息
     */
    @GetMapping("/history")
    @ApiOperation("客服查看聊天历史")
    public Result<List<ChatMessage>> history(@RequestParam Long userId) {
        return Result.success(chatMessageMapper.listByUserId(userId));
    }

    /**
     * 打开某用户会话：该用户发来的消息置已读
     */
    @PutMapping("/read")
    @ApiOperation("客服标记已读")
    public Result<String> read(@RequestParam Long userId) {
        chatMessageMapper.markReadByMerchant(userId);
        return Result.success();
    }

    /**
     * 客服总未读数
     */
    @GetMapping("/unreadCount")
    @ApiOperation("客服未读总数")
    public Result<Integer> unreadCount() {
        return Result.success(chatMessageMapper.countUnreadForMerchant());
    }
}
