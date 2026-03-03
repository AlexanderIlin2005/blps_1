package ru.sashil.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.sashil.model.Product;
import ru.sashil.repository.ProductRepository;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Slf4j
public class ProductController {

    private final ProductRepository productRepository;

    @GetMapping
    public ResponseEntity<List<Product>> getAllProducts() {
        log.info("REST request to get all products");
        return ResponseEntity.ok(productRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> getProduct(@PathVariable Long id) {
        log.info("REST request to get product: {}", id);
        return productRepository.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/search/{name}")
    public ResponseEntity<Product> searchProduct(@PathVariable String name) {
        log.info("REST request to search product: {}", name);
        return productRepository.findByNameContainingIgnoreCase(name)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
