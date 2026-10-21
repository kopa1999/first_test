package com.masterarbeit.observability.shopping_service.client;

import com.masterarbeit.observability.shopping_service.dto.UserResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class UserClient {
    private final RestClient restClient;

    public UserClient(
            RestClient.Builder builder,
            @Value("${services.user.url}") String userServiceUrl
    ) {
        this.restClient = builder
                .baseUrl(userServiceUrl)
                .build();
    }

    public UserResponse getUserById(Long userId) {
        return restClient
                .get()
                .uri("/users/{id}", userId)
                .retrieve()
                .body(UserResponse.class);
    }
}
