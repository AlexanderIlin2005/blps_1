package ru.sashil.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "accounting_documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountingDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String orderNumber;
    private String docType;
    private String title;
    private String fileName;
    private String storageBucket;
    private String objectKey;
    private String contentType;
    private Long fileSize;
    private String content;
    private LocalDateTime createdAt;
}
