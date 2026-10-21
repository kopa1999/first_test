package com.masterarbeit.observability.payment_service.controller;

import com.masterarbeit.observability.payment_service.dto.CreatePaymentRequest;
import com.masterarbeit.observability.payment_service.model.Payment;
import com.masterarbeit.observability.payment_service.service.PaymentService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payments")
public class PaymentController {
    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public Payment createPayment(@RequestBody CreatePaymentRequest cPRequest) {
        return paymentService.createPayment(cPRequest);
    }

    @GetMapping("/{id}")
    public Payment getPaymentById(@PathVariable("id") Long id) {
        return paymentService.getPaymentById(id);
    }
}
