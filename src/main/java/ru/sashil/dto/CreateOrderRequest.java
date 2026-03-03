package ru.sashil.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import ru.sashil.model.DeliveryType;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {
    private Long customerId;
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    private List<CartItemDTO> items;
    private DeliveryType deliveryType;
    private String deliveryAddress;
    private String pickupPointId;
    private String pickupPointAddress;
    private String promoCode;
}
