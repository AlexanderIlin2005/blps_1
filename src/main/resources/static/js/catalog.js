
let cart = JSON.parse(localStorage.getItem('cart')) || [];


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


function createProductCard(product) {
    const card = document.createElement('div');
    card.className = 'product-card';
    card.dataset.sku = product.sku;

    const cartItem = cart.find(item => item.sku === product.sku);
    const currentQuantity = cartItem ? cartItem.quantity : 0;

    card.innerHTML = `
        <div class="product-image">📱</div>
        <div class="product-info">
            <h3>${product.name}</h3>
            <p class="product-price">${product.price.toLocaleString()} ₽</p>
            <p class="product-stock">В наличии: ${product.stockQuantity} шт.</p>

            <div class="product-quantity-control">
                <button class="quantity-btn decrease-btn" ${currentQuantity === 0 ? 'disabled' : ''}>−</button>
                <span class="quantity-display" id="quantity-${product.sku}">${currentQuantity}</span>
                <button class="quantity-btn increase-btn" ${product.stockQuantity <= currentQuantity ? 'disabled' : ''}>+</button>
            </div>

            <button class="btn btn-primary add-to-cart-btn"
                    data-sku="${product.sku}"
                    data-name="${product.name}"
                    data-price="${product.price}"
                    ${product.stockQuantity <= 0 ? 'disabled' : ''}>
                Добавить в корзину
            </button>
        </div>
    `;


    const decreaseBtn = card.querySelector('.decrease-btn');
    const increaseBtn = card.querySelector('.increase-btn');
    const quantityDisplay = card.querySelector('.quantity-display');
    const addBtn = card.querySelector('.add-to-cart-btn');

    decreaseBtn.addEventListener('click', () => {
        const currentQuantity = parseInt(quantityDisplay.textContent);
        if (currentQuantity > 0) {
            updateCartItemQuantity(product.sku, currentQuantity - 1);
            quantityDisplay.textContent = currentQuantity - 1;


            decreaseBtn.disabled = currentQuantity - 1 === 0;
            increaseBtn.disabled = product.stockQuantity <= currentQuantity - 1;
        }
    });

    increaseBtn.addEventListener('click', () => {
        const currentQuantity = parseInt(quantityDisplay.textContent);
        if (currentQuantity < product.stockQuantity) {
            updateCartItemQuantity(product.sku, currentQuantity + 1);
            quantityDisplay.textContent = currentQuantity + 1;


            decreaseBtn.disabled = false;
            increaseBtn.disabled = product.stockQuantity <= currentQuantity + 1;
        }
    });

    addBtn.addEventListener('click', () => {
        const quantity = parseInt(quantityDisplay.textContent);
        if (quantity > 0) {

            showNotification(`Товар добавлен в корзину (${quantity} шт.)`);
        } else {

            updateCartItemQuantity(product.sku, 1);
            quantityDisplay.textContent = 1;
            decreaseBtn.disabled = false;
            increaseBtn.disabled = product.stockQuantity <= 1;
            showNotification('Товар добавлен в корзину');
        }
    });

    return card;
}


function updateCartItemQuantity(sku, newQuantity) {
    const existingItemIndex = cart.findIndex(item => item.sku === sku);

    if (newQuantity === 0) {

        if (existingItemIndex !== -1) {
            cart.splice(existingItemIndex, 1);
        }
    } else {
        if (existingItemIndex !== -1) {

            cart[existingItemIndex].quantity = newQuantity;
        } else {

            const productCard = document.querySelector(`[data-sku="${sku}"]`);
            const addBtn = productCard?.querySelector('.add-to-cart-btn');
            const name = addBtn?.dataset.name || '';
            const price = parseFloat(addBtn?.dataset.price || 0);

            cart.push({
                sku,
                name,
                price,
                quantity: newQuantity
            });
        }
    }

    localStorage.setItem('cart', JSON.stringify(cart));
    updateCartDisplay();
}


