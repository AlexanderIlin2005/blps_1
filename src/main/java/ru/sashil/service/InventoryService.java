package ru.sashil.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
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

    @Async
    public CompletableFuture<Void> processFulfillment(Order order) {
        log.info("🚀 Starting fulfillment for order: {}", order.getOrderNumber());

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Обновляем статус на PROCESSING
                order.setStatus(OrderStatus.PROCESSING);
                orderRepository.save(order);
                log.info("Order {} status updated to PROCESSING", order.getOrderNumber());

                // Небольшая пауза для имитации работы
                Thread.sleep(1500);

                // Проверка наличия на складе
                log.info("Checking stock for order: {}", order.getOrderNumber());
                boolean allInStock = checkStock(order);

                if (allInStock) {
                    log.info("All items in stock for order: {}", order.getOrderNumber());

                    order.setStatus(OrderStatus.PACKING);
                    orderRepository.save(order);
                    log.info("Order {} status updated to PACKING", order.getOrderNumber());

                    // Сборка заказа
                    log.info("Packing order: {}", order.getOrderNumber());
                    Thread.sleep(2000); // Имитация времени сборки

                    order.setStatus(OrderStatus.READY_FOR_SHIPPING);
                    orderRepository.save(order);
                    log.info("Order {} ready for shipping", order.getOrderNumber());

                    // Передача в доставку
                    log.info("Handing over to delivery service: {}", order.getOrderNumber());
                    deliveryService.handoverToDelivery(order);

                    log.info("✅ Fulfillment completed for order: {}", order.getOrderNumber());
                } else {
                    log.warn("❌ Some items out of stock for order: {}", order.getOrderNumber());

                    // Возврат средств
                    order.setStatus(OrderStatus.CANCELLED);
                    orderRepository.save(order);

                    notificationService.sendOutOfStockNotification(order);
                }
            } catch (Exception e) {
                log.error("Error in fulfillment for order: {}", order.getOrderNumber(), e);
            }
            return null;
        });
    }

    private boolean checkStock(Order order) {
        return order.getItems().stream().allMatch(item -> {
            Product product = productRepository.findBySku(item.getProductId()).orElse(null);
            if (product == null) {
                log.warn("Product not found: {}", item.getProductId());
                return false;
            }

            boolean inStock = product.getStockQuantity() >= item.getQuantity();
            if (inStock) {
                // Резервируем товар
                product.setStockQuantity(product.getStockQuantity() - item.getQuantity());
                productRepository.save(product);
                log.info("Reserved {} units of product: {}", item.getQuantity(), product.getName());
            } else {
                log.warn("Not enough stock for product: {}. Required: {}, Available: {}",
                    product.getName(), item.getQuantity(), product.getStockQuantity());
            }
            return inStock;
        });
    }
}
