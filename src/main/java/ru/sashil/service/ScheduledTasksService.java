package ru.sashil.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.sashil.model.Order;
import ru.sashil.model.OrderStatus;
import ru.sashil.model.PaymentStatus;
import ru.sashil.repository.OrderRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledTasksService {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    // Проверка неоплаченных заказов каждые 5 минут
    @Scheduled(fixedDelay = 300000)
    public void checkUnpaidOrders() {
        log.info("Checking for unpaid orders...");

        LocalDateTime timeoutThreshold = LocalDateTime.now().minusMinutes(30);

        List<Order> unpaidOrders = orderRepository.findByPaymentStatus(PaymentStatus.PENDING);

        for (Order order : unpaidOrders) {
            if (order.getCreatedAt().isBefore(timeoutThreshold)) {
                log.info("Cancelling unpaid order: {}", order.getOrderNumber());
                orderService.cancelUnpaidOrder(order.getOrderNumber());
            }
        }
    }
}
