package com.masterarbeit.observability.shopping_service.repository;

import com.masterarbeit.observability.shopping_service.model.Cart;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartRepository extends JpaRepository<Cart, Long> {
}