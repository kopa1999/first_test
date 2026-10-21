package com.masterarbeit.observability.product_service.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ReleaseStockRequest(@NotNull @Min(1) Integer quantity) {
}
