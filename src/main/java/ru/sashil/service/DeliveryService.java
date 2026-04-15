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

            Order completedOrder = transactionTemplate.execute(status -> {
                try {
                    Order order = processDelivery(orderNumber);
                    sendToAccounting(payload);
                    return order;
                } catch (Exception e) {
                    log.error("Failed to process delivery for order: {}", orderNumber, e);
                    status.setRollbackOnly();
                    throw new RuntimeException(e);
                }
            });
            if (completedOrder != null) {
                notificationService.sendDeliveryNotification(completedOrder);
            }

        } catch (JMSException e) {
            log.error("JMS error in delivery listener", e);
        }
    }

    private Order processDelivery(String orderNumber) throws Exception {
        Order order = orderRepository.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new RuntimeException("Order not found: " + orderNumber));

        log.info("Handing over order to delivery: {}", order.getOrderNumber());

        String trackingNumber = generateTrackingNumber();
        orderService.updateTracking(order.getOrderNumber(), trackingNumber);

        // Стадия 1: Передача в доставку
        orderService.updateOrderStatus(order.getOrderNumber(), OrderStatus.OUT_FOR_DELIVERY,
            "Заказ передан в службу доставки, трек-номер: " + trackingNumber);
        waitForStageTransition();

        if (order.getDeliveryType() == DeliveryType.COURIER) {
            // Стадия 2: Курьер выехал
            orderService.updateOrderStatus(order.getOrderNumber(), OrderStatus.OUT_FOR_DELIVERY,
                "Курьер выехал по адресу: " + order.getDeliveryAddress());
            waitForStageTransition();
            
            // Стадия 3: Доставлен
            orderService.updateOrderStatus(order.getOrderNumber(), OrderStatus.DELIVERED,
                "Заказ доставлен курьером");
        } else {
            orderService.updateOrderStatus(order.getOrderNumber(), OrderStatus.PICKUP_READY,
                "Заказ готов к выдаче в ПВЗ: " + order.getPickupPointAddress());
            waitForStageTransition();
            orderService.updateOrderStatus(order.getOrderNumber(), OrderStatus.PICKED_UP,
                "Заказ получен в пункте самовывоза");
        }

        waitForStageTransition();
        orderService.completeDelivery(order.getOrderNumber());
        log.info("Order {} delivery completed", order.getOrderNumber());
        return order;
    }

    void waitForStageTransition() throws InterruptedException {
        Thread.sleep(20000);
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

            String response = output.getPayload();
            log.info("Accounting system response: {}", response);
            if (response == null || response.startsWith("ERROR")) {
                throw new RuntimeException("Accounting system rejected payload: " + response);
            }
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
