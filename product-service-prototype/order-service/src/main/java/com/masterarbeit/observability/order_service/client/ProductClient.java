package com.masterarbeit.observability.order_service.client;

import com.masterarbeit.observability.order_service.dto.ProductResponse;
import com.masterarbeit.observability.order_service.dto.ReleaseStockRequest;
import com.masterarbeit.observability.order_service.dto.ReserveRequest;
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

    public ProductResponse reserveProduct(Long pId, int quantity) {
        return restClient
                .post()
                .uri("/products/{id}/reserve", pId)
                .body(new ReserveRequest(quantity))
                .retrieve()
                .body(ProductResponse.class);
    }

    public ProductResponse releaseStock(Long pId, Integer quantity) {
        return restClient
                .post()
                .uri("/products/{id}/release", pId)
                .body(new ReleaseStockRequest(quantity))
                .retrieve()
                .body(ProductResponse.class);
    }
}
