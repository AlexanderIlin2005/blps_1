package ru.sashil.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebController {

    @GetMapping("/")
    public String index() {
        return "index";  // Возвращает имя шаблона (index.html)
    }

    @GetMapping("/catalog")
    public String catalog() {
        return "catalog";  // Возвращает имя шаблона (catalog.html)
    }

    @GetMapping("/checkout")
    public String checkout() {
        return "checkout";  // Возвращает имя шаблона (checkout.html)
    }

    @GetMapping("/order/{orderNumber}")
    public String orderDetails() {
        return "order-details";  // Возвращает имя шаблона (order-details.html)
    }
}
