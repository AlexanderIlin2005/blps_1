package ru.sashil.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import ru.sashil.dto.UserDTO;
import ru.sashil.dto.UserRegistrationDTO;
import ru.sashil.model.Role;
import ru.sashil.model.User;
import ru.sashil.repository.RoleRepository;
import ru.sashil.repository.UserRepository;

import java.util.Collections;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    private TransactionTemplate transactionTemplate;

    public UserDTO register(UserRegistrationDTO registrationDTO) {
        log.info("Registering new user: {}", registrationDTO.getUsername());

        return transactionTemplate.execute(status -> {
            if (userRepository.existsByUsername(registrationDTO.getUsername())) {
                throw new RuntimeException("Username already exists");
            }
            if (userRepository.existsByEmail(registrationDTO.getEmail())) {
                throw new RuntimeException("Email already exists");
            }

            if (!registrationDTO.getPassword().equals(registrationDTO.getConfirmPassword())) {
                throw new RuntimeException("Passwords do not match");
            }

            User user = new User();
            user.setUsername(registrationDTO.getUsername());

            String encodedPassword = passwordEncoder.encode(registrationDTO.getPassword());
            user.setPassword(encodedPassword);

            user.setEmail(registrationDTO.getEmail());
            user.setFullName(registrationDTO.getFullName());
            user.setPhone(registrationDTO.getPhone());
            
            Role defaultRole = roleRepository.findByName("ROLE_CUSTOMER")
                    .orElseThrow(() -> new RuntimeException("Default role not found"));
            user.setRoles(Collections.singletonList(defaultRole));

            User savedUser = userRepository.save(user);
            log.info("User registered successfully: {}", savedUser.getUsername());

            return mapToDTO(savedUser);
        });
    }

    public User findByUsername(String username) {
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
        
        String rolesStr = user.getRoles() != null ? 
                user.getRoles().stream().map(Role::getName).collect(Collectors.joining(",")) : "";
        dto.setRole(rolesStr);
        
        dto.setCreatedAt(user.getCreatedAt());
        return dto;
    }
}
