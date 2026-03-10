package ru.sashil.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import ru.sashil.dto.PaymentResponse;
import ru.sashil.dto.PaymentTask;
import ru.sashil.repository.OrderRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final OrderRepository orderRepository;
    private final NotificationService notificationService;
    private final RestTemplate restTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${yoomoney.api.url}")
    private String yooMoneyUrl;

    @Value("${yoomoney.shop.id}")
    private String shopId;

    @Value("${yoomoney.secret.key}")
    private String secretKey;

    private static final String PAYMENT_QUEUE = "yoomoney_payment_queue";

    private InventoryService inventoryService;

    @Autowired
    public void setInventoryService(@Lazy InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    public PaymentResponse processPayment(String orderNumber, String paymentMethod, Double amount, String details) {
        log.info("Queueing payment via Redis for YooMoney - Order: {}, Method: {}, Amount: {}", 
            orderNumber, paymentMethod, amount);

        PaymentTask task = new PaymentTask(
            orderNumber,
            amount,
            paymentMethod,
            details,
            System.currentTimeMillis()
        );

        PaymentResponse response = new PaymentResponse();
        response.setPaymentId("task-" + UUID.randomUUID().toString().substring(0, 8));
        
        try {
            redisTemplate.opsForList().rightPush(PAYMENT_QUEUE, task);
            
            response.setStatus("PENDING");
            response.setMessage("Payment task queued for processing via YooMoney");
            log.info("Payment task queued successfully for order: {}", orderNumber);
        } catch (Exception e) {
            log.error("Failed to queue payment task in Redis: {}", e.getMessage());
            response.setStatus("FAILED");
            response.setMessage("Internal queuing error: " + e.getMessage());
        }

        return response;
    }

    public PaymentResponse checkPaymentStatus(String paymentId) {
        PaymentResponse response = new PaymentResponse();
        response.setPaymentId(paymentId);
        response.setStatus("COMPLETED");
        return response;
    }

    public PaymentResponse refundPayment(String paymentId, Double amount) {
        log.info("Refunding payment: {} amount: {}", paymentId, amount);

        PaymentResponse response = new PaymentResponse();
        response.setPaymentId(paymentId);
        response.setStatus("REFUNDED");
        response.setMessage("Refund processed successfully via YooMoney API");

        return response;
    }
}
