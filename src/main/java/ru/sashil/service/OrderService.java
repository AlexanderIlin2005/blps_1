package ru.sashil.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
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
    private final IdempotencyService idempotencyService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private PaymentService paymentService;

    @Autowired
    public void setPaymentService(@Lazy PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PreAuthorize("hasAuthority('CREATE_ORDER')")
    public OrderResponse createOrder(CreateOrderRequest request) {
        log.info("Creating new order for customer: {}", request.getCustomerName());

        return transactionTemplate.execute(status -> {
            idempotencyService.checkAndRegister(request.getIdempotencyKey());

            User user = userRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new RuntimeException("User not found"));

            List<OrderItem> orderItems = new ArrayList<>();
            double subtotal = 0;

            for (CartItemDTO item : request.getItems()) {
                Product product = productRepository.findBySku(item.getProductId())
                    .orElseThrow(() -> new RuntimeException("Product not found: " + item.getProductId()));

                if (product.getStockQuantity() < item.getQuantity()) {
                    throw new RuntimeException("Not enough stock for product: " + product.getName());
                }
                

                product.setStockQuantity(product.getStockQuantity() - item.getQuantity());
                productRepository.save(product);

                OrderItem orderItem = new OrderItem();
                orderItem.setProductId(item.getProductId());
                orderItem.setProductName(product.getName()); 
                orderItem.setQuantity(item.getQuantity());
                orderItem.setPrice(product.getPrice()); 
                orderItem.setTotal(product.getPrice() * item.getQuantity());
                
                orderItems.add(orderItem);
                subtotal += orderItem.getTotal();
            }

            Order order = new Order();
            order.setOrderNumber(generateOrderNumber());
            order.setUser(user); 
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

            order.setItems(orderItems);
            for(OrderItem item : orderItems) {
                item.setOrder(order);
            }
            
            order.setSubtotal(subtotal);
            order.setDiscount(0.0);
            order.setTotal(subtotal);
            order.setPaymentStatus(PaymentStatus.PENDING);

            Order savedOrder = orderRepository.save(order);
            addStatusHistory(savedOrder, OrderStatus.CHECKOUT, "Заказ создан");

            log.info("Order created with number: {}", savedOrder.getOrderNumber());
            return mapToResponse(savedOrder);
        });
    }

    public OrderResponse processPayment(String orderNumber, String paymentMethod, String paymentDetails) {
        log.info("Processing payment for order: {}", orderNumber);

        Order order = transactionTemplate.execute(status -> {
            Order o = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderNumber));

            o.setPaymentMethod(paymentMethod);
            o.setPaymentStatus(PaymentStatus.PROCESSING);
            o.setStatus(OrderStatus.PAYMENT_PROCESSING);
            orderRepository.save(o);

            addStatusHistory(o, OrderStatus.PAYMENT_PROCESSING,
                "Обработка платежа методом: " + getPaymentMethodName(paymentMethod));
            return o;
        });


        PaymentResponse paymentResponse = paymentService.processPayment(
            orderNumber,
            paymentMethod,
            order.getTotal(),
            paymentDetails
        );

        return transactionTemplate.execute(status -> {
            Order o = orderRepository.findByOrderNumber(orderNumber).get();

            if ("SUCCESS".equals(paymentResponse.getStatus())) {
                o.setPaymentId(paymentResponse.getPaymentId());
                o.setPaymentStatus(PaymentStatus.COMPLETED);
                o.setStatus(OrderStatus.PAID);
                o.setPaidAt(LocalDateTime.now());
                orderRepository.save(o);

                addStatusHistory(o, OrderStatus.PAID,
                    "Оплата успешна, ID платежа: " + paymentResponse.getPaymentId());

                notificationService.sendOrderConfirmation(o);
                log.info("Order {} paid successfully", orderNumber);

            } else if ("PENDING".equals(paymentResponse.getStatus())) {
                o.setPaymentId(paymentResponse.getPaymentId());
                orderRepository.save(o);
                addStatusHistory(o, OrderStatus.PAYMENT_PROCESSING,
                    "Запрос на оплату отправлен в очередь (ЮKassa). Ожидайте подтверждения.");
            } else if ("FAILED".equals(paymentResponse.getStatus())) {
                o.setPaymentStatus(PaymentStatus.FAILED);
                o.setStatus(OrderStatus.CANCELLED);
                o.setCancelledAt(LocalDateTime.now());
                orderRepository.save(o);

                addStatusHistory(o, OrderStatus.CANCELLED,
                    "Оплата не удалась: " + paymentResponse.getMessage());
            }
            return mapToResponse(o);
        });
    }

    public void updateOrderStatus(String orderNumber, OrderStatus newStatus, String description) {
        transactionTemplate.execute(status -> {
            Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderNumber));

            OrderStatus oldStatus = order.getStatus();
            order.setStatus(newStatus);
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);

            addStatusHistory(order, newStatus, description);

            log.info("Order {} status changed: {} -> {} ({})",
                orderNumber, oldStatus, newStatus, description);
            return null;
        });
    }

    public void updateTracking(String orderNumber, String trackingNumber) {
        transactionTemplate.execute(status -> {
            Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderNumber));

            order.setTrackingNumber(trackingNumber);
            order.setStatus(OrderStatus.SHIPPED);
            order.setShippedAt(LocalDateTime.now());
            orderRepository.save(order);

            addStatusHistory(order, OrderStatus.SHIPPED,
                "Трек-номер сгенерирован: " + trackingNumber);

            notificationService.sendTrackingInfo(order);
            return null;
        });
    }

    public void completeDelivery(String orderNumber) {
        transactionTemplate.execute(status -> {
            Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderNumber));

            order.setStatus(OrderStatus.COMPLETED);
            order.setDeliveredAt(LocalDateTime.now());
            orderRepository.save(order);

            addStatusHistory(order, OrderStatus.COMPLETED,
                "Заказ получен покупателем");
            return null;
        });
    }

    public void cancelUnpaidOrder(String orderNumber) {
        transactionTemplate.execute(status -> {
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
            return null;
        });
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
        return transactionTemplate.execute(status -> {
            Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderNumber));
            return mapToResponse(order);
        });
    }

    @PreAuthorize("hasAuthority('READ_OWN_ORDERS')")
    public OrderResponse getOrderForUser(String orderNumber, Long userId) {
        return transactionTemplate.execute(status -> {
            Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderNumber));
            
            Long orderUserId = (order.getUser() != null) ? order.getUser().getId() : null;
            log.info("Checking access for order {}: orderUserId={}, currentUserId={}", 
                orderNumber, orderUserId, userId);

            if (orderUserId != null && !orderUserId.equals(userId)) {
                throw new RuntimeException("Access denied: This order belongs to another user (ID: " + orderUserId + ", yours: " + userId + ")");
            }
            
            return mapToResponse(order);
        });
    }

    @PreAuthorize("hasAuthority('READ_OWN_ORDERS')")
    public List<OrderResponse> getCustomerOrders(Long customerId) {
        return transactionTemplate.execute(status -> {
            return orderRepository.findByCustomerId(customerId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        });
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
        response.setPaymentConfirmationUrl(order.getPaymentConfirmationUrl());
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
