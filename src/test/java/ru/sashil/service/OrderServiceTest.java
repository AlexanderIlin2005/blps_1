package ru.sashil.service;

import jakarta.transaction.UserTransaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.sashil.dto.CartItemDTO;
import ru.sashil.dto.CreateOrderRequest;
import ru.sashil.dto.OrderResponse;
import ru.sashil.model.DeliveryType;
import ru.sashil.model.Order;
import ru.sashil.model.Product;
import ru.sashil.model.User;
import ru.sashil.repository.OrderRepository;
import ru.sashil.repository.OrderStatusHistoryRepository;
import ru.sashil.repository.ProductRepository;
import ru.sashil.repository.UserRepository;

import java.util.Collections;
import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private OrderStatusHistoryRepository historyRepository;
    @Mock
    private UserTransaction userTransaction;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private OrderService orderService;

    @Test
    void createOrder_shouldSucceedWhenStockAvailable() throws Exception {
        // Arrange
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerId(1L);
        request.setCustomerName("Ivan Ivanov");
        request.setItems(Collections.singletonList(new CartItemDTO("IPHONE-14", "iPhone 14", 1, 99000.0)));
        request.setDeliveryType(DeliveryType.COURIER);

        User user = new User();
        user.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        Product product = new Product();
        product.setSku("IPHONE-14");
        product.setStockQuantity(10);
        product.setPrice(99000.0);
        when(productRepository.findBySku("IPHONE-14")).thenReturn(Optional.of(product));

        Order savedOrder = new Order();
        savedOrder.setOrderNumber("ORD-123");
        savedOrder.setItems(Collections.emptyList());
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        // Act
        OrderResponse response = orderService.createOrder(request);

        // Assert (using Google Truth)
        assertThat(response.getOrderNumber()).isEqualTo("ORD-123");
        verify(userTransaction).begin();
        verify(userTransaction).commit();
        verify(productRepository).save(argThat(p -> p.getStockQuantity() == 9));
    }

    @Test
    void createOrder_shouldFailWhenStockInsufficient() throws Exception {
        // Arrange
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerId(1L);
        request.setItems(Collections.singletonList(new CartItemDTO("IPHONE-14", "iPhone 14", 100, 99000.0)));

        User user = new User();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        Product product = new Product();
        product.setSku("IPHONE-14");
        product.setStockQuantity(10);
        when(productRepository.findBySku("IPHONE-14")).thenReturn(Optional.of(product));

        // Act & Assert
        try {
            orderService.createOrder(request);
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).contains("Not enough stock");
        }
        
        verify(userTransaction).begin();
        verify(userTransaction).rollback();
    }
}
