package ru.sashil.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentTask implements Serializable {
    private String orderNumber;
    private Double amount;
    private String paymentMethod;
    private String details;
    private Long timestamp;
}
