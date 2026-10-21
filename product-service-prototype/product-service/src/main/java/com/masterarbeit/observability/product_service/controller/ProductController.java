package com.masterarbeit.observability.product_service.controller;

import com.masterarbeit.observability.product_service.dto.ReleaseStockRequest;
import com.masterarbeit.observability.product_service.dto.ReserveQuantityRequest;
import com.masterarbeit.observability.product_service.dto.UpdateQuantityRequest;
import com.masterarbeit.observability.product_service.model.Product;
import com.masterarbeit.observability.product_service.service.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/products")
public class ProductController {
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    public Product createProduct(@RequestBody Product product) {
        return productService.createProduct(product);
    }

    @GetMapping
    public List<Product> getAllProducts() {
        return productService.getAllProducts();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> getProductById(@PathVariable Long id) {
        return productService.getProductById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/quantity")
    public Product updateQuantity(@PathVariable Long id, @RequestBody UpdateQuantityRequest uQRequest) {
        return productService.updateQuantityRequest(id, uQRequest.quantity());
    }

    @PostMapping("/{id}/reserve")
    public Product reserveProduct(@PathVariable Long id, @RequestBody ReserveQuantityRequest rQRequest) {
        return productService.reserveQuantity(id, rQRequest.quantity());
    }

    @PostMapping("/{id}/release")
    public Product releaseStock(@PathVariable Long id, @RequestBody ReleaseStockRequest request) {
        return productService.releaseStock(id, request.quantity());
    }

}
