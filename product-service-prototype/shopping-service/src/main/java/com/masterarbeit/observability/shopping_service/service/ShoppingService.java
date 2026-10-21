package com.masterarbeit.observability.shopping_service.service;

import com.masterarbeit.observability.shopping_service.client.ProductClient;
import com.masterarbeit.observability.shopping_service.client.UserClient;
import com.masterarbeit.observability.shopping_service.dto.AddCartItemRequest;
import com.masterarbeit.observability.shopping_service.dto.CreateCartRequest;
import com.masterarbeit.observability.shopping_service.dto.ProductResponse;
import com.masterarbeit.observability.shopping_service.model.Cart;
import com.masterarbeit.observability.shopping_service.model.CartItem;
import com.masterarbeit.observability.shopping_service.repository.CartRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ShoppingService {

    private final CartRepository cartRepository;
    private final UserClient userClient;
    private final ProductClient productClient;

    public ShoppingService(CartRepository cartRepository, UserClient userClient, ProductClient productClient) {
        this.cartRepository = cartRepository;
        this.userClient = userClient;
        this.productClient = productClient;
    }

    @Transactional
    public Cart createCart(CreateCartRequest request) {
        userClient.getUserById(request.userId());
        Cart cart = new Cart(request.userId());
        return cartRepository.save(cart);
    }

    public Cart getCartById(Long cartId) {
        return cartRepository.findById(cartId)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart not found")
                );
    }

    @Transactional
    public Cart addItem(Long cartId, AddCartItemRequest request) {

        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart not found")
                );

        ProductResponse product =
                productClient.getProductById(request.productId());

        CartItem existingItem = cart.getItems()
                .stream()
                .filter(item ->
                        item.getId().equals(product.id())
                )
                .findFirst()
                .orElse(null);

        if (existingItem != null) {

            int newQuantity =
                    existingItem.getQuantity() + request.quantity();

            if (newQuantity > product.quantity()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Not enough stock");
            }

            existingItem.setQuantity(newQuantity);

        } else {

            if (request.quantity() > product.quantity()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Not enough stock");
            }

            CartItem item = new CartItem(
                    product.id(),
                    product.name(),
                    product.price(),
                    request.quantity(),
                    cart
            );

            cart.addItem(item);
        }

        return cartRepository.save(cart);
    }

    @Transactional
    public Cart removeItem(Long cartId, Long itemId) {

        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart not found")
                );

        CartItem item = cart.getItems()
                .stream()
                .filter(cartItem ->
                        cartItem.getId().equals(itemId)
                )
                .findFirst()
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart item not found")
                );

        cart.removeItem(item);

        return cartRepository.save(cart);
    }

    @Transactional
    public Cart clearCart(Long cartId) {

        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart not found")
                );

        cart.getItems().clear();

        return cartRepository.save(cart);
    }

}
