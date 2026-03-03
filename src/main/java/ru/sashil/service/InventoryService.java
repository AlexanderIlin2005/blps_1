package ru.sashil.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final DeliveryService deliveryService;

    @Async
    public CompletableFuture<Void> processFulfillment(Order order) {
        log.info("Starting fulfillment for order: {}", order.getOrderNumber());

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Проверка наличия на складе
                boolean allInStock = checkStock(order);

                if (allInStock) {
                    order.setStatus(OrderStatus.PACKING);
                    orderRepository.save(order);

                    // Сборка заказа
                    Thread.sleep(2000); // Имитация времени сборки

                    order.setStatus(OrderStatus.READY_FOR_SHIPPING);
                    orderRepository.save(order);

                    // Передача в доставку
                    deliveryService.handoverToDelivery(order);

                    log.info("Fulfillment completed for order: {}", order.getOrderNumber());
                } else {
                    // Возврат средств
                    order.setStatus(OrderStatus.CANCELLED);
                    orderRepository.save(order);

                    log.warn("Order cancelled due to stock issues: {}", order.getOrderNumber());
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
            if (product == null) return false;

            boolean inStock = product.getStockQuantity() >= item.getQuantity();
            if (inStock) {
                // Резервируем товар
                product.setStockQuantity(product.getStockQuantity() - item.getQuantity());
                productRepository.save(product);
            }
            return inStock;
        });
    }
}
