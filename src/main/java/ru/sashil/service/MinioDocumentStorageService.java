package ru.sashil.service;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class MinioDocumentStorageService {

    private final MinioClient minioClient;

    @Value("${accounting.minio.bucket:accounting-documents}")
    private String bucketName;

    public StoredDocument store(String objectKey, String contentType, byte[] data) {
        try {
            ensureBucketExists();
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectKey)
                    .stream(new ByteArrayInputStream(data), data.length, -1)
                    .contentType(contentType)
                    .build()
            );
            return new StoredDocument(bucketName, objectKey, data.length);
        } catch (Exception e) {
            throw new RuntimeException("Failed to store document in MinIO", e);
        }
    }

    public byte[] load(String bucket, String objectKey) {
        try (InputStream inputStream = minioClient.getObject(
            GetObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .build()
        )) {
            return inputStream.readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load document from MinIO", e);
        }
    }

    private void ensureBucketExists() throws Exception {
        boolean exists = minioClient.bucketExists(
            BucketExistsArgs.builder()
                .bucket(bucketName)
                .build()
        );
        if (!exists) {
            log.info("Creating MinIO bucket {}", bucketName);
            minioClient.makeBucket(
                MakeBucketArgs.builder()
                    .bucket(bucketName)
                    .build()
            );
        }
    }

    public record StoredDocument(String bucket, String objectKey, long size) {
    }
}
