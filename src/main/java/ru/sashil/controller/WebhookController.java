package ru.sashil.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import ru.sashil.model.Order;
import ru.sashil.model.OrderStatus;
import ru.sashil.model.PaymentStatus;
import ru.sashil.repository.OrderRepository;
import ru.sashil.service.InventoryService;
import ru.sashil.service.NotificationService;
import ru.sashil.service.OrderService;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/payments/webhook")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final InventoryService inventoryService;
    private final NotificationService notificationService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @PostMapping
    public ResponseEntity<Void> handleYooKassaWebhook(@RequestBody Map<String, Object> payload) {
        log.info(">>> YooKassa Webhook Received: {}", payload);

        try {
            Map<String, Object> object = (Map<String, Object>) payload.get("object");
            if (object == null) return ResponseEntity.ok().build();

            String event = (String) payload.get("event");
            String status = (String) object.get("status");
            Map<String, String> metadata = (Map<String, String>) object.get("metadata");
            String orderNumber = (metadata != null) ? metadata.get("order_id") : null;
            String paymentId = (String) object.get("id");

            if (orderNumber == null) {
                log.error("Order number not found in webhook metadata!");
                return ResponseEntity.ok().build();
            }

            transactionTemplate.execute(statusTx -> {
                Order order = orderRepository.findByOrderNumber(orderNumber)
                    .orElseThrow(() -> new RuntimeException("Order not found: " + orderNumber));

                if (order.getStatus() == OrderStatus.PAID) {
                    log.info("Order {} is already PAID", order.getOrderNumber());
                    return null;
                }

                if ("payment.succeeded".equals(event) || "succeeded".equals(status)) {
                    processSucceededPayment(order, paymentId);
                } else if ("payment.canceled".equals(event) || "canceled".equals(status)) {
                    processCanceledPayment(order);
                }
                return null;
            });

        } catch (Exception e) {
            log.error("Critical error in Webhook Controller: {}", e.getMessage(), e);
        }

        return ResponseEntity.ok().build();
    }

    private void processSucceededPayment(Order order, String paymentId) {
        order.setPaymentId(paymentId);
        order.setPaymentStatus(PaymentStatus.COMPLETED);
        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(LocalDateTime.now());
        orderRepository.save(order);

        orderService.updateOrderStatus(order.getOrderNumber(), OrderStatus.PAID, "Оплата подтверждена через Webhook ЮKassa");
        notificationService.sendOrderConfirmation(order);
        inventoryService.processFulfillment(order);
    }

    private void processCanceledPayment(Order order) {
        order.setPaymentStatus(PaymentStatus.FAILED);
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(LocalDateTime.now());
        orderRepository.save(order);
        
        orderService.updateOrderStatus(order.getOrderNumber(), OrderStatus.CANCELLED, "Оплата отменена или отклонена (Webhook)");
    }
}
