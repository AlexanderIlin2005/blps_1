package ru.sashil.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import org.springframework.transaction.support.TransactionTemplate;
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

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Value("${yoomoney.api.url}")
    private String yooMoneyUrl;

    @Value("${yoomoney.shop.id}")
    private String shopId;

    @Value("${yoomoney.secret.key}")
    private String secretKey;

    @Value("${yoomoney.return.url}")
    private String returnUrl;

    private static final String PAYMENT_QUEUE = "yoomoney_payment_queue";

    @Scheduled(fixedDelay = 15000)
    public void pollPendingPayments() {
        log.info("Polling for pending payments in YooKassa...");
        java.util.List<Order> pendingOrders = orderRepository.findAll().stream()
            .filter(o -> o.getStatus() == OrderStatus.PAYMENT_PROCESSING && o.getPaymentId() != null)
            .collect(java.util.stream.Collectors.toList());

        for (Order order : pendingOrders) {
            checkAndUpdateOrder(order);
        }
    }

    private void checkAndUpdateOrder(Order order) {
        try {
            HttpHeaders headers = createAuthHeaders();
            ResponseEntity<Map> response = restTemplate.exchange(
                yooMoneyUrl + "/" + order.getPaymentId(),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                String status = (String) response.getBody().get("status");
                log.info("Current YooKassa status for {}: {}", order.getOrderNumber(), status);

                if ("succeeded".equals(status)) {
                    transactionTemplate.execute(statusTx -> {
                        finalizePayment(order, order.getPaymentId(), true, "Оплата подтверждена (опрос API)");
                        return null;
                    });
                } else if ("canceled".equals(status)) {
                    transactionTemplate.execute(statusTx -> {
                        finalizePayment(order, null, false, "Оплата отменена (опрос API)");
                        return null;
                    });
                }
            }
        } catch (Exception e) {
            log.error("Failed to poll status for order {}: {}", order.getOrderNumber(), e.getMessage());
        }
    }

    @Scheduled(fixedDelay = 5000)
    public void processPaymentQueue() {
        while (true) {
            PaymentTask task = (PaymentTask) redisTemplate.opsForList().leftPop(PAYMENT_QUEUE);
            if (task == null) break;
            processTask(task);
        }
    }

    private void processTask(PaymentTask task) {
        try {
            log.info("Processing YooKassa payment for order: {}", task.getOrderNumber());
            
            HttpHeaders headers = createAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Idempotence-Key", UUID.randomUUID().toString());

            Map<String, Object> body = new HashMap<>();
            body.put("amount", Map.of(
                "value", String.format("%.2f", task.getAmount()).replace(",", "."),
                "currency", "RUB"
            ));
            body.put("capture", true);
            body.put("description", "Оплата заказа №" + task.getOrderNumber());
            body.put("confirmation", Map.of(
                "type", "redirect",
                "return_url", returnUrl + "?orderNumber=" + task.getOrderNumber()
            ));
            body.put("metadata", Map.of("order_id", task.getOrderNumber()));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(yooMoneyUrl, entity, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                Map responseBody = response.getBody();
                String paymentId = (String) responseBody.get("id");
                
                Map<String, Object> conf = (Map<String, Object>) responseBody.get("confirmation");
                String confUrl = (conf != null) ? (String) conf.get("confirmation_url") : null;

                transactionTemplate.execute(status -> {
                    Order order = orderRepository.findByOrderNumber(task.getOrderNumber()).get();
                    order.setPaymentId(paymentId);
                    order.setPaymentConfirmationUrl(confUrl);
                    orderRepository.save(order);
                    
                    orderService.updateOrderStatus(order.getOrderNumber(), OrderStatus.PAYMENT_PROCESSING, 
                        "Платеж создан. Ожидание оплаты пользователем.");
                    return null;
                });
            }
        } catch (Exception e) {
            log.error("Payment task failed for order {}: {}", task.getOrderNumber(), e.getMessage());
        }
    }

    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        String auth = shopId + ":" + secretKey;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
        headers.set("Authorization", "Basic " + encodedAuth);
        return headers;
    }

    private void finalizePayment(Order order, String paymentId, boolean success, String message) {
        Order freshOrder = orderRepository.findById(order.getId()).get();
        if (success) {
            freshOrder.setPaymentId(paymentId);
            freshOrder.setPaymentStatus(PaymentStatus.COMPLETED);
            freshOrder.setStatus(OrderStatus.PAID);
            freshOrder.setPaidAt(LocalDateTime.now());
            orderRepository.save(freshOrder);
            orderService.updateOrderStatus(freshOrder.getOrderNumber(), OrderStatus.PAID, message);
            notificationService.sendOrderConfirmation(freshOrder);
            inventoryService.processFulfillment(freshOrder);
        } else {
            freshOrder.setPaymentStatus(PaymentStatus.FAILED);
            freshOrder.setStatus(OrderStatus.CANCELLED);
            freshOrder.setCancelledAt(LocalDateTime.now());
            orderRepository.save(freshOrder);
            orderService.updateOrderStatus(freshOrder.getOrderNumber(), OrderStatus.CANCELLED, message);
        }
    }
}
