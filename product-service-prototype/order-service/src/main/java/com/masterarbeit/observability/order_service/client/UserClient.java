package com.masterarbeit.observability.order_service.client;

import com.masterarbeit.observability.order_service.dto.UserResponse;
import org.apache.catalina.User;
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

    public UserResponse getUserById(Long uId) {
        return restClient
                .get()
                .uri("/users/{id}", uId)
                .retrieve()
                .body(UserResponse.class);
    }
}
