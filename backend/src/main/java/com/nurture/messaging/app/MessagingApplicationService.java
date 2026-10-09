package com.nurture.messaging.app;

import com.nurture.messaging.domain.Conversation;
import com.nurture.messaging.domain.Message;
import com.nurture.messaging.domain.SystemNotice;
import com.nurture.messaging.infra.ConversationRepository;
import com.nurture.messaging.infra.MessageRepository;
import com.nurture.messaging.infra.SystemNoticeRepository;
import com.nurture.shared.security.CallerContext;
import com.nurture.shared.security.CallerPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 站内信令应用服务 — 课前咨询会话、履约动态通知与结构化系统消息派发。
 */
@Service
public class MessagingApplicationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final SystemNoticeRepository noticeRepository;

    public MessagingApplicationService(ConversationRepository conversationRepository,
                                       MessageRepository messageRepository,
                                       SystemNoticeRepository noticeRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.noticeRepository = noticeRepository;
    }

    @Transactional
    public Conversation openOrGet(UUID studentId, UUID teacherId, String subject) {
        return conversationRepository.findByStudentIdOrderByLastMessageAtDesc(studentId).stream()
                .filter(c -> c.getTeacherId().equals(teacherId))
                .findFirst()
                .orElseGet(() -> conversationRepository.save(new Conversation(studentId, teacherId, subject)));
    }

    @Transactional
    public Message send(UUID conversationId, String body) {
        CallerPrincipal caller = CallerContext.require();
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        boolean participant = conversation.getStudentId().equals(caller.accountId())
                || conversation.getTeacherId().equals(caller.accountId());
        if (!participant) {
            throw new org.springframework.security.access.AccessDeniedException("仅会话双方可发送消息");
        }
        conversation.touch();
        return messageRepository.save(new Message(conversationId, caller.accountId(),
                Message.SenderRole.valueOf(caller.role().name()), body));
    }

    @Transactional(readOnly = true)
    public List<Conversation> conversationsOf(UUID accountId) {
        List<Conversation> asStudent = conversationRepository.findByStudentIdOrderByLastMessageAtDesc(accountId);
        List<Conversation> asTeacher = conversationRepository.findByTeacherIdOrderByLastMessageAtDesc(accountId);
        List<Conversation> all = new java.util.ArrayList<>(asStudent);
        all.addAll(asTeacher);
        return all;
    }

    /** 消息记录：仅会话参与双方可读（BOLA 修复）。 */
    @Transactional(readOnly = true)
    public List<Message> messagesOf(UUID conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        var caller = CallerContext.require();
        boolean participant = conversation.getStudentId().equals(caller.accountId())
                || conversation.getTeacherId().equals(caller.accountId());
        if (!participant) {
            throw new org.springframework.security.access.AccessDeniedException("仅会话双方可查看消息记录");
        }
        return messageRepository.findTop100ByConversationIdOrderBySentAtAsc(conversationId);
    }

    /** 结构化系统通知派发（供其他模块调用）。 */
    @Transactional
    public void notify(UUID recipientId, String type, String title, String body) {
        noticeRepository.save(new SystemNotice(recipientId, type, title, body));
    }

    @Transactional(readOnly = true)
    public Page<NoticeView> noticesOf(UUID recipientId, int page, int size) {
        return noticeRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId, PageRequest.of(page, size))
                .map(n -> new NoticeView(n.getId(), n.getNoticeType(), n.getTitle(), n.getBody(),
                        n.getCreatedAt(), n.getReadAt() != null));
    }

    /** 标记通知已读（对象级授权：仅接收人本人）。 */
    @Transactional
    public void markNoticeRead(UUID noticeId, UUID recipientId) {
        SystemNotice notice = noticeRepository.findById(noticeId)
                .orElseThrow(() -> new IllegalArgumentException("通知不存在"));
        if (!notice.getRecipientId().equals(recipientId)) {
            throw new org.springframework.security.access.AccessDeniedException("仅通知接收人可标记已读");
        }
        notice.markRead();
    }

    public record NoticeView(UUID noticeId, String type, String title, String body,
                             java.time.Instant createdAt, boolean read) {
    }
}
