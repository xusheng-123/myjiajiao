package com.nurture.order.infra;

import com.nurture.order.domain.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByOrderNo(String orderNo);

    Optional<Order> findByBookingId(UUID bookingId);

    Page<Order> findByPayerParentIdOrderByCreatedAtDesc(UUID payerParentId, Pageable pageable);

    Page<Order> findByStatus(com.nurture.order.domain.Order.OrderStatus status, Pageable pageable);
}
