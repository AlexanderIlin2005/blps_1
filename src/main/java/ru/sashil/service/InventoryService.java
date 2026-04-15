package ru.sashil.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;
import ru.sashil.dto.FulfillmentTask;
import ru.sashil.model.Order;
import ru.sashil.model.OrderStatus;
import ru.sashil.model.Product;
import ru.sashil.repository.OrderRepository;
import ru.sashil.repository.ProductRepository;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final NotificationService notificationService;
    private final JmsTemplate jmsTemplate;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;
    private final StompSenderService stompSenderService;

    @Value("${fulfillment.queue.name}")
    private String fulfillmentQueueName;

    @Value("${delivery.queue.name}")
    private String deliveryQueueName;

    private OrderService orderService;
    private DeliveryService deliveryService;

    @Autowired
    public void setOrderService(@Lazy OrderService orderService) {
        this.orderService = orderService;
    }

    @Autowired
    public void setDeliveryService(@Lazy DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    /**
     * Отправляет задачу фулфилмента в распределённую очередь ActiveMQ.
     * Больше не выполняет работу напрямую.
     */
    public CompletableFuture<Void> processFulfillment(Order order) {
        log.info("Queueing fulfillment task for order: {}", order.getOrderNumber());

        FulfillmentTask task = new FulfillmentTask(
            order.getOrderNumber(),
            order.getId(),
            System.currentTimeMillis()
        );

        try {
            jmsTemplate.convertAndSend(fulfillmentQueueName, task);
            log.info("Fulfillment task queued successfully for order: {}", order.getOrderNumber());
        } catch (Exception e) {
            log.error("Failed to queue fulfillment task for order: {}", order.getOrderNumber(), e);
            orderService.updateOrderStatus(order.getOrderNumber(), OrderStatus.CANCELLED,
                "Ошибка при постановке в очередь фулфилмента: " + e.getMessage());
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Реальная бизнес-логика фулфилмента (вызывается из JMS-слушателя).
     */
    public void executeFulfillment(FulfillmentTask task) {
        log.info("Executing fulfillment for order: {}", task.getOrderNumber());

        transactionTemplate.execute(status -> {
            try {
                Order order = orderRepository.findById(task.getOrderId())
                    .orElseThrow(() -> new RuntimeException("Order not found: " + task.getOrderNumber()));

                orderService.updateOrderStatus(order.getOrderNumber(), OrderStatus.PROCESSING,
                    "Начата обработка заказа на складе (распределённая очередь)");

                log.info("Checking stock for order: {}", order.getOrderNumber());
                boolean allInStock = checkStockAndReserve(order);

                if (allInStock) {
                    log.info("All items in stock for order: {}", order.getOrderNumber());

                    orderService.updateOrderStatus(order.getOrderNumber(), OrderStatus.PACKING,
                        "Товары в наличии, начата сборка заказа");

                    orderService.updateOrderStatus(order.getOrderNumber(), OrderStatus.READY_FOR_SHIPPING,
                        "Заказ собран и упакован, готов к передаче в доставку");

                    log.info("Handing over to delivery service: {}", order.getOrderNumber());
                    stompSenderService.sendToQueue(deliveryQueueName, "{\"orderNumber\":\"" + order.getOrderNumber() + "\",\"total\":" + order.getTotal() + "}");

                    log.info("Fulfillment completed for order: {}", order.getOrderNumber());
                } else {
                    log.warn("Some items out of stock for order: {}", order.getOrderNumber());

                    orderService.updateOrderStatus(order.getOrderNumber(), OrderStatus.CANCELLED,
                        "Товара нет в наличии, заказ отменен");

                    notificationService.sendOutOfStockNotification(order);
                }
            } catch (Exception e) {
                log.error("Error in fulfillment for order: {}", task.getOrderNumber(), e);
                status.setRollbackOnly();
                try {
                    Order order = orderRepository.findById(task.getOrderId()).orElse(null);
                    if (order != null) {
                        orderService.updateOrderStatus(order.getOrderNumber(), OrderStatus.CANCELLED,
                            "Ошибка при выполнении фулфилмента: " + e.getMessage());
                    }
                } catch (Exception ex) {
                    log.error("Failed to update order status after fulfillment error", ex);
                }
            }
            return null;
        });
    }

    private boolean checkStockAndReserve(Order order) {
        boolean allInStock = true;
        for (var item : order.getItems()) {
            Product product = productRepository.findBySku(item.getProductId()).orElse(null);
            if (product == null || product.getStockQuantity() < item.getQuantity()) {
                allInStock = false;
                break;
            }
        }

        if (allInStock) {
            for (var item : order.getItems()) {
                Product product = productRepository.findBySku(item.getProductId()).get();
                product.setStockQuantity(product.getStockQuantity() - item.getQuantity());
                productRepository.save(product);
            }
        }

        return allInStock;
    }
}
