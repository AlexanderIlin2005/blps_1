package ru.sashil.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.sashil.dto.PaymentResponse;

import java.util.UUID;

@Service
@Slf4j
public class PaymentService {

    // Заглушка платежной системы
    public PaymentResponse processPayment(String orderNumber, String paymentMethod, Double amount, String details) {
        log.info("Processing payment - Order: {}, Method: {}, Amount: {}", orderNumber, paymentMethod, amount);

        // Имитация обработки платежа
        PaymentResponse response = new PaymentResponse();
        response.setPaymentId("PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());

        // Имитация успешного платежа (90%成功率)
        if (Math.random() < 0.9) {
            response.setStatus("SUCCESS");
            response.setMessage("Payment processed successfully");
            log.info("Payment successful for order: {}", orderNumber);
        } else {
            response.setStatus("FAILED");
            response.setMessage("Payment failed: insufficient funds");
            log.warn("Payment failed for order: {}", orderNumber);
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
