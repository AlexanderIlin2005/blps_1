package ru.sashil.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class TransactionTest {

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    void testCommitSync() {
        String key = "test-commit-" + System.currentTimeMillis();
        transactionTemplate.execute(status -> {
            idempotencyService.checkAndRegister(key);
            return null;
        });

        // После коммита статус должен быть COMPLETED
        String status = (String) redisTemplate.opsForValue().get("idempotency:" + key);
        assertEquals("COMPLETED", status, "Статус в Redis должен быть COMPLETED после коммита");
        System.out.println("SUCCESS: Transaction committed, Redis updated to COMPLETED");
    }

    @Test
    void testRollbackSync() {
        String key = "test-rollback-" + System.currentTimeMillis();
        try {
            transactionTemplate.execute(status -> {
                idempotencyService.checkAndRegister(key);
                throw new RuntimeException("Simulated failure");
            });
        } catch (Exception e) {
            // expected
        }

        // После отката ключ должен быть удален
        Object status = redisTemplate.opsForValue().get("idempotency:" + key);
        assertNull(status, "Ключ в Redis должен быть удален после отката");
        System.out.println("SUCCESS: Transaction rolled back, Redis key deleted");
    }
}
