package ru.sashil.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.sashil.dto.CreateOrderRequest;
import ru.sashil.dto.OrderResponse;
import ru.sashil.dto.PaymentRequest;
import ru.sashil.model.User;
import ru.sashil.service.OrderService;
import ru.sashil.service.UserService;

import java.util.List;

@Controller
@RequestMapping("/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;
    private final UserService userService;

    @GetMapping
    public String getUserOrders(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = userService.findByUsername(auth.getName());

        List<OrderResponse> orders = orderService.getCustomerOrders(user.getId());
        model.addAttribute("orders", orders);
        return "orders";
    }

    @GetMapping("/{orderNumber}")
    public String getOrder(@PathVariable String orderNumber, Model model) {
        OrderResponse order = orderService.getOrder(orderNumber);
        model.addAttribute("order", order);
        return "order-details";
    }

    @PostMapping("/create")
    public String createOrder(@ModelAttribute CreateOrderRequest request, Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = userService.findByUsername(auth.getName());

        request.setCustomerId(user.getId());
        request.setCustomerName(user.getFullName());
        request.setCustomerEmail(user.getEmail());
        request.setCustomerPhone(user.getPhone());

        OrderResponse response = orderService.createOrder(request);
        return "redirect:/orders/" + response.getOrderNumber();
    }

    @PostMapping("/{orderNumber}/payment")
    public String processPayment(
            @PathVariable String orderNumber,
            @ModelAttribute PaymentRequest paymentRequest,
            Model model) {
        OrderResponse response = orderService.processPayment(
            orderNumber,
            paymentRequest.getPaymentMethod(),
            paymentRequest.getCardNumber()
        );
        return "redirect:/orders/" + orderNumber;
    }

    // REST API для совместимости
    @RestController
    @RequestMapping("/api/orders")
    @RequiredArgsConstructor
    public static class OrderApiController {
        private final OrderService orderService;

        @GetMapping("/{orderNumber}")
        public ResponseEntity<OrderResponse> getOrder(@PathVariable String orderNumber) {
            return ResponseEntity.ok(orderService.getOrder(orderNumber));
        }

        @GetMapping("/customer/{customerId}")
        public ResponseEntity<List<OrderResponse>> getCustomerOrders(@PathVariable Long customerId) {
            return ResponseEntity.ok(orderService.getCustomerOrders(customerId));
        }
    }
}
