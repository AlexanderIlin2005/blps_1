package ru.sashil.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import ru.sashil.dto.PaymentTask;
import ru.sashil.model.Order;
import ru.sashil.model.OrderStatus;
import ru.sashil.model.PaymentStatus;
import ru.sashil.repository.OrderRepository;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentWorker {

    private final RedisTemplate<String, Object> redisTemplate;
    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;
    private final NotificationService notificationService;
    private final OrderService orderService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${yoomoney.api.url}")
    private String yooMoneyUrl;

    @Value("${yoomoney.shop.id}")
    private String shopId;

    @Value("${yoomoney.secret.key}")
    private String secretKey;

    private static final String PAYMENT_QUEUE = "yoomoney_payment_queue";

    @Scheduled(fixedDelay = 10000)
    public void checkPendingPayments() {
        java.util.List<Order> pendingOrders = orderRepository.findAll().stream()
            .filter(o -> o.getStatus() == OrderStatus.PAYMENT_PROCESSING && o.getPaymentId() != null)
            .collect(java.util.stream.Collectors.toList());

        for (Order order : pendingOrders) {
            try {
                log.info("Polling status for order {} (PaymentID: {})", order.getOrderNumber(), order.getPaymentId());
                
                HttpHeaders headers = new HttpHeaders();
                String auth = shopId + ":" + secretKey;
                String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
                headers.set("Authorization", "Basic " + encodedAuth);

                ResponseEntity<Map> response = restTemplate.exchange(
                    yooMoneyUrl + "/" + order.getPaymentId(),
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class
                );

                if (response.getStatusCode().is2xxSuccessful()) {
                    String status = (String) response.getBody().get("status");
                    log.info("YooKassa status for {}: {}", order.getOrderNumber(), status);

                    if ("succeeded".equals(status)) {
                        finalizePayment(order, order.getPaymentId(), true, "Оплата подтверждена через опрос API");
                    } else if ("canceled".equals(status)) {
                        finalizePayment(order, null, false, "Оплата отменена (подтверждено через опрос API)");
                    }
                }
            } catch (Exception e) {
                log.error("Failed to poll status for order {}: {}", order.getOrderNumber(), e.getMessage());
            }
        }
    }

    @Scheduled(fixedDelay = 5000)
    public void processPaymentQueue() {
        while (true) {
            PaymentTask task = (PaymentTask) redisTemplate.opsForList().leftPop(PAYMENT_QUEUE);
            if (task == null) break;

            log.info("Processing payment task from Redis for order: {}", task.getOrderNumber());
            processTask(task);
        }
    }

    private void processTask(PaymentTask task) {
        try {
            Order order = orderRepository.findByOrderNumber(task.getOrderNumber())
                .orElseThrow(() -> new RuntimeException("Order not found: " + task.getOrderNumber()));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String auth = shopId + ":" + secretKey;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
            headers.set("Authorization", "Basic " + encodedAuth);
            headers.set("Idempotence-Key", UUID.randomUUID().toString());

            Map<String, Object> amount = new HashMap<>();
            amount.put("value", String.format("%.2f", task.getAmount()).replace(",", "."));
            amount.put("currency", "RUB");

            Map<String, Object> body = new HashMap<>();
            body.put("amount", amount);
            body.put("capture", true);
            body.put("description", "Оплата заказа №" + task.getOrderNumber());

            Map<String, String> paymentMethodData = new HashMap<>();
            paymentMethodData.put("type", "bank_card");
            body.put("payment_method_data", paymentMethodData);

            Map<String, Object> confirmation = new HashMap<>();
            confirmation.put("type", "redirect");
            confirmation.put("return_url", "https://gitea.timoapp.tech/payment-result?orderNumber=" + task.getOrderNumber());
            body.put("confirmation", confirmation);
            
            Map<String, String> metadata = new HashMap<>();
            metadata.put("order_id", task.getOrderNumber());
            body.put("metadata", metadata);

            log.info("--- Sending REAL request to YooKassa API for order {} ---", task.getOrderNumber());
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            
            try {
                ResponseEntity<Map> response = restTemplate.postForEntity(yooMoneyUrl, entity, Map.class);
                
                if (response.getStatusCode().is2xxSuccessful()) {
                    Map responseBody = response.getBody();
                    String paymentId = (String) responseBody.get("id");
                    String status = (String) responseBody.get("status");
                    
                    Map<String, Object> confirmationResponse = (Map<String, Object>) responseBody.get("confirmation");
                    String confirmationUrl = null;
                    if (confirmationResponse != null && "redirect".equals(confirmationResponse.get("type"))) {
                        confirmationUrl = (String) confirmationResponse.get("confirmation_url");
                    }

                    log.info("YooKassa Pending: Status={}, ID={}, ConfirmationUrl={}", status, paymentId, confirmationUrl);

                    order.setPaymentId(paymentId);
                    order.setPaymentConfirmationUrl(confirmationUrl);
                    orderRepository.save(order);
                    
                    orderService.updateOrderStatus(order.getOrderNumber(), OrderStatus.PAYMENT_PROCESSING, 
                        "Платеж создан. Ожидание перехода пользователя на страницу оплаты.");
                } else {
                    log.error("YooKassa Error: HTTP {}", response.getStatusCode());
                    finalizePayment(order, null, false, "YooKassa error: " + response.getStatusCode());
                }
            } catch (Exception e) {
                log.error("YooKassa API Call Failed: {}", e.getMessage());
                finalizePayment(order, null, false, "Connection/Auth error: " + e.getMessage());
            }

        } catch (Exception e) {
            log.error("Worker error for order {}: {}", task.getOrderNumber(), e.getMessage());
        }
    }

    private void finalizePayment(Order order, String paymentId, boolean success, String message) {
        if (success) {
            order.setPaymentId(paymentId);
            order.setPaymentStatus(PaymentStatus.COMPLETED);
            order.setStatus(OrderStatus.PAID);
            order.setPaidAt(LocalDateTime.now());
            orderRepository.save(order);

            orderService.updateOrderStatus(order.getOrderNumber(), OrderStatus.PAID, message);
            notificationService.sendOrderConfirmation(order);
            
            log.info("Order {} finalized as PAID", order.getOrderNumber());
            inventoryService.processFulfillment(order);
        } else {
            order.setPaymentStatus(PaymentStatus.FAILED);
            order.setStatus(OrderStatus.CANCELLED);
            order.setCancelledAt(LocalDateTime.now());
            orderRepository.save(order);
            
            orderService.updateOrderStatus(order.getOrderNumber(), OrderStatus.CANCELLED, message);
            log.warn("Order {} finalized as FAILED", order.getOrderNumber());
        }
    }
}
