
let cart = JSON.parse(localStorage.getItem('cart')) || [];
const cartTotal = localStorage.getItem('cartTotal') || 0;

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
        if (document.getElementById('deliveryAddress')) {
            document.getElementById('deliveryAddress').required = true;
        }
    } else {
        courierFields.style.display = 'none';
        pickupFields.style.display = 'block';
        if (document.getElementById('deliveryAddress')) {
            document.getElementById('deliveryAddress').required = false;
        }
    }
}

document.addEventListener('DOMContentLoaded', () => {
    displayOrderItems();

    document.querySelectorAll('input[name="deliveryType"]').forEach(radio => {
        radio.addEventListener('change', toggleDeliveryFields);
    });

    toggleDeliveryFields();


    if (window.location.search.includes('clearCart=true')) {
        localStorage.removeItem('cart');
        localStorage.removeItem('cartTotal');
    }
});
