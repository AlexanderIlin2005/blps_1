package ru.sashil.rules;

import ru.sashil.dto.CreateOrderRequest;
import ru.sashil.model.User;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.HashMap;
import java.util.Map;

public class RuleContext {
    private CreateOrderRequest orderRequest;
    private User user;
    private RedisTemplate<String, Object> redisTemplate;
    private Map<String, Object> facts = new HashMap<>();
    private boolean blocked = false;
    private String blockReason;
    private double discountPercent = 0.0;
    private boolean freeShipping = false;

    public RuleContext(CreateOrderRequest request, User user, RedisTemplate<String, Object> redis) {
        this.orderRequest = request;
        this.user = user;
        this.redisTemplate = redis;
    }

    public CreateOrderRequest getOrderRequest() { return orderRequest; }
    public User getUser() { return user; }
    public RedisTemplate<String, Object> getRedisTemplate() { return redisTemplate; }

    public Map<String, Object> getFacts() { return facts; }
    public void setFact(String key, Object value) { facts.put(key, value); }
    public Object getFact(String key) { return facts.get(key); }

    public boolean isBlocked() { return blocked; }
    public void setBlocked(boolean blocked) { this.blocked = blocked; }
    public String getBlockReason() { return blockReason; }
    public void setBlockReason(String blockReason) { this.blockReason = blockReason; }

    public double getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(double discountPercent) { this.discountPercent = discountPercent; }

    public boolean isFreeShipping() { return freeShipping; }
    public void setFreeShipping(boolean freeShipping) { this.freeShipping = freeShipping; }
}
