package com.masterarbeit.observability.shopping_service.dto;

public record UserResponse(
        Long id,
        String firstName,
        String lastName,
        String email
) {
}
