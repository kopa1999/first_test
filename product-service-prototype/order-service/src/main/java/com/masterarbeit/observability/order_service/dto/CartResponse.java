package com.masterarbeit.observability.order_service.dto;

import java.util.List;

public record CartResponse(Long id, Long userId, List<CartItemResponse> items) {
}
