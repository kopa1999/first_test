package com.masterarbeit.observability.payment_service.service;

import com.masterarbeit.observability.payment_service.dto.CreatePaymentRequest;
import com.masterarbeit.observability.payment_service.model.Payment;
import com.masterarbeit.observability.payment_service.model.PaymentStatus;
import com.masterarbeit.observability.payment_service.repository.PaymentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Value;

@Service
public class PaymentService {
    private final PaymentRepository paymentRepository;

    @Value("${PAYMENT_DELAY_MS:0}")
    private long paymentDelayMs;

    @Value("${PAYMENT_FORCE_FAILURE:false}")
    private boolean paymentForceFailure;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    /*public Payment createPayment(CreatePaymentRequest createPaymentRequest) {
        Payment payment = new Payment(createPaymentRequest.sum(), PaymentStatus.COMPLETED);
        return paymentRepository.save(payment);
    }*/

    public Payment createPayment(CreatePaymentRequest createPaymentRequest) {

        if (paymentDelayMs > 0) {
            try {
                Thread.sleep(paymentDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Payment delay interrupted", e);
            }
        }

	if(paymentForceFailure) {
		throw new ResponseStatusException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"Simulated payment failure"
		);
	}

        Payment payment =
                new Payment(createPaymentRequest.sum(), PaymentStatus.COMPLETED);

        return paymentRepository.save(payment);
    }


    public Payment getPaymentById(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found")
                );
    }
}
