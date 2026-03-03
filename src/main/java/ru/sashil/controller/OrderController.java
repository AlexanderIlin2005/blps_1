package ru.sashil.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.sashil.dto.CreateOrderRequest;
import ru.sashil.dto.OrderResponse;
import ru.sashil.dto.PaymentRequest;
import ru.sashil.dto.PaymentResponse;
import ru.sashil.service.OrderService;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@RequestBody CreateOrderRequest request) {
        log.info("REST request to create order");
        OrderResponse response = orderService.createOrder(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{orderNumber}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable String orderNumber) {
        log.info("REST request to get order: {}", orderNumber);
        OrderResponse response = orderService.getOrder(orderNumber);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<OrderResponse>> getCustomerOrders(@PathVariable Long customerId) {
        log.info("REST request to get orders for customer: {}", customerId);
        List<OrderResponse> responses = orderService.getCustomerOrders(customerId);
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/{orderNumber}/payment")
    public ResponseEntity<OrderResponse> processPayment(
            @PathVariable String orderNumber,
            @RequestBody PaymentRequest paymentRequest) {
        log.info("REST request to process payment for order: {}", orderNumber);
        OrderResponse response = orderService.processPayment(
            orderNumber,
            paymentRequest.getPaymentMethod(),
            paymentRequest.getCardNumber() // упрощенно
        );
        return ResponseEntity.ok(response);
    }
}
