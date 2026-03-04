package ru.sashil.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import ru.sashil.model.DeliveryType;
import ru.sashil.model.Order;
import ru.sashil.model.OrderStatus;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryService {

    private final OrderService orderService;
    private final NotificationService notificationService;

    @Async
    public CompletableFuture<Void> handoverToDelivery(Order order) {
        log.info("📦 Handing over order to delivery: {}", order.getOrderNumber());

        return CompletableFuture.supplyAsync(() -> {
            try {
                
                String trackingNumber = generateTrackingNumber();
                log.info("Generated tracking number: {} for order: {}", trackingNumber, order.getOrderNumber());

                
                orderService.updateTracking(order.getOrderNumber(), trackingNumber);
                Thread.sleep(1000);

                orderService.updateOrderStatus(order.getOrderNumber(), OrderStatus.OUT_FOR_DELIVERY,
                    "Заказ передан в службу доставки, трек-номер: " + trackingNumber);

                
                log.info("Delivery in progress for order: {}...", order.getOrderNumber());

                if (order.getDeliveryType() == DeliveryType.COURIER) {
                    
                    orderService.updateOrderStatus(order.getOrderNumber(), OrderStatus.OUT_FOR_DELIVERY,
                        "Курьер выехал по адресу: " + order.getDeliveryAddress());
                    Thread.sleep(4000);

                    orderService.updateOrderStatus(order.getOrderNumber(), OrderStatus.DELIVERED,
                        "Заказ доставлен курьером");
                    Thread.sleep(1000);

                } else {
                    
                    orderService.updateOrderStatus(order.getOrderNumber(), OrderStatus.PICKUP_READY,
                        "Заказ готов к выдаче в ПВЗ: " + order.getPickupPointAddress());
                    Thread.sleep(3000);

                    orderService.updateOrderStatus(order.getOrderNumber(), OrderStatus.PICKED_UP,
                        "Заказ получен в пункте самовывоза");
                    Thread.sleep(1000);
                }

                orderService.completeDelivery(order.getOrderNumber());
                notificationService.sendDeliveryNotification(order);

                log.info("✅ Order {} delivery completed", order.getOrderNumber());

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
