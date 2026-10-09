package com.nurture.shared.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 进程内事件总线 — 发件箱派发器的默认实现（Spring ApplicationEvent）。
 * 业务模块通过 @EventListener(OutboxEvent) 订阅，替代外部消息中间件（规范禁止 Kafka/RocketMQ）。
 */
@Component
public class ApplicationEventBus implements OutboxDispatcher.InProcessEventBus {

    private static final Logger log = LoggerFactory.getLogger(ApplicationEventBus.class);

    private final ApplicationEventPublisher publisher;

    public ApplicationEventBus(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(String eventType, UUID aggregateId, JsonNode payload) {
        publisher.publishEvent(new OutboxEvent(eventType, aggregateId, payload));
    }

    /** 发件箱派发出的领域事件载荷；监听者按 eventType 分发处理。 */
    public record OutboxEvent(String eventType, UUID aggregateId, JsonNode payload) {
    }
}
