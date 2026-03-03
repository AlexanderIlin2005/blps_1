package ru.sashil.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sashil.dto.CartItemDTO;
import ru.sashil.dto.CreateOrderRequest;
import ru.sashil.dto.OrderResponse;
import ru.sashil.dto.PaymentResponse;
import ru.sashil.model.*;
import ru.sashil.repository.OrderRepository;
import ru.sashil.repository.ProductRepository;

import java.time.LocalDateTime;
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
    private final PaymentService paymentService;
    private final NotificationService notificationService;

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        log.info("Creating new order for customer: {}", request.getCustomerName());

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
            OrderItem orderItem = new OrderItem();
            orderItem.setProductId(item.getProductId());
            orderItem.setProductName(item.getProductName());
            orderItem.setQuantity(item.getQuantity());
            orderItem.setPrice(item.getPrice());
            orderItem.setTotal(item.getPrice() * item.getQuantity());
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

            // Отправка подтверждения
            notificationService.sendOrderConfirmation(order);

            log.info("Order {} paid successfully, fulfillment will start automatically", orderNumber);

        } else if ("FAILED".equals(paymentResponse.getStatus())) {
            order.setPaymentStatus(PaymentStatus.FAILED);
            order.setStatus(OrderStatus.CANCELLED);
            order.setCancelledAt(LocalDateTime.now());
            orderRepository.save(order);
        }

        return mapToResponse(order);
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

            notificationService.sendOrderCancelledNotification(order);
        }
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
    }

    public OrderResponse getOrder(String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new RuntimeException("Order not found: " + orderNumber));
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

        return response;
    }
}
