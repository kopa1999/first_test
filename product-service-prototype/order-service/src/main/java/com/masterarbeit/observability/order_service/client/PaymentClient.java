package com.masterarbeit.observability.order_service.client;

import com.masterarbeit.observability.order_service.dto.CreatePaymentRequest;
import com.masterarbeit.observability.order_service.dto.PaymentResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

@Component
public class PaymentClient {

    private final RestClient restClient;

    public PaymentClient(
            RestClient.Builder builder,
            @Value("${services.payment.url}") String paymentServiceUrl
    ) {
        this.restClient = builder
                .baseUrl(paymentServiceUrl)
                .build();
    }

    public PaymentResponse createPayment(BigDecimal amount) {
        return restClient
                .post()
                .uri("/payments")
                .body(new CreatePaymentRequest(amount))
                .retrieve()
                .body(PaymentResponse.class);
    }
}
