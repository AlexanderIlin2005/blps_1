package ru.sashil.controller.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.sashil.dto.UserDTO;
import ru.sashil.dto.UserRegistrationDTO;
import ru.sashil.model.Role;
import ru.sashil.model.User;
import ru.sashil.service.UserService;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class UserApiController {
    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<UserDTO> register(@RequestBody UserRegistrationDTO registrationDTO) {
        log.info("REST request to register user: {}", registrationDTO.getUsername());
        return ResponseEntity.ok(userService.register(registrationDTO));
    }

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
        
        String rolesStr = user.getRoles() != null ? 
            user.getRoles().stream().map(Role::getName).collect(Collectors.joining(",")) : "";
        dto.setRole(rolesStr);
        
        dto.setCreatedAt(user.getCreatedAt());
        
        return ResponseEntity.ok(dto);
    }
}
