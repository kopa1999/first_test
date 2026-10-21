package com.masterarbeit.observability.shopping_service.controller;

import com.masterarbeit.observability.shopping_service.dto.AddCartItemRequest;
import com.masterarbeit.observability.shopping_service.dto.CreateCartRequest;
import com.masterarbeit.observability.shopping_service.model.Cart;
import com.masterarbeit.observability.shopping_service.service.ShoppingService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/carts")
public class ShoppingController {

    private final ShoppingService shoppingService;

    public ShoppingController(ShoppingService shoppingService) {
        this.shoppingService = shoppingService;
    }

    @PostMapping
    public Cart createCart(@RequestBody CreateCartRequest cCRequest) {
        return shoppingService.createCart(cCRequest);
    }

    @GetMapping("/{id}")
    public Cart getCartById(@PathVariable Long id) {
        return shoppingService.getCartById(id);
    }

    @PostMapping("/{cartId}/items")
    public Cart addItem(@PathVariable Long cartId, @RequestBody AddCartItemRequest aCIRequest) {
        return shoppingService.addItem(cartId, aCIRequest);
    }

    @DeleteMapping("/{cardId}/items/{itemId}")
    public Cart removeItem(@PathVariable Long cartId, @PathVariable Long itemId) {
        return shoppingService.removeItem(cartId, itemId);
    }

    @PostMapping("/{cartId}/clear")
    public Cart clearCart(@PathVariable Long cartId) {
        return shoppingService.clearCart(cartId);
    }
}
