package ru.sashil.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import ru.sashil.dto.PaymentResponse;
import ru.sashil.model.Order;
import ru.sashil.repository.OrderRepository;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final OrderRepository orderRepository;
    private final NotificationService notificationService;
    private final RestTemplate restTemplate;

    @Value("${payment.service.url}")
    private String paymentServiceUrl;

    // Используем @Lazy для разрыва цикла
    private InventoryService inventoryService;

    @Autowired
    public void setInventoryService(@Lazy InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    // Внешний запрос к платежной системе
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExternalPaymentRequest {
        private String order_id;
        private Double amount;
        private String currency = "RUB";
        private String card_number;
    }

    // Внешний ответ от платежной системы
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExternalPaymentResponse {
        private String transaction_id;
        private String status;
        private String message;
        private String timestamp;
    }

    public PaymentResponse processPayment(String orderNumber, String paymentMethod, Double amount, String details) {
        log.info("Processing payment via external microservice - Order: {}, Method: {}, Amount: {}", 
            orderNumber, paymentMethod, amount);

        String cardNumber = "unknown";
        if ("card".equals(paymentMethod) && details != null) {
            String[] parts = details.split("\\|");
            if (parts.length > 0) {
                cardNumber = parts[0];
            }
        } else if ("sbp".equals(paymentMethod)) {
            cardNumber = details; // Используем телефон как номер карты для эмуляции
        }

        ExternalPaymentRequest externalRequest = new ExternalPaymentRequest(
            orderNumber,
            amount,
            "RUB",
            cardNumber
        );

        PaymentResponse response = new PaymentResponse();
        try {
            ExternalPaymentResponse externalResponse = restTemplate.postForObject(
                paymentServiceUrl, 
                externalRequest, 
                ExternalPaymentResponse.class
            );

            if (externalResponse != null) {
                response.setPaymentId(externalResponse.getTransaction_id());
                // Мок возвращает "APPROVED" при успехе
                if ("APPROVED".equalsIgnoreCase(externalResponse.getStatus())) {
                    response.setStatus("SUCCESS");
                    response.setMessage("Payment approved: " + externalResponse.getMessage());
                } else {
                    // Обрабатываем "DECLINED", "BLOCKED" и другие
                    response.setStatus("FAILED");
                    response.setMessage(externalResponse.getStatus() + ": " + externalResponse.getMessage());
                }
            } else {
                response.setStatus("FAILED");
                response.setMessage("Empty response from payment service");
            }
        } catch (Exception e) {
            log.error("Error calling external payment service: {}", e.getMessage());
            response.setStatus("FAILED");
            // Проверяем на таймаут
            if (e.getMessage().contains("Read timed out")) {
                response.setMessage("Payment timeout (5s delay simulated)");
            } else {
                response.setMessage("Payment system error: " + e.getMessage());
            }
        }

        if ("SUCCESS".equals(response.getStatus())) {
            log.info("Payment successful for order: {}", orderNumber);

            // Асинхронно запускаем фулфилмент после успешной оплаты
            CompletableFuture.runAsync(() -> {
                try {
                    Order order = orderRepository.findByOrderNumber(orderNumber)
                        .orElseThrow(() -> new RuntimeException("Order not found: " + orderNumber));

                    log.info("Starting fulfillment for paid order: {}", orderNumber);
                    inventoryService.processFulfillment(order);

                } catch (Exception e) {
                    log.error("Error starting fulfillment for order {}: {}", orderNumber, e.getMessage());
                }
            });
        } else {
            log.warn("Payment failed for order: {}. Status: {}, Message: {}", 
                orderNumber, response.getStatus(), response.getMessage());
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
        response.setMessage("Refund processed successfully");

        return response;
    }
}
