package ru.sashil.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.sashil.dto.CartItemDTO;
import ru.sashil.dto.CreateOrderRequest;
import ru.sashil.dto.OrderResponse;
import ru.sashil.dto.PaymentRequest;
import ru.sashil.model.User;
import ru.sashil.service.OrderService;
import ru.sashil.service.UserService;

import java.util.ArrayList;
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
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = userService.findByUsername(auth.getName());
        
        OrderResponse order = orderService.getOrderForUser(orderNumber, user.getId());
        model.addAttribute("order", order);
        return "order-details";
    }

    @PostMapping("/create")
    public String createOrder(
            @RequestParam("customerName") String customerName,
            @RequestParam("customerEmail") String customerEmail,
            @RequestParam("customerPhone") String customerPhone,
            @RequestParam("deliveryType") String deliveryType,
            @RequestParam(value = "deliveryAddress", required = false) String deliveryAddress,
            @RequestParam(value = "pickupPointId", required = false) String pickupPointId,
            @RequestParam("paymentMethod") String paymentMethod,
            @RequestParam(value = "cardNumber", required = false) String cardNumber,
            @RequestParam(value = "cardExpiry", required = false) String cardExpiry,
            @RequestParam(value = "cardCvv", required = false) String cardCvv,
            @RequestParam(value = "phoneNumber", required = false) String phoneNumber,
            @RequestParam(value = "items[0].productId", required = false) List<String> productIds,
            @RequestParam(value = "items[0].productName", required = false) List<String> productNames,
            @RequestParam(value = "items[0].quantity", required = false) List<Integer> quantities,
            @RequestParam(value = "items[0].price", required = false) List<Double> prices,
            Model model) {

        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User user = userService.findByUsername(auth.getName());

            log.info("Creating order for user: {} with {} items, payment method: {}",
                user.getUsername(), productIds != null ? productIds.size() : 0, paymentMethod);

            // Создаем список товаров
            List<CartItemDTO> items = new ArrayList<>();
            if (productIds != null && !productIds.isEmpty()) {
                for (int i = 0; i < productIds.size(); i++) {
                    CartItemDTO item = new CartItemDTO();
                    item.setProductId(productIds.get(i));
                    item.setProductName(productNames.get(i));
                    item.setQuantity(quantities.get(i));
                    item.setPrice(prices.get(i));
                    items.add(item);
                }
            }

            // Создаем запрос
            CreateOrderRequest request = new CreateOrderRequest();
            request.setCustomerId(user.getId());
            request.setCustomerName(customerName);
            request.setCustomerEmail(customerEmail);
            request.setCustomerPhone(customerPhone);
            request.setItems(items);
            request.setDeliveryType(deliveryType.equals("COURIER") ?
                ru.sashil.model.DeliveryType.COURIER : ru.sashil.model.DeliveryType.PICKUP);

            if (deliveryType.equals("COURIER")) {
                request.setDeliveryAddress(deliveryAddress);
            } else {
                request.setPickupPointId(pickupPointId);
                // Здесь можно добавить получение адреса ПВЗ по ID
                if ("p1".equals(pickupPointId)) {
                    request.setPickupPointAddress("ул. Тверская, 7");
                } else if ("p2".equals(pickupPointId)) {
                    request.setPickupPointAddress("пр. Мира, 26");
                } else if ("p3".equals(pickupPointId)) {
                    request.setPickupPointAddress("Ленинский пр., 68");
                }
            }

            // Создаем заказ
            OrderResponse response = orderService.createOrder(request);
            log.info("Order created successfully: {}", response.getOrderNumber());

            // Сразу обрабатываем оплату, если выбран метод оплаты
            if (paymentMethod != null && !paymentMethod.isEmpty()) {
                log.info("Processing payment for order {} with method {}", response.getOrderNumber(), paymentMethod);

                String paymentDetails = "";
                if ("card".equals(paymentMethod)) {
                    paymentDetails = cardNumber + "|" + cardExpiry + "|" + cardCvv;
                } else if ("sbp".equals(paymentMethod)) {
                    paymentDetails = phoneNumber;
                }

                OrderResponse paidOrder = orderService.processPayment(
                    response.getOrderNumber(),
                    paymentMethod,
                    paymentDetails
                );

                log.info("Payment processed, status: {}", paidOrder.getPaymentStatus());
            }

            // Добавляем атрибут для очистки корзины на клиенте
            model.addAttribute("clearCart", true);

            return "redirect:/orders/" + response.getOrderNumber() + "?success=true";

        } catch (Exception e) {
            log.error("Error creating order: {}", e.getMessage(), e);
            model.addAttribute("error", "Ошибка при создании заказа: " + e.getMessage());
            return "redirect:/checkout?error=true";
        }
    }

    @PostMapping("/{orderNumber}/payment")
    public String processPayment(
            @PathVariable String orderNumber,
            @RequestParam("paymentMethod") String paymentMethod,
            @RequestParam(value = "cardNumber", required = false) String cardNumber,
            @RequestParam(value = "cardExpiry", required = false) String cardExpiry,
            @RequestParam(value = "cardCvv", required = false) String cardCvv,
            @RequestParam(value = "phoneNumber", required = false) String phoneNumber,
            Model model) {

        try {
            log.info("Processing payment for order {} with method {}", orderNumber, paymentMethod);

            String paymentDetails = "";
            if ("card".equals(paymentMethod)) {
                paymentDetails = cardNumber + "|" + cardExpiry + "|" + cardCvv;
            } else if ("sbp".equals(paymentMethod)) {
                paymentDetails = phoneNumber;
            }

            OrderResponse response = orderService.processPayment(
                orderNumber,
                paymentMethod,
                paymentDetails
            );

            return "redirect:/orders/" + orderNumber + "?payment=success";

        } catch (Exception e) {
            log.error("Error processing payment: {}", e.getMessage());
            return "redirect:/orders/" + orderNumber + "?payment=failed";
        }
    }

    // REST API для совместимости
    @RestController
    @RequestMapping("/api/orders")
    @RequiredArgsConstructor
    public static class OrderApiController {
        private final OrderService orderService;
        private final UserService userService;

        @GetMapping("/{orderNumber}")
        public ResponseEntity<OrderResponse> getOrder(@PathVariable String orderNumber, Authentication auth) {
            User user = userService.findByUsername(auth.getName());
            return ResponseEntity.ok(orderService.getOrderForUser(orderNumber, user.getId()));
        }

        @GetMapping("/customer/{customerId}")
        public ResponseEntity<List<OrderResponse>> getCustomerOrders(@PathVariable Long customerId, Authentication auth) {
            User user = userService.findByUsername(auth.getName());
            if (!user.getId().equals(customerId)) {
                return ResponseEntity.status(403).build();
            }
            return ResponseEntity.ok(orderService.getCustomerOrders(customerId));
        }
    }
}
