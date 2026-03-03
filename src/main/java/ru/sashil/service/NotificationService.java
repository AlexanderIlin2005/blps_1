package ru.sashil.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.sashil.model.Order;

@Service
@Slf4j
public class NotificationService {

    public void sendOrderConfirmation(Order order) {
        log.info("Sending order confirmation to {} for order {}",
            order.getCustomerEmail(), order.getOrderNumber());

        // Здесь будет реальная отправка email
        log.info("Email content: Your order {} has been confirmed. Total: {}",
            order.getOrderNumber(), order.getTotal());
    }

    public void sendTrackingInfo(Order order) {
        log.info("Sending tracking info to {} for order {}",
            order.getCustomerEmail(), order.getOrderNumber());

        log.info("Tracking number: {} for order {}",
            order.getTrackingNumber(), order.getOrderNumber());
    }

    public void sendOrderCancelledNotification(Order order) {
        log.info("Sending cancellation notification to {} for order {}",
            order.getCustomerEmail(), order.getOrderNumber());

        log.info("Order {} has been cancelled due to non-payment",
            order.getOrderNumber());
    }

    public void sendOutOfStockNotification(Order order) {
        log.info("Sending out of stock notification to {} for order {}",
            order.getCustomerEmail(), order.getOrderNumber());

        log.info("Some items in order {} are out of stock. Refund initiated.",
            order.getOrderNumber());
    }
}
