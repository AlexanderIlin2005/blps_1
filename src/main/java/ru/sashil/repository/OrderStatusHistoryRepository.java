package ru.sashil.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.sashil.model.Order;
import ru.sashil.model.OrderStatusHistory;

import java.util.List;

@Repository
public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {
    List<OrderStatusHistory> findByOrderOrderByChangedAtDesc(Order order);
    List<OrderStatusHistory> findByOrderIdOrderByChangedAtDesc(Long orderId);
}
