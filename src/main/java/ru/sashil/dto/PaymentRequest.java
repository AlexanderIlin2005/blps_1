package ru.sashil.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {
    private String orderNumber;
    private String paymentMethod;
    private Double amount;
    private String cardNumber;
    private String cardExpiry;
    private String cardCvv;
    private String phoneNumber; // для SBP
}
