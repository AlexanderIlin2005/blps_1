package ru.sashil.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.sashil.model.Order;
import ru.sashil.model.OrderStatus;
import ru.sashil.model.PaymentStatus;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByOrderNumber(String orderNumber);
    List<Order> findByCustomerId(Long customerId);
    List<Order> findByStatus(OrderStatus status);
    List<Order> findByPaymentStatus(PaymentStatus paymentStatus);
}
