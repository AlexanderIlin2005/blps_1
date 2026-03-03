package ru.sashil.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sashil.dto.UserDTO;
import ru.sashil.dto.UserRegistrationDTO;
import ru.sashil.model.User;
import ru.sashil.repository.UserRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserDTO register(UserRegistrationDTO registrationDTO) {
        log.info("Registering new user: {}", registrationDTO.getUsername());

        // Проверка существования
        if (userRepository.existsByUsername(registrationDTO.getUsername())) {
            throw new RuntimeException("Username already exists");
        }
        if (userRepository.existsByEmail(registrationDTO.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        // Проверка паролей
        if (!registrationDTO.getPassword().equals(registrationDTO.getConfirmPassword())) {
            throw new RuntimeException("Passwords do not match");
        }

        // Создание пользователя
        User user = new User();
        user.setUsername(registrationDTO.getUsername());

        String encodedPassword = passwordEncoder.encode(registrationDTO.getPassword());
        log.debug("Password encoded: {}", encodedPassword);
        user.setPassword(encodedPassword);

        user.setEmail(registrationDTO.getEmail());
        user.setFullName(registrationDTO.getFullName());
        user.setPhone(registrationDTO.getPhone());
        user.setRole("USER");

        User savedUser = userRepository.save(user);
        log.info("User registered successfully: {}", savedUser.getUsername());

        return mapToDTO(savedUser);
    }

    public User findByUsername(String username) {
        log.debug("Finding user by username: {}", username);
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("User not found: " + username));
    }

    public UserDTO getUserDTO(Long id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("User not found"));
        return mapToDTO(user);
    }

    private UserDTO mapToDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setFullName(user.getFullName());
        dto.setPhone(user.getPhone());
        dto.setRole(user.getRole());
        dto.setCreatedAt(user.getCreatedAt());
        return dto;
    }
}
