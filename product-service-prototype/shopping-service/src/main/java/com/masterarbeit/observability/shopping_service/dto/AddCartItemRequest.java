package com.masterarbeit.observability.shopping_service.dto;

public record AddCartItemRequest(Long productId, Integer quantity) {
}
