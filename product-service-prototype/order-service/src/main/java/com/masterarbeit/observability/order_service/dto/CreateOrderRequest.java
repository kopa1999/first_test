package com.masterarbeit.observability.order_service.dto;

import jakarta.validation.constraints.NotNull;

public record CreateOrderRequest(@NotNull Long cartId) {
}
