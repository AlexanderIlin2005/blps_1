// Получение корзины из localStorage
const cart = JSON.parse(localStorage.getItem('cart')) || [];
const cartTotal = localStorage.getItem('cartTotal') || 0;

// Отображение товаров в заказе
function displayOrderItems() {
    const orderItems = document.getElementById('order-items');
    const orderTotal = document.getElementById('order-total');

    if (cart.length === 0) {
        window.location.href = '/catalog';
        return;
    }

    let itemsHtml = '<table class="order-items-table">';
    itemsHtml += '<tr><th>Товар</th><th>Кол-во</th><th>Цена</th><th>Сумма</th></tr>';

    cart.forEach(item => {
        const total = item.price * item.quantity;
        itemsHtml += `
            <tr>
                <td>${item.name}</td>
                <td>${item.quantity}</td>
                <td>${item.price.toLocaleString()} ₽</td>
                <td>${total.toLocaleString()} ₽</td>
            </tr>
        `;
    });

    itemsHtml += '</table>';
    orderItems.innerHTML = itemsHtml;
    orderTotal.textContent = cartTotal;
}

// Переключение полей доставки
function toggleDeliveryFields() {
    const courierFields = document.getElementById('courier-fields');
    const pickupFields = document.getElementById('pickup-fields');
    const deliveryType = document.querySelector('input[name="deliveryType"]:checked').value;

    if (deliveryType === 'COURIER') {
        courierFields.style.display = 'block';
        pickupFields.style.display = 'none';
        document.getElementById('deliveryAddress').required = true;
        document.getElementById('pickupPointId').required = false;
    } else {
        courierFields.style.display = 'none';
        pickupFields.style.display = 'block';
        document.getElementById('deliveryAddress').required = false;
        document.getElementById('pickupPointId').required = true;
    }
}

// Переключение полей оплаты
function togglePaymentFields() {
    const cardDetails = document.getElementById('card-details');
    const sbpDetails = document.getElementById('sbp-details');
    const paymentMethod = document.querySelector('input[name="paymentMethod"]:checked').value;

    cardDetails.style.display = 'none';
    sbpDetails.style.display = 'none';

    if (paymentMethod === 'card') {
        cardDetails.style.display = 'block';
    } else if (paymentMethod === 'sbp') {
        sbpDetails.style.display = 'block';
    }
}

// Отправка формы
async function submitOrder(event) {
    event.preventDefault();

    const formData = new FormData(event.target);
    const deliveryType = formData.get('deliveryType');

    const orderData = {
        customerId: 1, // Временный ID
        customerName: formData.get('customerName'),
        customerEmail: formData.get('customerEmail'),
        customerPhone: formData.get('customerPhone'),
        items: cart.map(item => ({
            productId: item.sku,
            productName: item.name,
            quantity: item.quantity,
            price: item.price
        })),
        deliveryType: deliveryType,
        deliveryAddress: deliveryType === 'COURIER' ? formData.get('deliveryAddress') : null,
        pickupPointId: deliveryType === 'PICKUP' ? formData.get('pickupPointId') : null,
        promoCode: formData.get('promoCode')
    };

    try {
        const response = await fetch('/api/orders', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(orderData)
        });

        if (!response.ok) {
            throw new Error('Ошибка при создании заказа');
        }

        const order = await response.json();

        // Переходим к оплате
        await processPayment(order.orderNumber, formData);

    } catch (error) {
        console.error('Error:', error);
        alert('Произошла ошибка при оформлении заказа');
    }
}

// Обработка платежа
async function processPayment(orderNumber, formData) {
    const paymentMethod = formData.get('paymentMethod');
    let paymentDetails = {};

    if (paymentMethod === 'card') {
        paymentDetails = {
            cardNumber: formData.get('cardNumber'),
            cardExpiry: formData.get('cardExpiry'),
            cardCvv: formData.get('cardCvv')
        };
    } else if (paymentMethod === 'sbp') {
        paymentDetails = {
            phoneNumber: formData.get('phoneNumber')
        };
    }

    const paymentData = {
        orderNumber: orderNumber,
        paymentMethod: paymentMethod,
        ...paymentDetails
    };

    try {
        const response = await fetch(`/api/orders/${orderNumber}/payment`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(paymentData)
        });

        if (!response.ok) {
            throw new Error('Ошибка при оплате');
        }

        const order = await response.json();

        // Очищаем корзину
        localStorage.removeItem('cart');
        localStorage.removeItem('cartTotal');

        // Показываем результат
        document.getElementById('checkout-form').style.display = 'none';
        const resultDiv = document.getElementById('order-result');
        resultDiv.style.display = 'block';
        document.getElementById('result-order-number').textContent = order.orderNumber;
        document.getElementById('result-status').textContent = getStatusText(order.status);
        document.getElementById('result-link').href = `/order/${order.orderNumber}`;

    } catch (error) {
        console.error('Payment error:', error);
        alert('Ошибка при оплате заказа');
    }
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
document.addEventListener('DOMContentLoaded', () => {
    displayOrderItems();

    // Добавляем обработчики
    document.querySelectorAll('input[name="deliveryType"]').forEach(radio => {
        radio.addEventListener('change', toggleDeliveryFields);
    });

    document.querySelectorAll('input[name="paymentMethod"]').forEach(radio => {
        radio.addEventListener('change', togglePaymentFields);
    });

    document.getElementById('checkout-form').addEventListener('submit', submitOrder);

    // Устанавливаем начальное состояние
    toggleDeliveryFields();
    togglePaymentFields();
});
