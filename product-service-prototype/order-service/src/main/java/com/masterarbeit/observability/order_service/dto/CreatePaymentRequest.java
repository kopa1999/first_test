package com.masterarbeit.observability.order_service.dto;

import java.math.BigDecimal;

public record CreatePaymentRequest(BigDecimal sum) {
}
