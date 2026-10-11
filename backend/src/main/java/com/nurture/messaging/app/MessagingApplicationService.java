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
    private final com.nurture.identity.app.IdentityApplicationService identityService;

    public MessagingApplicationService(ConversationRepository conversationRepository,
                                       MessageRepository messageRepository,
                                       SystemNoticeRepository noticeRepository,
                                       com.nurture.identity.app.IdentityApplicationService identityService) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.noticeRepository = noticeRepository;
        this.identityService = identityService;
    }

    @Transactional
    public Conversation openOrGet(UUID studentId, UUID teacherId, String subject) {
        if (!CallerContext.require().accountId().equals(studentId)) throw new org.springframework.security.access.AccessDeniedException("只能开启自己的会话");
        var teacher = identityService.adminAccountById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("导师不存在"));
        if (teacher.role() != com.nurture.shared.security.CallerRole.TEACHER || !"ACTIVE".equals(teacher.status())) {
            throw new IllegalArgumentException("请选择可用的导师账户");
        }
        if (subject != null && subject.length() > 64) throw new IllegalArgumentException("科目不能超过 64 字");
        return conversationRepository.findByStudentIdOrderByLastMessageAtDesc(studentId).stream()
                .filter(c -> c.getTeacherId().equals(teacherId))
                .findFirst()
                .orElseGet(() -> conversationRepository.save(new Conversation(studentId, teacherId, subject)));
    }

    @Transactional
    public UUID openOrGetId(UUID student,UUID teacher,String subject){return openOrGet(student,teacher,subject).getId();}

    @Transactional
    public Message send(UUID conversationId, String body) {
        if (body == null || body.isBlank() || body.length() > 4000) throw new IllegalArgumentException("消息须为 1–4000 字");
        CallerPrincipal caller = CallerContext.require();
        Conversation conversation = conversationRepository.locked(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        boolean participant = conversation.getStudentId().equals(caller.accountId())
                || conversation.getTeacherId().equals(caller.accountId());
        if (!participant) {
            throw new org.springframework.security.access.AccessDeniedException("仅会话双方可发送消息");
        }
        conversation.touch();
        return messageRepository.save(new Message(conversationId, caller.accountId(),
                Message.SenderRole.valueOf(caller.role().name()), body.trim()));
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
    public List<Message> messagesOf(UUID conversationId, int page) {
        requireParticipant(conversationId);
        if (page < 0 || page > 10000) throw new IllegalArgumentException("页码无效");
        var messages = new java.util.ArrayList<>(messageRepository.findByConversationIdOrderBySentAtDescIdDesc(conversationId, PageRequest.of(page, 100)));
        java.util.Collections.reverse(messages);
        return messages;
    }

    private Conversation requireParticipant(UUID conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        var caller = CallerContext.require();
        boolean participant = conversation.getStudentId().equals(caller.accountId())
                || conversation.getTeacherId().equals(caller.accountId());
        if (!participant) {
            throw new org.springframework.security.access.AccessDeniedException("仅会话双方可查看消息记录");
        }
        return conversation;
    }

    @Transactional
    public void markConversationRead(UUID conversationId) {
        requireParticipant(conversationId);
        messageRepository.markRead(conversationId, CallerContext.require().accountId(), java.time.Instant.now());
    }

    public record ConversationSummary(UUID conversationId, UUID counterpartId, String counterpartName,
                                      String subject, java.time.Instant lastMessageAt, long unreadCount) {}

    @Transactional(readOnly = true)
    public List<ConversationSummary> conversationSummaries() {
        UUID me = CallerContext.require().accountId();
        return conversationsOf(me).stream().sorted(java.util.Comparator.comparing(Conversation::getLastMessageAt).reversed())
                .map(c -> {
                    UUID other = c.getStudentId().equals(me) ? c.getTeacherId() : c.getStudentId();
                    String name = identityService.adminAccountById(other).map(a -> a.displayName()).orElse("会话用户");
                    return new ConversationSummary(c.getId(), other, name, c.getSubject(), c.getLastMessageAt(),
                            messageRepository.countByConversationIdAndSenderIdNotAndReadAtIsNull(c.getId(), me));
                }).toList();
    }

    /** 结构化系统通知派发（供其他模块调用）。 */
    @Transactional
    public void notify(UUID recipientId, String type, String title, String body) {
        noticeRepository.save(new SystemNotice(recipientId, type, title, body));
    }

    @Transactional(readOnly = true)
    public Page<NoticeView> noticesOf(UUID recipientId, int page, int size) {
        if(page<0 || page>10000 || size<1 || size>100)throw new IllegalArgumentException("通知分页参数无效");
        return noticeRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(recipientId, PageRequest.of(page, size))
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

    @Transactional(readOnly=true)
    public long unreadNotices(UUID recipientId){return noticeRepository.countByRecipientIdAndReadAtIsNull(recipientId);}

    public record NoticeView(UUID noticeId, String type, String title, String body,
                             java.time.Instant createdAt, boolean read) {
    }
}
