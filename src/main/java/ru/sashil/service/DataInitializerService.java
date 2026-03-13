package ru.sashil.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import ru.sashil.model.Product;
import ru.sashil.model.Role;
import ru.sashil.model.Privilege;
import ru.sashil.repository.ProductRepository;
import ru.sashil.repository.RoleRepository;
import ru.sashil.repository.PrivilegeRepository;
import ru.sashil.repository.UserRepository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataInitializerService {

    private final ProductRepository productRepository;
    private final RoleRepository roleRepository;
    private final PrivilegeRepository privilegeRepository;
    private final UserRepository userRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @PostConstruct
    public void initData() {
        log.info("Initializing sample data and security config using TransactionTemplate...");
        transactionTemplate.execute(status -> {
            initSecurityData();
            initProducts();
            return null;
        });
    }

    private void initSecurityData() {
        Privilege readCatalog = createPrivilegeIfNotFound("READ_CATALOG");
        Privilege createOrder = createPrivilegeIfNotFound("CREATE_ORDER");
        Privilege readOwnOrders = createPrivilegeIfNotFound("READ_OWN_ORDERS");
        Privilege cancelOwnOrder = createPrivilegeIfNotFound("CANCEL_OWN_ORDER");
        Privilege readAllOrders = createPrivilegeIfNotFound("READ_ALL_ORDERS");
        Privilege updateOrderStatus = createPrivilegeIfNotFound("UPDATE_ORDER_STATUS");
        Privilege manageProducts = createPrivilegeIfNotFound("MANAGE_PRODUCTS");

        List<Privilege> customerPrivileges = Arrays.asList(readCatalog, createOrder, readOwnOrders, cancelOwnOrder);
        Role customerRole = createRoleIfNotFound("ROLE_CUSTOMER", customerPrivileges);

        List<Privilege> managerPrivileges = Arrays.asList(readCatalog, createOrder, readOwnOrders, cancelOwnOrder, readAllOrders, updateOrderStatus);
        createRoleIfNotFound("ROLE_MANAGER", managerPrivileges);

        List<Privilege> adminPrivileges = Arrays.asList(readCatalog, createOrder, readOwnOrders, cancelOwnOrder, readAllOrders, updateOrderStatus, manageProducts);
        createRoleIfNotFound("ROLE_ADMIN", adminPrivileges);

        // Fix existing users without roles
        userRepository.findAll().stream()
            .filter(u -> u.getRoles() == null || u.getRoles().isEmpty())
            .forEach(u -> {
                log.info("Assigning ROLE_CUSTOMER to existing user: {}", u.getUsername());
                u.setRoles(new ArrayList<>(Collections.singletonList(customerRole)));
                userRepository.save(u);
            });
    }

    private Privilege createPrivilegeIfNotFound(String name) {
        return privilegeRepository.findByName(name)
            .orElseGet(() -> privilegeRepository.save(new Privilege(null, name)));
    }

    private Role createRoleIfNotFound(String name, Collection<Privilege> privileges) {
        return roleRepository.findByName(name).orElseGet(() -> {
            Role role = new Role();
            role.setName(name);
            role.setPrivileges(new ArrayList<>(privileges));
            return roleRepository.save(role);
        });
    }

    private void initProducts() {
        // Updated to always ensure core products have emojis and info
        List<Product> products = Arrays.asList(
            createProduct("IPHONE-14-PRO", "iPhone 14 Pro", "Смартфон Apple iPhone 14 Pro 256GB", 99999.99, "Смартфоны", "📱"),
            createProduct("APPLE-WATCH-8", "Apple Watch Series 8", "Умные часы Apple Watch Series 8", 39999.99, "Часы", "⌚"),
            createProduct("IPAD-PRO", "iPad Pro", "Планшет Apple iPad Pro 11\"", 79999.99, "Планшеты", "平板"),
            createProduct("IPHONE-15-PRO", "iPhone 15 Pro", "A17 Pro chip, Titanium design", 119999.0, "Смартфоны", "📱"),
            createProduct("MACBOOK-M3-AIR", "MacBook Air M3", "13-inch, 16GB RAM, 512GB SSD", 145000.0, "Ноутбуки", "💻"),
            createProduct("SONY-WH1000XM5", "Sony WH-1000XM5", "Best-in-class Noise Cancelling Headphones", 35990.0, "Аудио", "🎧"),
            createProduct("IPAD-PRO-M4", "iPad Pro M4", "Ultra Retina XDR Display", 99999.0, "Планшеты", "平板"),
            createProduct("RTX-4090-FE", "NVIDIA RTX 4090", "Founders Edition 24GB VRAM", 249000.0, "Комплектующие", "🎮"),
            createProduct("PS5-SLIM", "PlayStation 5 Slim", "Digital Edition 1TB", 54990.0, "Консоли", "🕹️"),
            createProduct("KEYCHRON-Q1", "Keychron Q1 Pro", "Mechanical Custom Keyboard", 18500.0, "Периферия", "⌨️"),
            createProduct("LOGI-MX-MASTER", "Logitech MX Master 3S", "Ergonomic Productivity Mouse", 12990.0, "Периферия", "🖱️"),
            createProduct("SAMSUNG-S24-ULTRA", "Samsung S24 Ultra", "AI Camera, S-Pen included", 124990.0, "Смартфоны", "📱"),
            createProduct("DYSON-V15", "Dyson V15 Detect", "Cordless Vacuum Cleaner", 79990.0, "Дом", "🧹")
        );
        productRepository.saveAll(products);
        log.info("Products synchronized with emojis");
    }

    private Product createProduct(String sku, String name, String desc, Double price, String cat, String emoji) {
        Product p = productRepository.findBySku(sku).orElse(new Product());
        p.setSku(sku);
        p.setName(emoji + " " + name);
        p.setDescription(desc);
        p.setPrice(price);
        p.setStockQuantity(100);
        p.setCategory(cat);
        p.setActive(true);
        return p;
    }
}
