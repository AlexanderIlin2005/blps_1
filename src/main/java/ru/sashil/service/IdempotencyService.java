package ru.sashil.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final String PREFIX = "idempotency:";

    public void checkAndRegister(String key) {
        if (key == null || key.trim().isEmpty()) {
            return;
        }

        String fullKey = PREFIX + key;
        Boolean isAbsent = redisTemplate.opsForValue().setIfAbsent(fullKey, "PROCESSING", Duration.ofMinutes(10));

        if (Boolean.TRUE.equals(isAbsent)) {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == STATUS_COMMITTED) {
                            redisTemplate.opsForValue().set(fullKey, "COMPLETED", Duration.ofHours(24));
                            log.info("Idempotency key {} marked as COMPLETED", key);
                        } else {
                            redisTemplate.delete(fullKey);
                            log.info("Idempotency key {} removed due to rollback", key);
                        }
                    }
                });
            } else {
                log.warn("Transaction synchronization is not active. Idempotency state might not be updated.");
            }
        } else {
            String status = (String) redisTemplate.opsForValue().get(fullKey);
            throw new RuntimeException("Duplicate request. Idempotency key " + key + " is currently: " + status);
        }
    }
}
