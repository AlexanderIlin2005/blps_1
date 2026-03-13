package ru.sashil.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import ru.sashil.dto.PaymentResponse;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final RedisTemplate<String, Object> redisTemplate;
    private static final String PAYMENT_QUEUE = "yoomoney_payment_queue";

    @Value("${yoomoney.api.url}")
    private String yooMoneyUrl;

    @Value("${yoomoney.shop.id}")
    private String shopId;

    @Value("${yoomoney.secret.key}")
    private String secretKey;

    public PaymentResponse processPayment(String orderNumber, String paymentMethod, Double amount, String details) {
        log.info("Queuing YooKassa payment for order: {}", orderNumber);

        ru.sashil.dto.PaymentTask task = new ru.sashil.dto.PaymentTask(
            orderNumber, amount, paymentMethod, details, System.currentTimeMillis()
        );

        PaymentResponse response = new PaymentResponse();
        try {
            redisTemplate.opsForList().rightPush(PAYMENT_QUEUE, task);
            response.setStatus("PENDING");
            response.setMessage("Payment queued");
        } catch (Exception e) {
            log.error("Failed to queue payment: {}", e.getMessage());
            response.setStatus("FAILED");
        }
        return response;
    }

    public PaymentResponse checkPaymentStatus(String paymentId) {
        log.info("Checking YooKassa status for payment: {}", paymentId);
        try {
            HttpHeaders headers = createAuthHeaders();
            ResponseEntity<Map> responseEntity = restTemplate.exchange(
                yooMoneyUrl + "/" + paymentId,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class
            );

            PaymentResponse response = new PaymentResponse();
            response.setPaymentId(paymentId);
            if (responseEntity.getStatusCode().is2xxSuccessful()) {
                response.setStatus((String) responseEntity.getBody().get("status"));
            } else {
                response.setStatus("UNKNOWN");
            }
            return response;
        } catch (Exception e) {
            log.error("Failed to check payment status: {}", e.getMessage());
            throw new RuntimeException("Status check failed", e);
        }
    }

    public PaymentResponse refundPayment(String paymentId, Double amountValue) {
        log.info("Requesting YooKassa refund for payment: {} amount: {}", paymentId, amountValue);
        try {
            HttpHeaders headers = createAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Idempotence-Key", UUID.randomUUID().toString());

            Map<String, Object> amount = new HashMap<>();
            amount.put("value", String.format("%.2f", amountValue).replace(",", "."));
            amount.put("currency", "RUB");

            Map<String, Object> body = new HashMap<>();
            body.put("amount", amount);
            body.put("payment_id", paymentId);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> responseEntity = restTemplate.postForEntity(
                yooMoneyUrl.replace("/payments", "/refunds"), 
                entity, Map.class
            );

            PaymentResponse response = new PaymentResponse();
            response.setPaymentId(paymentId);
            if (responseEntity.getStatusCode().is2xxSuccessful()) {
                response.setStatus("REFUNDED");
                response.setMessage("Refund successful");
            } else {
                response.setStatus("FAILED");
            }
            return response;
        } catch (Exception e) {
            log.error("Refund failed: {}", e.getMessage());
            throw new RuntimeException("Refund failed", e);
        }
    }

    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        String auth = shopId + ":" + secretKey;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
        headers.set("Authorization", "Basic " + encodedAuth);
        return headers;
    }
}
