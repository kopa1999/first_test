package com.masterarbeit.observability.order_service.service;

import com.masterarbeit.observability.order_service.client.PaymentClient;
import com.masterarbeit.observability.order_service.client.ProductClient;
import com.masterarbeit.observability.order_service.client.ShoppingClient;
import com.masterarbeit.observability.order_service.client.UserClient;
import com.masterarbeit.observability.order_service.dto.CartItemResponse;
import com.masterarbeit.observability.order_service.dto.CartResponse;
import com.masterarbeit.observability.order_service.dto.CreateOrderRequest;
import com.masterarbeit.observability.order_service.dto.PaymentResponse;
import com.masterarbeit.observability.order_service.model.Order;
import com.masterarbeit.observability.order_service.model.OrderItem;
import com.masterarbeit.observability.order_service.model.OrderStatus;
import com.masterarbeit.observability.order_service.repository.OrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.ArrayList;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ShoppingClient shoppingClient;
    private final UserClient userClient;
    private final ProductClient productClient;
    private final PaymentClient paymentClient;

    public OrderService(
            OrderRepository orderRepository,
            ShoppingClient shoppingClient,
            UserClient userClient,
            ProductClient productClient,
            PaymentClient paymentClient
    ) {
        this.orderRepository = orderRepository;
        this.shoppingClient = shoppingClient;
        this.userClient = userClient;
        this.productClient = productClient;
        this.paymentClient = paymentClient;
    }

    public Order createOrder(CreateOrderRequest request) {

        CartResponse cart = shoppingClient.getCartById(request.cartId());

        if (cart.items() == null || cart.items().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart is empty");
        }

        userClient.getUserById(cart.userId());

        BigDecimal sum = calculateTotal(cart.items());

        List<CartItemResponse> reservedItems = new ArrayList<>();

        try {

            for (CartItemResponse item : cart.items()) {

                productClient.reserveProduct(
                        item.productId(),
                        item.quantity()
                );

                reservedItems.add(item);
            }

            PaymentResponse payment = paymentClient.createPayment(sum);

            if (!"COMPLETED".equals(payment.status())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Payment failed"
                );
            }

            Order order = new Order(
                    cart.userId(),
                    cart.id(),
                    sum,
                    OrderStatus.COMPLETED
            );

            order.setPaymentId(payment.id());

            for (CartItemResponse cartItem : cart.items()) {

                OrderItem orderItem = new OrderItem(
                        cartItem.productId(),
                        cartItem.productName(),
                        cartItem.unitPrice(),
                        cartItem.quantity()
                );

                order.addItem(orderItem);
            }

            Order savedOrder = orderRepository.save(order);

            shoppingClient.clearCart(cart.id());

            return savedOrder;

        } catch (Exception exception) {

            for (CartItemResponse item : reservedItems) {

                try {
                    productClient.releaseStock(
                            item.productId(),
                            item.quantity()
                    );
                } catch (Exception releaseException) {
                }
            }

            throw exception;
        }
//        for (CartItemResponse item : cart.items()) {
//            productClient.reserveProduct(
//                    item.productId(),
//                    item.quantity()
//            );
//        }
//
//        PaymentResponse payment = paymentClient.createPayment(sum);
//
//        if (!"COMPLETED".equals(payment.status())) {
//            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment failed");
//        }
//
//        Order order = new Order(
//                cart.userId(),
//                cart.id(),
//                sum,
//                OrderStatus.COMPLETED
//        );
//
//        order.setPaymentId(payment.id());
//
//        for (CartItemResponse cartItem : cart.items()) {
//
//            OrderItem orderItem = new OrderItem(
//                    cartItem.productId(),
//                    cartItem.productName(),
//                    cartItem.unitPrice(),
//                    cartItem.quantity()
//            );
//
//            order.addItem(orderItem);
//        }
//
//        return orderRepository.save(order);
    }

    public Order getOrderById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Order not found"
                        )
                );
    }

    public List<Order> getOrdersByUserId(Long userId) {
        return orderRepository.findAllByUserId(userId);
    }

    private BigDecimal calculateTotal(
            List<CartItemResponse> items
    ) {
        return items.stream()
                .map(item ->
                        item.unitPrice().multiply(
                                BigDecimal.valueOf(item.quantity())
                        )
                )
                .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add
                );
    }

}
