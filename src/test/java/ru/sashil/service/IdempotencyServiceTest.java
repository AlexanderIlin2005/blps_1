package ru.sashil.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private IdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.initSynchronization();
        }
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clear();
    }


    @Test
    void testTransactionFlow_HappyPath_CommitsToRedis() {
        String key = "happy-path-key";
        String fullKey = "idempotency:" + key;
        
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        when(valueOperations.setIfAbsent(eq(fullKey), eq("PROCESSING"), any(Duration.class)))
                .thenReturn(true);


        idempotencyService.checkAndRegister(key);


        assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size(), 
            "Должен быть зарегистрирован 1 синхронизатор транзакции");


        TransactionSynchronization sync = TransactionSynchronizationManager.getSynchronizations().get(0);


        sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);


        verify(valueOperations).set(eq(fullKey), eq("COMPLETED"), any(Duration.class));
        verify(redisTemplate, never()).delete(fullKey);
        
        System.out.println("Happy Path Test Passed: Status updated to COMPLETED after commit.");
    }


    @Test
    void testTransactionFlow_Rollback_DeletesFromRedis() {
        String key = "rollback-key";
        String fullKey = "idempotency:" + key;
        
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(fullKey), eq("PROCESSING"), any(Duration.class)))
                .thenReturn(true);


        idempotencyService.checkAndRegister(key);

        TransactionSynchronization sync = TransactionSynchronizationManager.getSynchronizations().get(0);


        sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);


        verify(redisTemplate).delete(fullKey);
        verify(valueOperations, never()).set(eq(fullKey), eq("COMPLETED"), any(Duration.class));
        
        System.out.println("Rollback Test Passed: Key deleted from Redis after rollback.");
    }

    @Test
    void testDuplicateRequest_ThrowsException() {
        String key = "duplicate-key";
        String fullKey = "idempotency:" + key;
        
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        when(valueOperations.setIfAbsent(eq(fullKey), eq("PROCESSING"), any(Duration.class)))
                .thenReturn(false);
        when(valueOperations.get(fullKey)).thenReturn("PROCESSING");


        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> idempotencyService.checkAndRegister(key));
        
        assertTrue(exception.getMessage().contains("Duplicate request"));
        assertTrue(exception.getMessage().contains("PROCESSING"));
        
        System.out.println("Duplicate Test Passed: Correctly blocked concurrent request.");
    }
}
