package ru.sashil.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import ru.sashil.dto.PaymentTask;
import ru.sashil.repository.OrderRepository;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class PaymentWorkerTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private OrderService orderService;

    @Test
    void convertsRedisHashPayloadIntoPaymentTask() {
        PaymentWorker paymentWorker = new PaymentWorker(
            redisTemplate,
            orderRepository,
            notificationService,
            orderService,
            new ObjectMapper()
        );

        PaymentTask task = paymentWorker.convertQueueItem(Map.of(
            "orderNumber", "ORD-QUEUE-1",
            "amount", 14990.0,
            "paymentMethod", "yookassa",
            "details", "payload",
            "timestamp", 123456789L
        ));

        assertEquals("ORD-QUEUE-1", task.getOrderNumber());
        assertEquals(14990.0, task.getAmount());
        assertEquals("yookassa", task.getPaymentMethod());
        assertEquals("payload", task.getDetails());
        assertEquals(123456789L, task.getTimestamp());
    }
}
