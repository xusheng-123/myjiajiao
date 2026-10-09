package com.nurture.payment.infra;

import com.nurture.payment.domain.PaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {

    Optional<PaymentAttempt> findByOrderIdAndChannel(UUID orderId, PaymentAttempt.PaymentChannel channel);

    Optional<PaymentAttempt> findByChannelRef(String channelRef);
}
