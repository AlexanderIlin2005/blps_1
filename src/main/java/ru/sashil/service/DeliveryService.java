package ru.sashil.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import ru.sashil.model.DeliveryType;
import ru.sashil.model.Order;
import ru.sashil.model.OrderStatus;
import ru.sashil.repository.OrderRepository;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryService {

    private final OrderRepository orderRepository;
    private final NotificationService notificationService;
    private final OrderService orderService;

    @Async
    public CompletableFuture<Void> handoverToDelivery(Order order) {
        log.info("Handing over order to delivery: {}", order.getOrderNumber());

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Генерация трек-номера
                String trackingNumber = generateTrackingNumber();

                // Обновление статуса через OrderService
                orderService.updateTracking(order.getOrderNumber(), trackingNumber);

                order.setStatus(OrderStatus.OUT_FOR_DELIVERY);
                orderRepository.save(order);

                // Имитация доставки
                Thread.sleep(3000);

                if (order.getDeliveryType() == DeliveryType.COURIER) {
                    // Курьерская доставка
                    log.info("Courier delivering to address: {}", order.getDeliveryAddress());
                    Thread.sleep(2000);

                    orderService.completeDelivery(order.getOrderNumber());

                } else {
                    // ПВЗ
                    log.info("Order ready for pickup at: {}", order.getPickupPointAddress());
                    order.setStatus(OrderStatus.PICKUP_READY);
                    orderRepository.save(order);

                    // Имитация получения в ПВЗ
                    Thread.sleep(5000);

                    orderService.completeDelivery(order.getOrderNumber());
                }

                log.info("Delivery completed for order: {}", order.getOrderNumber());

            } catch (Exception e) {
                log.error("Error in delivery for order: {}", order.getOrderNumber(), e);
            }
            return null;
        });
    }

    private String generateTrackingNumber() {
        return "TRK" + System.currentTimeMillis() + (int)(Math.random() * 1000);
    }
}
