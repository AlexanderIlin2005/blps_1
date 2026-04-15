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
    private final NotificationService notificationService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    @Autowired
    private TransactionTemplate transactionTemplate;

    @PostMapping
    public ResponseEntity<Void> handleYooKassaWebhook(jakarta.servlet.http.HttpServletRequest request) {
        try {
            java.util.Scanner scanner = new java.util.Scanner(request.getInputStream(), "UTF-8").useDelimiter("\\A");
            String rawPayload = scanner.hasNext() ? scanner.next() : "";
            log.info(">>> FULL Webhook Payload: {}", rawPayload);

            Map<String, Object> payload = objectMapper.readValue(rawPayload, Map.class);
            Map<String, Object> object = (Map<String, Object>) payload.get("object");
            
            if (object == null) {
                log.warn("Webhook payload object is null");
                return ResponseEntity.ok().build();
            }

            String event = (String) payload.get("event");
            String status = (String) object.get("status");
            Map<String, String> metadata = (Map<String, String>) object.get("metadata");
            String orderNumber = (metadata != null) ? metadata.get("order_id") : null;
            String paymentId = (String) object.get("id");

            log.info("Processing webhook for order: {}, status: {}", orderNumber, status);

            if (orderNumber == null) {
                log.error("Order number not found in webhook metadata!");
                return ResponseEntity.ok().build();
            }

            transactionTemplate.execute(statusTx -> {
                Order order = orderRepository.findByOrderNumber(orderNumber)
                    .orElseThrow(() -> new RuntimeException("Order not found: " + orderNumber));

                if ("payment.succeeded".equals(event) || "succeeded".equals(status)) {
                    log.info("Marking order {} as PAID", orderNumber);
                    order.setPaymentId(paymentId);
                    order.setPaymentStatus(PaymentStatus.COMPLETED);
                    order.setStatus(OrderStatus.PAID);
                    order.setPaidAt(LocalDateTime.now());
                    orderRepository.save(order);

                    orderService.updateOrderStatus(order.getOrderNumber(), OrderStatus.PAID, "Оплата подтверждена через Webhook ЮKassa");
                    notificationService.sendOrderConfirmation(order);
                    
                    try {
                        ru.sashil.service.InventoryService inventoryService =
                            org.springframework.web.context.support.WebApplicationContextUtils
                            .getRequiredWebApplicationContext(request.getServletContext())
                            .getBean(ru.sashil.service.InventoryService.class);
                        inventoryService.processFulfillment(order);
                    } catch (Exception e) {
                        log.error("Failed to trigger fulfillment from webhook", e);
                    }
                } else if ("payment.canceled".equals(event) || "canceled".equals(status)) {
                    log.info("Marking order {} as CANCELLED", orderNumber);
                    order.setPaymentStatus(PaymentStatus.FAILED);
                    order.setStatus(OrderStatus.CANCELLED);
                    order.setCancelledAt(LocalDateTime.now());
                    orderRepository.save(order);

                    orderService.updateOrderStatus(order.getOrderNumber(), OrderStatus.CANCELLED, "Оплата отменена (Webhook)");
                }
                return null;
            });
            return ResponseEntity.ok().build();

        } catch (Exception e) {
            log.error("Critical error in Webhook Controller: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
