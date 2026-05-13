package ru.sashil.rules;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class RuleVersionService {
    private final RedisTemplate<String, Object> redisTemplate;
    private static final String VERSION_KEY = "rules:active_version";

    @Value("${rules.active.version:v1}")
    private String defaultVersion;

    public RuleVersionService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String getActiveVersion() {
        Object val = redisTemplate.opsForValue().get(VERSION_KEY);
        if (val != null) return val.toString();
        return defaultVersion;
    }

    public List<String> getAvailableVersions() {
        return List.of("v1", "v2");
    }

    public void setActiveVersion(String version) {
        redisTemplate.opsForValue().set(VERSION_KEY, version);
    }
}