function showNotification(message) {
    const notification = document.createElement('div');
    notification.className = 'notification';
    notification.textContent = message;

    document.body.appendChild(notification);

    setTimeout(() => {
        notification.remove();
    }, 2000);
}


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


        document.querySelectorAll('.quantity-display').forEach(display => {
            display.textContent = '0';
        });
        document.querySelectorAll('.decrease-btn').forEach(btn => {
            btn.disabled = true;
        });
        document.querySelectorAll('.increase-btn').forEach(btn => {
            btn.disabled = false;
        });

        return;
    }

    let total = 0;
    let itemsHtml = '<div class="cart-items-list">';

    cart.forEach((item, index) => {
        const itemTotal = item.price * item.quantity;
        total += itemTotal;

        itemsHtml += `
            <div class="cart-item" data-sku="${item.sku}">
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


        const quantityDisplay = document.getElementById(`quantity-${item.sku}`);
        if (quantityDisplay) {
            quantityDisplay.textContent = item.quantity;

            const card = quantityDisplay.closest('.product-card');
            if (card) {
                const decreaseBtn = card.querySelector('.decrease-btn');
                const increaseBtn = card.querySelector('.increase-btn');
                const productStock = parseInt(card.querySelector('.product-stock').textContent.match(/\d+/)[0]);

                decreaseBtn.disabled = item.quantity <= 0;
                increaseBtn.disabled = productStock <= item.quantity;
            }
        }
    });

    itemsHtml += '</div>';
    cartItems.innerHTML = itemsHtml;
    cartTotal.innerHTML = `<strong>Итого: ${total.toLocaleString()} ₽</strong>`;


    fetch('/profile', { method: 'HEAD' })
        .then(response => {
            if (response.ok) {
                if (checkoutBtn) {
                    checkoutBtn.style.display = 'inline-block';
                    checkoutBtn.href = '/checkout';
                }
                if (loginToCheckout) loginToCheckout.style.display = 'none';
            } else {
                if (checkoutBtn) checkoutBtn.style.display = 'none';
                if (loginToCheckout) {
                    loginToCheckout.style.display = 'inline-block';
                    loginToCheckout.href = '/login?redirect=checkout';
                }
            }
        })
        .catch(() => {
            if (checkoutBtn) checkoutBtn.style.display = 'none';
            if (loginToCheckout) {
                loginToCheckout.style.display = 'inline-block';
                loginToCheckout.href = '/login?redirect=checkout';
            }
        });


    localStorage.setItem('cartTotal', total);
}


function removeFromCart(index) {
    const removedItem = cart[index];
    cart.splice(index, 1);
    localStorage.setItem('cart', JSON.stringify(cart));


    if (removedItem) {
        const quantityDisplay = document.getElementById(`quantity-${removedItem.sku}`);
        if (quantityDisplay) {
            quantityDisplay.textContent = '0';

            const card = quantityDisplay.closest('.product-card');
            if (card) {
                const decreaseBtn = card.querySelector('.decrease-btn');
                const increaseBtn = card.querySelector('.increase-btn');
                decreaseBtn.disabled = true;
                increaseBtn.disabled = false;
            }
        }
    }

    updateCartDisplay();
}


document.addEventListener('DOMContentLoaded', () => {
    loadProducts();
    updateCartDisplay();
});


const style = document.createElement('style');
style.textContent = `
    .product-quantity-control {
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 1rem;
        margin: 1rem 0;
    }

    .quantity-btn {
        width: 36px;
        height: 36px;
        border: 2px solid #e30613;
        background: white;
        color: #e30613;
        font-size: 1.2rem;
        font-weight: bold;
        border-radius: 8px;
        cursor: pointer;
        transition: all 0.3s;
        display: flex;
        align-items: center;
        justify-content: center;
    }

    .quantity-btn:hover:not(:disabled) {
        background: #e30613;
        color: white;
        transform: scale(1.1);
    }

    .quantity-btn:disabled {
        border-color: #ccc;
        color: #ccc;
        cursor: not-allowed;
        opacity: 0.5;
    }

    .quantity-display {
        font-size: 1.2rem;
        font-weight: bold;
        min-width: 40px;
        text-align: center;
    }

    .add-to-cart-btn {
        width: 100%;
        margin-top: 0.5rem;
    }

    .add-to-cart-btn:disabled {
        background-color: #ccc;
        cursor: not-allowed;
        transform: none;
    }
`;
document.head.appendChild(style);
