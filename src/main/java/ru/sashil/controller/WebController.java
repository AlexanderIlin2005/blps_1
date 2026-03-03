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
        // Получаем текущего пользователя
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
}
