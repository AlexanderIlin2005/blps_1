package ru.sashil.service;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;
import jakarta.resource.cci.Connection;
import jakarta.resource.cci.ConnectionFactory;
import jakarta.resource.cci.Interaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import ru.sashil.jca.StringRecord;
import ru.sashil.model.DeliveryType;
import ru.sashil.model.Order;
import ru.sashil.model.OrderStatus;
import ru.sashil.repository.OrderRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryService {

    private final OrderService orderService;
    private final NotificationService notificationService;
    private final OrderRepository orderRepository;
    private final TransactionTemplate transactionTemplate;
    private final ConnectionFactory accountingConnectionFactory;

    @JmsListener(destination = "${delivery.queue.name}", containerFactory = "jmsListenerContainerFactory")
    public void onDeliveryMessage(Message message) {
        if (!(message instanceof TextMessage)) {
            log.warn("Received non-text message in delivery queue");
            return;
        }

        try {
            String payload = ((TextMessage) message).getText();
            log.info("Received delivery task: {}", payload);
            
            // Simple JSON parsing (orderNumber extraction)
            String orderNumber = payload.split("\"orderNumber\":\"")[1].split("\"")[0];

            transactionTemplate.execute(status -> {
                try {
                    processDelivery(orderNumber);
                    
                    // JCA Integration with Accounting
                    sendToAccounting(payload);
                    
                } catch (Exception e) {
                    log.error("Failed to process delivery for order: {}", orderNumber, e);
                    status.setRollbackOnly();
                    throw new RuntimeException(e);
                }
                return null;
            });

        } catch (JMSException e) {
            log.error("JMS error in delivery listener", e);
        }
    }

    private void processDelivery(String orderNumber) throws Exception {
        Order order = orderRepository.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new RuntimeException("Order not found: " + orderNumber));

        log.info("Handing over order to delivery: {}", order.getOrderNumber());

        String trackingNumber = generateTrackingNumber();
        orderService.updateTracking(order.getOrderNumber(), trackingNumber);

        // Стадия 1: Передача в доставку
        orderService.updateOrderStatus(order.getOrderNumber(), OrderStatus.OUT_FOR_DELIVERY,
            "Заказ передан в службу доставки, трек-номер: " + trackingNumber);
        Thread.sleep(20000); // 20 секунд

        if (order.getDeliveryType() == DeliveryType.COURIER) {
            // Стадия 2: Курьер выехал
            orderService.updateOrderStatus(order.getOrderNumber(), OrderStatus.OUT_FOR_DELIVERY,
                "Курьер выехал по адресу: " + order.getDeliveryAddress());
            Thread.sleep(20000); // 20 секунд
            
            // Стадия 3: Доставлен
            orderService.updateOrderStatus(order.getOrderNumber(), OrderStatus.DELIVERED,
                "Заказ доставлен курьером");
        } else {
            orderService.updateOrderStatus(order.getOrderNumber(), OrderStatus.PICKUP_READY,
                "Заказ готов к выдаче в ПВЗ: " + order.getPickupPointAddress());
            Thread.sleep(20000); // 20 секунд
            orderService.updateOrderStatus(order.getOrderNumber(), OrderStatus.PICKED_UP,
                "Заказ получен в пункте самовывоза");
        }

        Thread.sleep(20000); // 20 секунд до завершения
        orderService.completeDelivery(order.getOrderNumber());
        notificationService.sendDeliveryNotification(order);
        log.info("Order {} delivery completed", order.getOrderNumber());
    }

    private void sendToAccounting(String payload) throws Exception {
        log.info("Sending delivery data to Accounting system via JCA...");
        Connection connection = null;
        Interaction interaction = null;
        try {
            connection = accountingConnectionFactory.getConnection();
            interaction = connection.createInteraction();
            
            StringRecord input = new StringRecord();
            input.setRecordName("AccountingRecord");
            input.setPayload(payload);
            
            StringRecord output = new StringRecord();
            interaction.execute(null, input, output);
            
            log.info("Accounting system response: {}", output.getPayload());
        } finally {
            if (interaction != null) {
                try { interaction.close(); } catch (Exception e) { log.warn("Failed to close JCA interaction", e); }
            }
            if (connection != null) {
                try { connection.close(); } catch (Exception e) { log.warn("Failed to close JCA connection", e); }
            }
        }
    }

    private String generateTrackingNumber() {
        return "TRK" + System.currentTimeMillis() + (int)(Math.random() * 1000);
    }
}
