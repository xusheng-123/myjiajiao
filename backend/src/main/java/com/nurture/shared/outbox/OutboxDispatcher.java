package com.nurture.shared.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 发件箱派发器 — 与业务同事务写入，独立事务轮询派发为进程内事件。
 */
@Service
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);
    private static final int MAX_ATTEMPTS = 8;

    private final OutboxMessageRepository repository;
    private final ObjectMapper objectMapper;
    private final InProcessEventBus eventBus;

    public OutboxDispatcher(OutboxMessageRepository repository, ObjectMapper objectMapper, InProcessEventBus eventBus) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.eventBus = eventBus;
    }

    @Scheduled(fixedDelay = 2000)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatchPending() {
        List<OutboxMessage> pending = repository.findTop100ByDispatchedAtIsNullOrderByOccurredAtAsc();
        for (OutboxMessage message : pending) {
            try {
                eventBus.publish(message.getEventType(), message.getAggregateId(),
                        objectMapper.readTree(message.getPayload()));
                message.markDispatched();
            } catch (Exception e) {
                message.markAttemptFailed();
                if (message.getAttempts() >= MAX_ATTEMPTS) {
                    log.error("发件箱事件超过最大重试次数，进入死信人工介入: type={}, id={}",
                            message.getEventType(), message.getId(), e);
                    message.markDispatched(); // 停止重试；生产可转独立死信表
                }
                log.warn("发件箱派发失败 attempts={}: type={}", message.getAttempts(), message.getEventType());
            }
        }
    }

    /** 进程内事件总线 — 替代外部消息中间件的轻量解耦通道。 */
    public interface InProcessEventBus {
        void publish(String eventType, java.util.UUID aggregateId, com.fasterxml.jackson.databind.JsonNode payload)
                throws JsonProcessingException;
    }
}
