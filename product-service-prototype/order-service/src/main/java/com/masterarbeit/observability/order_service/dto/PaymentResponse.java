package com.masterarbeit.observability.order_service.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        Long id,
        BigDecimal sum,
        String status,
        LocalDateTime createdAt
) {
}
