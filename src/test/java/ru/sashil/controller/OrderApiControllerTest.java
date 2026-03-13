package ru.sashil.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import ru.sashil.config.SecurityConfig;
import ru.sashil.model.User;
import ru.sashil.service.OrderService;
import ru.sashil.service.UserService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.OrderApiController.class)
@Import(SecurityConfig.class)
class OrderApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @MockBean
    private UserService userService;

    @Test
    @WithMockUser(authorities = {"READ_OWN_ORDERS"})
    void getCustomerOrders_withPermission_shouldReturnOk() throws Exception {
        User user = new User();
        user.setId(1L);
        when(userService.findByUsername(any())).thenReturn(user);

        mockMvc.perform(get("/api/orders/customer"))
                .andExpect(status().isOk());
    }

    @Test
    void getCustomerOrders_unauthenticated_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/orders/customer"))
                .andExpect(status().isUnauthorized());
    }
}
