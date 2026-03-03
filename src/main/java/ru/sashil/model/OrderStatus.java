package ru.sashil.model;

public enum OrderStatus {
    CART,               // Корзина (просмотр каталога)
    CHECKOUT,           // Оформление
    PAYMENT_PENDING,    // Ожидание оплаты
    PAYMENT_PROCESSING, // Оплата обрабатывается
    PAID,               // Оплачено
    CONFIRMED,          // Подтверждено
    PROCESSING,         // В обработке (склад)
    PACKING,            // Сборка
    READY_FOR_SHIPPING, // Готов к отправке
    SHIPPED,            // Отправлен
    OUT_FOR_DELIVERY,   // У курьера
    DELIVERED,          // Доставлен
    PICKUP_READY,       // Готов к выдаче в ПВЗ
    PICKED_UP,          // Получен в ПВЗ
    COMPLETED,          // Завершен
    CANCELLED,          // Отменен
    REFUNDED            // Возвращен
}
