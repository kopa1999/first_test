package com.masterarbeit.observability.product_service.service;

import com.masterarbeit.observability.product_service.model.Product;
import com.masterarbeit.observability.product_service.repository.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.Optional;

@Service
public class ProductService {

    @Value("${PRODUCT_LOCK_HOLD_MS:0}")
    private long productLockHoldMs;
    
    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Product createProduct(Product product) {
        return productRepository.save(product);
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public Optional<Product> getProductById(Long id) {
        return productRepository.findById(id);
    }

    public Product updateQuantityRequest(Long id, Integer quantity) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        product.setQuantity(quantity);
        return productRepository.save(product);
    }

    @Transactional
    public Product reserveQuantity(Long id, Integer quantity) {
        Product product = productRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        if(productLockHoldMs > 0){
		try {
			Thread.sleep(productLockHoldMs);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Lock simulation interrupted", e);
		}
	}
	if (product.getQuantity() < quantity) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Not enough products to reserve");
        }

        product.setQuantity(product.getQuantity() - quantity);

        return productRepository.save(product);
    }

    @Transactional
    public Product releaseStock(Long id, Integer quantity) {

        Product product = productRepository.findByIdForUpdate(id)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found")
                );

        product.setQuantity(product.getQuantity() + quantity);

        return productRepository.save(product);
    }
}
