package com.nurture.messaging.api;

import com.nurture.messaging.app.MessagingApplicationService;
import com.nurture.messaging.domain.Message;
import com.nurture.shared.security.CallerContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 站内信令 API — 咨询会话、消息与系统通知。
 */
@RestController
@RequestMapping("/api/v1/messaging")
@Tag(name = "messaging / 站内信令", description = "课前咨询会话、消息收发、系统通知")
@SecurityRequirement(name = "bearerJWT")
public class MessagingController {

    private final MessagingApplicationService messagingService;

    public MessagingController(MessagingApplicationService messagingService) {
        this.messagingService = messagingService;
    }

    public record OpenConversationRequest(@jakarta.validation.constraints.NotNull UUID teacherId,
                                           @jakarta.validation.constraints.Size(max = 64) String subject) {
    }

    public record SendMessageRequest(@NotBlank @jakarta.validation.constraints.Size(max = 4000) String body) {
    }

    public record ConversationView(UUID conversationId, UUID counterpartId, String subject,
                                   java.time.Instant lastMessageAt) {
    }

    public record MessageView(UUID messageId, UUID senderId, String senderRole, String body,
                              java.time.Instant sentAt, java.time.Instant readAt, boolean mine) {
        static MessageView from(Message m) {
            return new MessageView(m.getId(), m.getSenderId(), m.getSenderRole().name(), m.getBody(), m.getSentAt(),
                    m.getReadAt(), CallerContext.require().accountId().equals(m.getSenderId()));
        }
    }

    @PreAuthorize("hasRole('STUDENT') or hasRole('PARENT') or hasRole('ADMIN')")
    @PostMapping("/conversations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "发起课前咨询会话", description = "与导师开启双向沟通线程（幂等：重复开启返回既有会话）")
    public ConversationView open(@jakarta.validation.Valid @RequestBody OpenConversationRequest request) {
        UUID studentId = CallerContext.require().accountId();
        var conversation = messagingService.openOrGet(studentId, request.teacherId(), request.subject());
        return new ConversationView(conversation.getId(), request.teacherId(),
                conversation.getSubject(), conversation.getLastMessageAt());
    }

    @GetMapping("/conversations")
    @Operation(summary = "我的会话列表")
    public List<MessagingApplicationService.ConversationSummary> conversations() {
        return messagingService.conversationSummaries();
    }

    @GetMapping("/conversations/{conversationId}/messages")
    @Operation(summary = "会话消息记录")
    public List<MessageView> messages(@PathVariable UUID conversationId, @RequestParam(defaultValue = "0") int page) {
        return messagingService.messagesOf(conversationId, page).stream().map(MessageView::from).toList();
    }

    @PostMapping("/conversations/{conversationId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "发送会话消息")
    public MessageView send(@PathVariable UUID conversationId, @jakarta.validation.Valid @RequestBody SendMessageRequest request) {
        return MessageView.from(messagingService.send(conversationId, request.body()));
    }

    @PostMapping("/conversations/{conversationId}:read")
    public Map<String, Object> readConversation(@PathVariable UUID conversationId) {
        messagingService.markConversationRead(conversationId);
        return Map.of("conversationId", conversationId, "read", true);
    }

    /** 通知已读（D12「已处理状态」）：仅接收人本人可标记。 */
    @PostMapping("/notifications/{noticeId}:read")
    @Operation(summary = "标记通知已读", description = "已处理状态可追踪；仅接收人本人可操作")
    public Map<String, String> markRead(@PathVariable java.util.UUID noticeId) {
        messagingService.markNoticeRead(noticeId, CallerContext.require().accountId());
        return Map.of("noticeId", noticeId.toString(), "read", "true");
    }

    @GetMapping("/notifications")
    @Operation(summary = "系统通知列表", description = "履约动态通知（预约、支付、审核结果等）")
    public Map<String, Object> notifications(@RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "10") int size) {
        var result = messagingService.noticesOf(CallerContext.require().accountId(), page, size);
        return Map.of("total", result.getTotalElements(), "items", result.getContent());
    }
}
