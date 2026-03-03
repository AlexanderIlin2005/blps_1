-- Таблица пользователей
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    full_name VARCHAR(100),
    phone VARCHAR(20),
    role VARCHAR(20) DEFAULT 'USER',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Индексы для users
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);

-- Таблица products
CREATE TABLE IF NOT EXISTS products (
    id BIGSERIAL PRIMARY KEY,
    active BOOLEAN,
    category VARCHAR(255),
    description VARCHAR(255),
    name VARCHAR(255),
    price FLOAT,
    sku VARCHAR(255),
    stock_quantity INTEGER
);

-- Индекс для products
CREATE INDEX IF NOT EXISTS idx_products_sku ON products(sku);

-- Таблица orders
CREATE TABLE IF NOT EXISTS orders (
    id BIGSERIAL PRIMARY KEY,
    cancelled_at TIMESTAMP(6),
    created_at TIMESTAMP(6),
    customer_email VARCHAR(255),
    customer_id BIGINT,
    customer_name VARCHAR(255),
    customer_phone VARCHAR(255),
    delivered_at TIMESTAMP(6),
    delivery_address VARCHAR(255),
    delivery_type VARCHAR(255),
    discount FLOAT,
    order_number VARCHAR(255) UNIQUE,
    paid_at TIMESTAMP(6),
    payment_id VARCHAR(255),
    payment_method VARCHAR(255),
    payment_status VARCHAR(255),
    pickup_point_address VARCHAR(255),
    pickup_point_id VARCHAR(255),
    shipped_at TIMESTAMP(6),
    status VARCHAR(255),
    subtotal FLOAT,
    total FLOAT,
    tracking_number VARCHAR(255),
    updated_at TIMESTAMP(6),
    user_id BIGINT,
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

-- Индексы для orders
CREATE INDEX IF NOT EXISTS idx_orders_order_number ON orders(order_number);
CREATE INDEX IF NOT EXISTS idx_orders_customer_id ON orders(customer_id);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
CREATE INDEX IF NOT EXISTS idx_orders_payment_status ON orders(payment_status);
CREATE INDEX IF NOT EXISTS idx_orders_user_id ON orders(user_id);

-- Таблица order_items
CREATE TABLE IF NOT EXISTS order_items (
    id BIGSERIAL PRIMARY KEY,
    price FLOAT,
    product_id VARCHAR(255),
    product_name VARCHAR(255),
    quantity INTEGER,
    total FLOAT,
    order_id BIGINT,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
);

-- Индекс для order_items
CREATE INDEX IF NOT EXISTS idx_order_items_order_id ON order_items(order_id);

-- Таблица истории статусов заказа
CREATE TABLE IF NOT EXISTS order_status_history (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    description VARCHAR(255),
    CONSTRAINT fk_order_status_history_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
);

-- Индексы для order_status_history
CREATE INDEX IF NOT EXISTS idx_order_status_history_order_id ON order_status_history(order_id);
CREATE INDEX IF NOT EXISTS idx_order_status_history_changed_at ON order_status_history(changed_at);

-- Комментарии к таблицам
COMMENT ON TABLE users IS 'Пользователи системы';
COMMENT ON TABLE products IS 'Товары в каталоге';
COMMENT ON TABLE orders IS 'Заказы пользователей';
COMMENT ON TABLE order_items IS 'Товары в заказах';
COMMENT ON TABLE order_status_history IS 'История изменений статусов заказов';

