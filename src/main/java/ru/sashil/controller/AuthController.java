package ru.sashil.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import ru.sashil.dto.UserDTO;
import ru.sashil.dto.UserRegistrationDTO;
import ru.sashil.model.User;
import ru.sashil.service.UserService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final UserService userService;

    @GetMapping("/register")
    public String showRegistrationForm(Model model) {
        model.addAttribute("user", new UserRegistrationDTO());
        return "register";
    }

    @PostMapping("/register")
    public String registerUser(@ModelAttribute("user") UserRegistrationDTO registrationDTO, Model model) {
        try {
            log.info("Attempting to register user: {}", registrationDTO.getUsername());
            UserDTO user = userService.register(registrationDTO);
            log.info("User registered successfully: {}", user.getUsername());
            return "redirect:/login?registered";
        } catch (Exception e) {
            log.error("Registration error: {}", e.getMessage());
            model.addAttribute("error", e.getMessage());
            return "register";
        }
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/profile")
    public String profile(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        log.info("Profile accessed by: {}", auth.getName());

        User user = userService.findByUsername(auth.getName());
        model.addAttribute("user", user);
        return "profile";
    }

    // API для получения данных пользователя
    @RestController
    @RequestMapping("/api/user")
    @RequiredArgsConstructor
    public static class UserApiController {
        private final UserService userService;

        @GetMapping("/me")
        public ResponseEntity<UserDTO> getCurrentUser(Authentication auth) {
            log.info("REST request to get current user: {}", auth.getName());
            User user = userService.findByUsername(auth.getName());
            
            UserDTO dto = new UserDTO();
            dto.setId(user.getId());
            dto.setUsername(user.getUsername());
            dto.setEmail(user.getEmail());
            dto.setFullName(user.getFullName());
            dto.setPhone(user.getPhone());
            dto.setRole(user.getRole());
            dto.setCreatedAt(user.getCreatedAt());
            
            return ResponseEntity.ok(dto);
        }
    }
}
