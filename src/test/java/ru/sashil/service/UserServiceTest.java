package ru.sashil.service;

import jakarta.transaction.UserTransaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.sashil.dto.UserDTO;
import ru.sashil.dto.UserRegistrationDTO;
import ru.sashil.model.Role;
import ru.sashil.model.User;
import ru.sashil.repository.RoleRepository;
import ru.sashil.repository.UserRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserTransaction userTransaction;

    @InjectMocks
    private UserService userService;

    @Test
    void register_success() throws Exception {
        // Arrange
        UserRegistrationDTO dto = new UserRegistrationDTO();
        dto.setUsername("testuser");
        dto.setEmail("test@example.com");
        dto.setPassword("password");
        dto.setConfirmPassword("password");

        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password")).thenReturn("encoded_password");
        
        Role role = new Role();
        role.setName("ROLE_CUSTOMER");
        when(roleRepository.findByName("ROLE_CUSTOMER")).thenReturn(Optional.of(role));

        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        when(userRepository.save(any(User.class))).thenReturn(user);

        // Act
        UserDTO result = userService.register(dto);

        // Assert
        assertNotNull(result);
        assertEquals("testuser", result.getUsername());
        verify(userTransaction).begin();
        verify(userTransaction).commit();
    }

    @Test
    void register_passwordsDoNotMatch_shouldThrowException() throws Exception {
        // Arrange
        UserRegistrationDTO dto = new UserRegistrationDTO();
        dto.setUsername("testuser");
        dto.setPassword("password");
        dto.setConfirmPassword("wrong");

        // Act & Assert
        assertThrows(RuntimeException.class, () -> userService.register(dto));
        verify(userTransaction).begin();
        verify(userTransaction).rollback();
    }
}
