package ru.sashil.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import ru.sashil.model.OrderStatus;
import ru.sashil.model.DeliveryType;
import ru.sashil.model.PaymentStatus;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private Long id;
    private String orderNumber;
    private OrderStatus status;
    private LocalDateTime createdAt;
    private List<CartItemDTO> items;
    private Double total;
    private DeliveryType deliveryType;
    private String deliveryAddress;
    private String pickupPointAddress;
    private String paymentMethod;
    private PaymentStatus paymentStatus;
    private String trackingNumber;

    // История статусов
    private List<StatusHistoryDTO> statusHistory;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusHistoryDTO {
        private OrderStatus status;
        private LocalDateTime changedAt;
        private String description;
    }
}
