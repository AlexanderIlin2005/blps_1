package ru.sashil.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.sashil.model.Product;
import ru.sashil.repository.ProductRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataInitializerService {

    private final ProductRepository productRepository;

    @PostConstruct
    public void initData() {
        log.info("Initializing sample data...");

        if (productRepository.count() == 0) {
            
            Product phone = new Product();
            phone.setSku("IPHONE-14-PRO");
            phone.setName("iPhone 14 Pro");
            phone.setDescription("Смартфон Apple iPhone 14 Pro 256GB");
            phone.setPrice(99999.99);
            phone.setStockQuantity(50);
            phone.setCategory("Смартфоны");
            phone.setActive(true);

            Product watch = new Product();
            watch.setSku("APPLE-WATCH-8");
            watch.setName("Apple Watch Series 8");
            watch.setDescription("Умные часы Apple Watch Series 8");
            watch.setPrice(39999.99);
            watch.setStockQuantity(30);
            watch.setCategory("Часы");
            watch.setActive(true);

            Product tablet = new Product();
            tablet.setSku("IPAD-PRO");
            tablet.setName("iPad Pro");
            tablet.setDescription("Планшет Apple iPad Pro 11\"");
            tablet.setPrice(79999.99);
            tablet.setStockQuantity(20);
            tablet.setCategory("Планшеты");
            tablet.setActive(true);

            productRepository.save(phone);
            productRepository.save(watch);
            productRepository.save(tablet);

            log.info("Sample data initialized");
        }
    }
}
