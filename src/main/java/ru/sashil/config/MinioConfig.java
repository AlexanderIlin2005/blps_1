package ru.sashil.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {

    @Bean
    public MinioClient minioClient(
        @Value("${accounting.minio.endpoint:http://127.0.0.1:9000}") String endpoint,
        @Value("${accounting.minio.access-key:minioadmin}") String accessKey,
        @Value("${accounting.minio.secret-key:minioadmin}") String secretKey
    ) {
        return MinioClient.builder()
            .endpoint(endpoint)
            .credentials(accessKey, secretKey)
            .build();
    }
}
