// Получение номера заказа из URL
const pathParts = window.location.pathname.split('/');
const orderNumber = pathParts[pathParts.length - 1];

// Загрузка деталей заказа
async function loadOrderDetails() {
    try {
        const response = await fetch(`/api/orders/${orderNumber}`);

        if (!response.ok) {
            throw new Error('Заказ не найден');
        }

        const order = await response.json();
        displayOrderDetails(order);
    } catch (error) {
        console.error('Error:', error);
        document.getElementById('order-details').innerHTML = `
            <div class="error">
                <h2>Ошибка</h2>
                <p>Заказ не найден</p>
                <a href="/" class="btn btn-primary">На главную</a>
            </div>
        `;
    }
}

// Отображение деталей заказа
function displayOrderDetails(order) {
    const container = document.getElementById('order-details');

    const statusClass = getStatusClass(order.status);
    const statusText = getStatusText(order.status);

    let itemsHtml = '<table class="order-items-table">';
    itemsHtml += '<tr><th>Товар</th><th>Кол-во</th><th>Цена</th><th>Сумма</th></tr>';

    order.items.forEach(item => {
        const total = item.price * item.quantity;
        itemsHtml += `
            <tr>
                <td>${item.productName}</td>
                <td>${item.quantity}</td>
                <td>${item.price.toLocaleString()} ₽</td>
                <td>${total.toLocaleString()} ₽</td>
            </tr>
        `;
    });

    itemsHtml += '</table>';

    container.innerHTML = `
        <h2>Заказ #${order.orderNumber}</h2>

        <div class="order-info">
            <p><strong>Статус:</strong> <span class="status-badge ${statusClass}">${statusText}</span></p>
            <p><strong>Дата создания:</strong> ${new Date(order.createdAt).toLocaleString()}</p>
            <p><strong>Способ получения:</strong> ${order.deliveryType === 'COURIER' ? 'Курьером' : 'Самовывоз'}</p>
            ${order.deliveryAddress ? `<p><strong>Адрес доставки:</strong> ${order.deliveryAddress}</p>` : ''}
            ${order.pickupPointAddress ? `<p><strong>Пункт самовывоза:</strong> ${order.pickupPointAddress}</p>` : ''}
            <p><strong>Способ оплаты:</strong> ${order.paymentMethod || 'Не выбран'}</p>
            <p><strong>Статус оплаты:</strong> ${order.paymentStatus || 'Ожидает'}</p>
            ${order.trackingNumber ? `<p><strong>Трек-номер:</strong> ${order.trackingNumber}</p>` : ''}
        </div>

        <h3>Состав заказа</h3>
        ${itemsHtml}

        <div class="order-total">
            <strong>Итого: ${order.total.toLocaleString()} ₽</strong>
        </div>

        <div class="order-actions">
            <a href="/" class="btn btn-primary">На главную</a>
        </div>
    `;
}

// Получение класса статуса
function getStatusClass(status) {
    const statusClasses = {
        'PAID': 'status-completed',
        'COMPLETED': 'status-completed',
        'DELIVERED': 'status-completed',
        'PROCESSING': 'status-processing',
        'PACKING': 'status-processing',
        'SHIPPED': 'status-processing',
        'CANCELLED': 'status-cancelled',
        'REFUNDED': 'status-cancelled'
    };
    return statusClasses[status] || 'status-pending';
}

// Получение текста статуса
function getStatusText(status) {
    const statusMap = {
        'CART': 'Корзина',
        'CHECKOUT': 'Оформление',
        'PAYMENT_PENDING': 'Ожидание оплаты',
        'PAYMENT_PROCESSING': 'Оплата обрабатывается',
        'PAID': 'Оплачено',
        'CONFIRMED': 'Подтверждено',
        'PROCESSING': 'В обработке',
        'PACKING': 'Сборка',
        'READY_FOR_SHIPPING': 'Готов к отправке',
        'SHIPPED': 'Отправлен',
        'OUT_FOR_DELIVERY': 'У курьера',
        'DELIVERED': 'Доставлен',
        'PICKUP_READY': 'Готов к выдаче',
        'PICKED_UP': 'Получен',
        'COMPLETED': 'Завершен',
        'CANCELLED': 'Отменен',
        'REFUNDED': 'Возвращен'
    };
    return statusMap[status] || status;
}

// Инициализация
document.addEventListener('DOMContentLoaded', loadOrderDetails);
