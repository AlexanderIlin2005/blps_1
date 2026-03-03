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

    // Визуальное подтверждение
    showNotification('Товар добавлен в корзину');
}

// Показать уведомление
function showNotification(message) {
    const notification = document.createElement('div');
    notification.className = 'notification';
    notification.textContent = message;
    notification.style.cssText = `
        position: fixed;
        top: 20px;
        right: 20px;
        background-color: #e30613;
        color: white;
        padding: 1rem;
        border-radius: 4px;
        z-index: 1000;
        animation: slideIn 0.3s ease;
    `;

    document.body.appendChild(notification);

    setTimeout(() => {
        notification.remove();
    }, 2000);
}

// Обновление отображения корзины
function updateCartDisplay() {
    const cartItems = document.getElementById('cart-items');
    const cartTotal = document.getElementById('cart-total');
    const checkoutBtn = document.getElementById('checkout-btn');
    const loginToCheckout = document.getElementById('login-to-checkout');

    if (cart.length === 0) {
        cartItems.innerHTML = '<p>Корзина пуста</p>';
        cartTotal.innerHTML = '';
        if (checkoutBtn) checkoutBtn.style.display = 'none';
        if (loginToCheckout) loginToCheckout.style.display = 'none';
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
                    <div class="cart-item-price">${item.price.toLocaleString()} ₽ × ${item.quantity}</div>
                </div>
                <div class="cart-item-actions">
                    <div class="cart-item-total">${itemTotal.toLocaleString()} ₽</div>
                    <button onclick="removeFromCart(${index})" class="btn-remove">×</button>
                </div>
            </div>
        `;
    });

    itemsHtml += '</div>';
    cartItems.innerHTML = itemsHtml;
    cartTotal.innerHTML = `<strong>Итого: ${total.toLocaleString()} ₽</strong>`;

    // Проверяем, авторизован ли пользователь
    fetch('/profile', { method: 'HEAD' })
        .then(response => {
            if (response.ok) {
                // Пользователь авторизован
                if (checkoutBtn) {
                    checkoutBtn.style.display = 'inline-block';
                    checkoutBtn.href = '/checkout';
                }
                if (loginToCheckout) loginToCheckout.style.display = 'none';
            } else {
                // Пользователь не авторизован
                if (checkoutBtn) checkoutBtn.style.display = 'none';
                if (loginToCheckout) {
                    loginToCheckout.style.display = 'inline-block';
                    loginToCheckout.href = '/login?redirect=checkout';
                }
            }
        })
        .catch(() => {
            // Ошибка - считаем что не авторизован
            if (checkoutBtn) checkoutBtn.style.display = 'none';
            if (loginToCheckout) {
                loginToCheckout.style.display = 'inline-block';
                loginToCheckout.href = '/login?redirect=checkout';
            }
        });

    // Сохраняем общую сумму для страницы оформления
    localStorage.setItem('cartTotal', total);
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

// Добавляем стили для анимации
const style = document.createElement('style');
style.textContent = `
    @keyframes slideIn {
        from {
            transform: translateX(100%);
            opacity: 0;
        }
        to {
            transform: translateX(0);
            opacity: 1;
        }
    }

    .btn-remove {
        background: #e30613;
        color: white;
        border: none;
        width: 30px;
        height: 30px;
        border-radius: 50%;
        cursor: pointer;
        font-size: 18px;
        display: flex;
        align-items: center;
        justify-content: center;
        transition: background-color 0.3s;
    }

    .btn-remove:hover {
        background-color: #b00410;
    }

    .cart-item-total {
        font-weight: bold;
        margin-right: 10px;
    }
`;
document.head.appendChild(style);
