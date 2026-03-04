package ru.sashil.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sashil.dto.CartItemDTO;
import ru.sashil.dto.CreateOrderRequest;
import ru.sashil.dto.OrderResponse;
import ru.sashil.dto.PaymentResponse;
import ru.sashil.model.*;
import ru.sashil.repository.OrderRepository;
import ru.sashil.repository.OrderStatusHistoryRepository;
import ru.sashil.repository.ProductRepository;
import ru.sashil.repository.UserRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final NotificationService notificationService;

    // Используем @Lazy для разрыва цикла
    private PaymentService paymentService;
    private InventoryService inventoryService;

    @Autowired
    public void setPaymentService(@Lazy PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Autowired
    public void setInventoryService(@Lazy InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        log.info("Creating new order for customer: {}", request.getCustomerName());

        User user = userRepository.findById(request.getCustomerId())
            .orElseThrow(() -> new RuntimeException("User not found"));

        // Проверка наличия товаров
        for (CartItemDTO item : request.getItems()) {
            Product product = productRepository.findBySku(item.getProductId())
                .orElseThrow(() -> new RuntimeException("Product not found: " + item.getProductId()));

            if (product.getStockQuantity() < item.getQuantity()) {
                throw new RuntimeException("Not enough stock for product: " + product.getName());
            }
        }

        // Создание заказа
        Order order = new Order();
        order.setOrderNumber(generateOrderNumber());
        order.setUser(user); // Устанавливаем связь с пользователем
        order.setCustomerId(request.getCustomerId());
        order.setCustomerName(request.getCustomerName());
        order.setCustomerEmail(request.getCustomerEmail());
        order.setCustomerPhone(request.getCustomerPhone());
        order.setStatus(OrderStatus.CHECKOUT);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        order.setDeliveryType(request.getDeliveryType());

        if (request.getDeliveryType() == DeliveryType.COURIER) {
            order.setDeliveryAddress(request.getDeliveryAddress());
        } else {
            order.setPickupPointId(request.getPickupPointId());
            order.setPickupPointAddress(request.getPickupPointAddress());
        }

        // Добавление товаров
        List<OrderItem> orderItems = new ArrayList<>();
        double subtotal = 0;

        for (CartItemDTO item : request.getItems()) {
            Product product = productRepository.findBySku(item.getProductId())
                .orElseThrow(() -> new RuntimeException("Product not found: " + item.getProductId()));

            OrderItem orderItem = new OrderItem();
            orderItem.setProductId(item.getProductId());
            orderItem.setProductName(product.getName()); // Берем название из базы
            orderItem.setQuantity(item.getQuantity());
            orderItem.setPrice(product.getPrice()); // КРИТИЧЕСКИ ВАЖНО: Берем цену из базы!
            orderItem.setTotal(product.getPrice() * item.getQuantity());
            orderItem.setOrder(order);

            orderItems.add(orderItem);
            subtotal += orderItem.getTotal();
        }

        order.setItems(orderItems);
        order.setSubtotal(subtotal);
        order.setDiscount(0.0);
        order.setTotal(subtotal);
        order.setPaymentStatus(PaymentStatus.PENDING);

        Order savedOrder = orderRepository.save(order);

        // Записываем историю
        addStatusHistory(savedOrder, OrderStatus.CHECKOUT, "Заказ создан");

        log.info("Order created with number: {}", savedOrder.getOrderNumber());

        return mapToResponse(savedOrder);
    }

    @Transactional
    public OrderResponse processPayment(String orderNumber, String paymentMethod, String paymentDetails) {
        log.info("Processing payment for order: {}", orderNumber);

        Order order = orderRepository.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new RuntimeException("Order not found: " + orderNumber));

        order.setPaymentMethod(paymentMethod);
        order.setPaymentStatus(PaymentStatus.PROCESSING);
        order.setStatus(OrderStatus.PAYMENT_PROCESSING);
        orderRepository.save(order);

        addStatusHistory(order, OrderStatus.PAYMENT_PROCESSING,
            "Обработка платежа методом: " + getPaymentMethodName(paymentMethod));

        // Заглушка платежной системы
        PaymentResponse paymentResponse = paymentService.processPayment(
            orderNumber,
            paymentMethod,
            order.getTotal(),
            paymentDetails
        );

        if ("SUCCESS".equals(paymentResponse.getStatus())) {
            order.setPaymentId(paymentResponse.getPaymentId());
            order.setPaymentStatus(PaymentStatus.COMPLETED);
            order.setStatus(OrderStatus.PAID);
            order.setPaidAt(LocalDateTime.now());
            orderRepository.save(order);

            addStatusHistory(order, OrderStatus.PAID,
                "Оплата успешна, ID платежа: " + paymentResponse.getPaymentId());

            // Отправка подтверждения
            notificationService.sendOrderConfirmation(order);

            log.info("Order {} paid successfully, fulfillment will start automatically", orderNumber);

        } else if ("FAILED".equals(paymentResponse.getStatus())) {
            order.setPaymentStatus(PaymentStatus.FAILED);
            order.setStatus(OrderStatus.CANCELLED);
            order.setCancelledAt(LocalDateTime.now());
            orderRepository.save(order);

            addStatusHistory(order, OrderStatus.CANCELLED,
                "Оплата не удалась: " + paymentResponse.getMessage());
        }

        return mapToResponse(order);
    }

    @Transactional
    public void updateOrderStatus(String orderNumber, OrderStatus newStatus, String description) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new RuntimeException("Order not found: " + orderNumber));

        OrderStatus oldStatus = order.getStatus();
        order.setStatus(newStatus);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        addStatusHistory(order, newStatus, description);

        log.info("Order {} status changed: {} -> {} ({})",
            orderNumber, oldStatus, newStatus, description);
    }

    @Transactional
    public void updateTracking(String orderNumber, String trackingNumber) {
        log.info("Updating tracking for order: {} with tracking: {}", orderNumber, trackingNumber);

        Order order = orderRepository.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new RuntimeException("Order not found: " + orderNumber));

        order.setTrackingNumber(trackingNumber);
        order.setStatus(OrderStatus.SHIPPED);
        order.setShippedAt(LocalDateTime.now());
        orderRepository.save(order);

        addStatusHistory(order, OrderStatus.SHIPPED,
            "Трек-номер сгенерирован: " + trackingNumber);

        notificationService.sendTrackingInfo(order);
    }

    @Transactional
    public void completeDelivery(String orderNumber) {
        log.info("Completing delivery for order: {}", orderNumber);

        Order order = orderRepository.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new RuntimeException("Order not found: " + orderNumber));

        order.setStatus(OrderStatus.COMPLETED);
        order.setDeliveredAt(LocalDateTime.now());
        orderRepository.save(order);

        addStatusHistory(order, OrderStatus.COMPLETED,
            "Заказ получен покупателем");
    }

    @Transactional
    public void cancelUnpaidOrder(String orderNumber) {
        log.info("Cancelling unpaid order: {}", orderNumber);

        Order order = orderRepository.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new RuntimeException("Order not found: " + orderNumber));

        if (order.getPaymentStatus() == PaymentStatus.PENDING ||
            order.getPaymentStatus() == PaymentStatus.PROCESSING) {
            order.setPaymentStatus(PaymentStatus.TIMEOUT);
            order.setStatus(OrderStatus.CANCELLED);
            order.setCancelledAt(LocalDateTime.now());
            orderRepository.save(order);

            addStatusHistory(order, OrderStatus.CANCELLED,
                "Заказ отменен по таймауту оплаты (30 минут)");

            notificationService.sendOrderCancelledNotification(order);
        }
    }

    private void addStatusHistory(Order order, OrderStatus status, String description) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(order);
        history.setStatus(status);
        history.setChangedAt(LocalDateTime.now());
        history.setDescription(description);
        historyRepository.save(history);
    }

    public OrderResponse getOrder(String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new RuntimeException("Order not found: " + orderNumber));
        return mapToResponse(order);
    }

    public OrderResponse getOrderForUser(String orderNumber, Long userId) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new RuntimeException("Order not found: " + orderNumber));
        
        if (order.getUser() != null && !order.getUser().getId().equals(userId)) {
            throw new RuntimeException("Access denied: This order belongs to another user");
        }
        
        return mapToResponse(order);
    }

    public List<OrderResponse> getCustomerOrders(Long customerId) {
        return orderRepository.findByCustomerId(customerId).stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    private String generateOrderNumber() {
        return "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String getPaymentMethodName(String method) {
        switch (method) {
            case "card": return "Банковская карта";
            case "sbp": return "СБП";
            case "installments": return "Рассрочка";
            default: return method;
        }
    }

    private OrderResponse mapToResponse(Order order) {
        OrderResponse response = new OrderResponse();
        response.setId(order.getId());
        response.setOrderNumber(order.getOrderNumber());
        response.setStatus(order.getStatus());
        response.setCreatedAt(order.getCreatedAt());
        response.setTotal(order.getTotal());
        response.setDeliveryType(order.getDeliveryType());
        response.setDeliveryAddress(order.getDeliveryAddress());
        response.setPickupPointAddress(order.getPickupPointAddress());
        response.setPaymentMethod(order.getPaymentMethod());
        response.setPaymentStatus(order.getPaymentStatus());
        response.setTrackingNumber(order.getTrackingNumber());

        List<CartItemDTO> items = order.getItems().stream()
            .map(item -> new CartItemDTO(
                item.getProductId(),
                item.getProductName(),
                item.getQuantity(),
                item.getPrice()
            ))
            .collect(Collectors.toList());
        response.setItems(items);

        // Загружаем историю статусов
        List<OrderStatusHistory> history = historyRepository.findByOrderOrderByChangedAtDesc(order);
        List<OrderResponse.StatusHistoryDTO> historyDTOs = history.stream()
            .map(h -> new OrderResponse.StatusHistoryDTO(
                h.getStatus(),
                h.getChangedAt(),
                h.getDescription()
            ))
            .collect(Collectors.toList());
        response.setStatusHistory(historyDTOs);

        return response;
    }
}
