
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

        document.getElementById('cardNumber').required = true;
        document.getElementById('cardExpiry').required = true;
        document.getElementById('cardCvv').required = true;
        if (document.getElementById('phoneNumber')) {
            document.getElementById('phoneNumber').required = false;
        }
    } else if (paymentMethod === 'sbp') {
        sbpDetails.style.display = 'block';
        document.getElementById('cardNumber').required = false;
        document.getElementById('cardExpiry').required = false;
        document.getElementById('cardCvv').required = false;
        if (document.getElementById('phoneNumber')) {
            document.getElementById('phoneNumber').required = true;
        }
    } else {
        document.getElementById('cardNumber').required = false;
        document.getElementById('cardExpiry').required = false;
        document.getElementById('cardCvv').required = false;
        if (document.getElementById('phoneNumber')) {
            document.getElementById('phoneNumber').required = false;
        }
    }
}


document.addEventListener('DOMContentLoaded', () => {
    displayOrderItems();


    document.querySelectorAll('input[name="deliveryType"]').forEach(radio => {
        radio.addEventListener('change', toggleDeliveryFields);
    });

    document.querySelectorAll('input[name="paymentMethod"]').forEach(radio => {
        radio.addEventListener('change', togglePaymentFields);
    });


    toggleDeliveryFields();
    togglePaymentFields();
});
