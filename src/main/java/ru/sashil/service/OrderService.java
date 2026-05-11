package ru.sashil.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;
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
import ru.sashil.rules.RuleContext;
import ru.sashil.rules.RuleEngine;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
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
    private final InventoryService inventoryService;
    private final RuleEngine ruleEngine;
    private final RedisTemplate<String, Object> redisTemplate;

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

            // Increment order count for antifraud in Redis
            String antifraudKey = "antifraud:user:" + user.getId() + ":orderCount";
            redisTemplate.opsForValue().increment(antifraudKey, 1);
            redisTemplate.expire(antifraudKey, Duration.ofHours(1));

            // Create rule context and evaluate business rules
            RuleContext ctx = new RuleContext(request, user, redisTemplate);
            ruleEngine.evaluate(ctx);

            if (ctx.isBlocked()) {
                log.warn("Order creation blocked: {}", ctx.getBlockReason());
                throw new RuntimeException(ctx.getBlockReason());
            }

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

            // Apply discount from rules
            double discountAmount = 0;
            List<String> appliedRules = new ArrayList<>();
            if (ctx.getDiscountPercent() > 0) {
                discountAmount = subtotal * ctx.getDiscountPercent() / 100.0;
                appliedRules.add("Volume Discount " + ctx.getDiscountPercent() + "%");
            }
            if (ctx.isFreeShipping()) {
                // free shipping not directly impacting total, but can be stored for display
                appliedRules.add("Free Shipping Applied");
            }

            double total = subtotal - discountAmount;

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
            order.setDiscount(discountAmount);
            order.setTotal(total);
            order.setPaymentStatus(PaymentStatus.PENDING);

            // Store applied rules in metadata? We'll store in order response via mapToResponse
            // For simplicity, we set discount amount.

            Order savedOrder = orderRepository.save(order);
            addStatusHistory(savedOrder, OrderStatus.CHECKOUT, "Order created");

            log.info("Order created with number: {}, discount: {}", savedOrder.getOrderNumber(), discountAmount);
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
                "Payment processing via method: " + getPaymentMethodName(paymentMethod));
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
                // Evaluate post-payment rules (if any)
                User user = o.getUser();
                CreateOrderRequest fakeReq = new CreateOrderRequest();
                fakeReq.setCustomerId(user.getId());
                fakeReq.setItems(o.getItems().stream().map(i -> new CartItemDTO(i.getProductId(), i.getProductName(), i.getQuantity(), i.getPrice())).collect(Collectors.toList()));
                RuleContext ctx = new RuleContext(fakeReq, user, redisTemplate);
                ruleEngine.evaluate(ctx);
                // Post-payment discount would not be applied again; just for logging or additional actions

                finalizePayment(o, paymentResponse.getPaymentId(), true, "Payment successful");
            } else if ("PENDING".equals(paymentResponse.getStatus())) {
                o.setPaymentId(paymentResponse.getPaymentId());
                orderRepository.save(o);
                addStatusHistory(o, OrderStatus.PAYMENT_PROCESSING,
                    "Payment request sent to queue (Kassa). Waiting for confirmation.");
            } else if ("FAILED".equals(paymentResponse.getStatus())) {
                finalizePayment(o, null, false, "Payment failed: " + paymentResponse.getMessage());
            }
            return mapToResponse(o);
        });
    }

    public void finalizePayment(Order order, String paymentId, boolean success, String message) {
        transactionTemplate.execute(status -> {
            Order o = orderRepository.findById(order.getId()).get();

            if (success) {
                o.setPaymentId(paymentId);
                o.setPaymentStatus(PaymentStatus.COMPLETED);
                o.setStatus(OrderStatus.PAID);
                o.setPaidAt(LocalDateTime.now());
                orderRepository.save(o);
                addStatusHistory(o, OrderStatus.PAID, message);
                notificationService.sendOrderConfirmation(o);

                log.info("Triggering fulfillment via queue for order: {}", o.getOrderNumber());
                inventoryService.processFulfillment(o);
            } else {
                o.setPaymentStatus(PaymentStatus.FAILED);
                o.setStatus(OrderStatus.CANCELLED);
                o.setCancelledAt(LocalDateTime.now());
                orderRepository.save(o);
                addStatusHistory(o, OrderStatus.CANCELLED, message);
            }
            return null;
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

            log.info("Order {} status changed: {} -> {} ({})", orderNumber, oldStatus, newStatus, description);
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

            addStatusHistory(order, OrderStatus.SHIPPED, "Tracking generated: " + trackingNumber);
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

            addStatusHistory(order, OrderStatus.COMPLETED, "Order delivered to customer");
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

                addStatusHistory(order, OrderStatus.CANCELLED, "Cancelled due to payment timeout (30 min)");
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
            if (orderUserId != null && !orderUserId.equals(userId)) {
                throw new RuntimeException("Access denied: order belongs to another user");
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
            case "card": return "Bank card";
            case "sbp": return "SBP";
            case "installments": return "Installments";
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
        response.setDiscountApplied(order.getDiscount());

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
