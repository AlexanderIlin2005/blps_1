package ru.sashil.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FulfillmentTask implements Serializable {
    private String orderNumber;
    private Long orderId;
    private Long timestamp;
}
