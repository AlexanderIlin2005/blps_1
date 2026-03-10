package ru.sashil.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import ru.sashil.model.User;
import ru.sashil.service.UserService;

@Controller
@RequiredArgsConstructor
@Slf4j
public class WebController {

    private final UserService userService;

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/catalog")
    public String catalog() {
        return "catalog";
    }

    @GetMapping("/checkout")
    public String checkout(Model model) {
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            try {
                User user = userService.findByUsername(auth.getName());
                model.addAttribute("user", user);
                log.info("User data loaded for checkout: {}", user.getUsername());
            } catch (Exception e) {
                log.error("Error loading user data: {}", e.getMessage());
            }
        }
        return "checkout";
    }

    @GetMapping("/payment-result")
    public String paymentResult(@org.springframework.web.bind.annotation.RequestParam(required = false) String orderNumber, 
                                @org.springframework.web.bind.annotation.RequestParam(required = false) String flow, 
                                Model model) {
        model.addAttribute("orderNumber", orderNumber);
        model.addAttribute("flow", flow);
        
        if (orderNumber != null) {
            try {
                // Пытаемся найти заказ, чтобы проверить наличие ссылки на оплату
                ru.sashil.repository.OrderRepository orderRepository = ((ru.sashil.repository.OrderRepository) 
                    org.springframework.web.context.support.WebApplicationContextUtils
                    .getRequiredWebApplicationContext(
                        ((org.springframework.web.context.request.ServletRequestAttributes) 
                        org.springframework.web.context.request.RequestContextHolder.getRequestAttributes())
                        .getRequest().getServletContext()
                    ).getBean(ru.sashil.repository.OrderRepository.class));
                
                orderRepository.findByOrderNumber(orderNumber).ifPresent(order -> model.addAttribute("order", order));
            } catch (Exception e) {
                log.warn("Could not load order for result page: {}", e.getMessage());
            }
        }
        
        return "payment-result";
    }
}
