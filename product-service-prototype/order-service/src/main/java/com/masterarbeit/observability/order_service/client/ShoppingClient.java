package com.masterarbeit.observability.order_service.client;

import com.masterarbeit.observability.order_service.dto.CartResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ShoppingClient {

    private final RestClient restClient;

    public ShoppingClient(
            RestClient.Builder builder,
            @Value("${services.shopping.url}") String shoppingServiceUrl
    ) {
        this.restClient = builder
                .baseUrl(shoppingServiceUrl)
                .build();
    }

    public CartResponse getCartById(Long cId) {
        return restClient
                .get()
                .uri("/carts/{id}", cId)
                .retrieve()
                .body(CartResponse.class);
    }

    public void clearCart(Long cId) {
        restClient
                .post()
                .uri("/carts/{id}/clear", cId)
                .retrieve()
                .toBodilessEntity();
    }
}
