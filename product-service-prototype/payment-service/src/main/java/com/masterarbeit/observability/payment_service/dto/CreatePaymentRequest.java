package com.masterarbeit.observability.payment_service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreatePaymentRequest(@NotNull @DecimalMin(value = "0.01")BigDecimal sum) {
}
