package com.nurture.shared.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 发件箱写入服务 — 在业务事务内落库，与状态变更原子提交。
 */
@Service
public class OutboxWriter {

    private final OutboxMessageRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxWriter(OutboxMessageRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void write(String eventType, String aggregateType, UUID aggregateId, Object payload) {
        try {
            repository.save(new OutboxMessage(eventType, aggregateType, aggregateId,
                    objectMapper.writeValueAsString(payload)));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("发件箱载荷序列化失败", e);
        }
    }
}
