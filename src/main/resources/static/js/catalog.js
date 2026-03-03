// Корзина в localStorage
let cart = JSON.parse(localStorage.getItem('cart')) || [];

// Загрузка товаров
async function loadProducts() {
    try {
        const response = await fetch('/api/products');
        const products = await response.json();

        const productsContainer = document.getElementById('products');
        productsContainer.innerHTML = '';

        products.forEach(product => {
            const productCard = createProductCard(product);
            productsContainer.appendChild(productCard);
        });
    } catch (error) {
        console.error('Error loading products:', error);
    }
}

// Создание карточки товара
function createProductCard(product) {
    const card = document.createElement('div');
    card.className = 'product-card';

    card.innerHTML = `
        <div class="product-image">📱</div>
        <div class="product-info">
            <h3>${product.name}</h3>
            <p class="product-price">${product.price.toLocaleString()} ₽</p>
            <p class="product-stock">В наличии: ${product.stockQuantity} шт.</p>
            <button class="btn btn-primary" onclick="addToCart('${product.sku}', '${product.name}', ${product.price})">
                Добавить в корзину
            </button>
        </div>
    `;

    return card;
}

// Добавление в корзину
function addToCart(sku, name, price) {
    const existingItem = cart.find(item => item.sku === sku);

    if (existingItem) {
        existingItem.quantity++;
    } else {
        cart.push({
            sku,
            name,
            price,
            quantity: 1
        });
    }

    localStorage.setItem('cart', JSON.stringify(cart));
    updateCartDisplay();

    // Анимация
    alert('Товар добавлен в корзину');
}

// Обновление отображения корзины
function updateCartDisplay() {
    const cartItems = document.getElementById('cart-items');
    const cartTotal = document.getElementById('cart-total');
    const checkoutBtn = document.getElementById('checkout-btn');

    if (cart.length === 0) {
        cartItems.innerHTML = '<p>Корзина пуста</p>';
        cartTotal.innerHTML = '';
        checkoutBtn.style.display = 'none';
        return;
    }

    let total = 0;
    let itemsHtml = '<div class="cart-items-list">';

    cart.forEach((item, index) => {
        const itemTotal = item.price * item.quantity;
        total += itemTotal;

        itemsHtml += `
            <div class="cart-item">
                <div class="cart-item-info">
                    <div class="cart-item-title">${item.name}</div>
                    <div class="cart-item-price">${item.price.toLocaleString()} ₽</div>
                </div>
                <div class="cart-item-actions">
                    <input type="number" min="1" value="${item.quantity}"
                           onchange="updateQuantity(${index}, this.value)" class="cart-item-quantity">
                    <button onclick="removeFromCart(${index})" class="btn btn-secondary">Удалить</button>
                </div>
            </div>
        `;
    });

    itemsHtml += '</div>';
    cartItems.innerHTML = itemsHtml;
    cartTotal.innerHTML = `<strong>Итого: ${total.toLocaleString()} ₽</strong>`;
    checkoutBtn.style.display = 'inline-block';

    // Сохраняем общую сумму для страницы оформления
    localStorage.setItem('cartTotal', total);
}

// Обновление количества
function updateQuantity(index, quantity) {
    if (quantity < 1) {
        removeFromCart(index);
        return;
    }

    cart[index].quantity = parseInt(quantity);
    localStorage.setItem('cart', JSON.stringify(cart));
    updateCartDisplay();
}

// Удаление из корзины
function removeFromCart(index) {
    cart.splice(index, 1);
    localStorage.setItem('cart', JSON.stringify(cart));
    updateCartDisplay();
}

// Инициализация
document.addEventListener('DOMContentLoaded', () => {
    loadProducts();
    updateCartDisplay();
});
