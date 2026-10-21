package com.masterarbeit.observability.shopping_service.client;

import com.masterarbeit.observability.shopping_service.dto.ProductResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ProductClient {

    private final RestClient restClient;

    public ProductClient(
            RestClient.Builder builder,
            @Value("${services.product.url}") String productServiceUrl
    ) {
        this.restClient = builder
                .baseUrl(productServiceUrl)
                .build();
    }

    public ProductResponse getProductById(Long productId) {
        return restClient
                .get()
                .uri("/products/{id}", productId)
                .retrieve()
                .body(ProductResponse.class);
    }
}
