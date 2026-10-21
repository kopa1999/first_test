package com.masterarbeit.observability.order_service.dto;

import java.math.BigDecimal;

public record ProductResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        int quantity
) {
}
