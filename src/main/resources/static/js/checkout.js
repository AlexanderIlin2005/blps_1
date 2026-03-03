// Получение корзины из localStorage
const cart = JSON.parse(localStorage.getItem('cart')) || [];
const cartTotal = localStorage.getItem('cartTotal') || 0;

// Отображение товаров в заказе
function displayOrderItems() {
    const orderItems = document.getElementById('order-items');
    const orderTotal = document.getElementById('order-total');
    const cartContainer = document.getElementById('cart-items-container');

    if (cart.length === 0) {
        window.location.href = '/catalog';
        return;
    }

    let itemsHtml = '<table class="order-items-table">';
    itemsHtml += '<tr><th>Товар</th><th>Кол-во</th><th>Цена</th><th>Сумма</th></tr>';

    // Очищаем контейнер для скрытых полей
    cartContainer.innerHTML = '';

    cart.forEach((item, index) => {
        const total = item.price * item.quantity;
        itemsHtml += `
            <tr>
                <td>${item.name}</td>
                <td>${item.quantity}</td>
                <td>${item.price.toLocaleString()} ₽</td>
                <td>${total.toLocaleString()} ₽</td>
            </tr>
        `;

        // Добавляем скрытые поля для каждого товара
        cartContainer.innerHTML += `
            <input type="hidden" name="items[${index}].productId" value="${item.sku}">
            <input type="hidden" name="items[${index}].productName" value="${item.name}">
            <input type="hidden" name="items[${index}].quantity" value="${item.quantity}">
            <input type="hidden" name="items[${index}].price" value="${item.price}">
        `;
    });

    itemsHtml += '</table>';
    orderItems.innerHTML = itemsHtml;
    orderTotal.textContent = Number(cartTotal).toLocaleString();
}

// Переключение полей доставки
function toggleDeliveryFields() {
    const courierFields = document.getElementById('courier-fields');
    const pickupFields = document.getElementById('pickup-fields');
    const deliveryRadios = document.querySelectorAll('input[name="deliveryType"]');
    let deliveryType = 'COURIER';

    deliveryRadios.forEach(radio => {
        if (radio.checked) {
            deliveryType = radio.value;
        }
    });

    if (deliveryType === 'COURIER') {
        courierFields.style.display = 'block';
        pickupFields.style.display = 'none';
        document.getElementById('deliveryAddress').required = true;
        if (document.getElementById('pickupPointId')) {
            document.getElementById('pickupPointId').required = false;
        }
    } else {
        courierFields.style.display = 'none';
        pickupFields.style.display = 'block';
        document.getElementById('deliveryAddress').required = false;
        if (document.getElementById('pickupPointId')) {
            document.getElementById('pickupPointId').required = true;
        }
    }
}

// Переключение полей оплаты
function togglePaymentFields() {
    const cardDetails = document.getElementById('card-details');
    const sbpDetails = document.getElementById('sbp-details');
    const paymentRadios = document.querySelectorAll('input[name="paymentMethod"]');
    let paymentMethod = 'card';

    paymentRadios.forEach(radio => {
        if (radio.checked) {
            paymentMethod = radio.value;
        }
    });

    cardDetails.style.display = 'none';
    sbpDetails.style.display = 'none';

    if (paymentMethod === 'card') {
        cardDetails.style.display = 'block';
    } else if (paymentMethod === 'sbp') {
        sbpDetails.style.display = 'block';
    }
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

    // Устанавливаем начальное состояние
    toggleDeliveryFields();
    togglePaymentFields();
});
