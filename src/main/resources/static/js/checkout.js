document.addEventListener('DOMContentLoaded', function() {
    const cart = JSON.parse(localStorage.getItem('cart')) || [];
    const itemsList = document.getElementById('checkout-items-list');
    const totalEl = document.getElementById('checkout-total');
    const dataContainer = document.getElementById('cart-data-container');

    if (cart.length === 0 && !window.location.search.includes('clearCart=true')) {
        window.location.href = '/cart';
        return;
    }

    // Проверка на ошибку остатков
    const urlParams = new URLSearchParams(window.location.search);
    const errorMsg = document.getElementById('error-splash');
    
    if (errorMsg && errorMsg.textContent.includes('Not enough stock')) {
        handleStockError(errorMsg.textContent);
    }

    let total = 0;
    itemsList.innerHTML = cart.map((item, index) => {
        total += item.price * item.quantity;
        
        // Add hidden inputs for form submission
        dataContainer.innerHTML += `
            <input type="hidden" name="productId[]" value="${item.sku}">
            <input type="hidden" name="productName[]" value="${item.name}">
            <input type="hidden" name="quantity[]" value="${item.quantity}">
            <input type="hidden" name="price[]" value="${item.price}">
        `;

        return `
            <div style="display: flex; justify-content: space-between; padding: 0.75rem 0; border-bottom: 1px solid rgba(255,255,255,0.05);">
                <div style="flex: 1;">
                    <div style="font-weight: 600; font-size: 0.95rem;">${item.name}</div>
                    <small style="color: var(--text-dim);">${item.quantity} шт. × ${item.price.toLocaleString()} ₽</small>
                </div>
                <div style="font-weight: 700;">${(item.price * item.quantity).toLocaleString()} ₽</div>
            </div>
        `;
    }).join('');

    totalEl.textContent = total.toLocaleString() + ' ₽';

    function handleStockError(message) {
        // Парсим название товара из ошибки "Not enough stock for product: 📱 iPhone 15 Pro"
        const productName = message.split(':').pop().trim();
        
        // Показываем уведомление
        showSplash(`Упс, ${productName} заканчивается. Мы изменили количество товара в корзине на доступное.`);
        
        // Запрашиваем актуальные остатки и правим корзину
        fetch('/api/products')
            .then(res => res.json())
            .then(products => {
                const product = products.find(p => p.name.includes(productName) || productName.includes(p.name));
                if (product) {
                    let updatedCart = JSON.parse(localStorage.getItem('cart')) || [];
                    updatedCart = updatedCart.map(item => {
                        if (item.sku === product.sku) {
                            item.quantity = product.stockQuantity;
                        }
                        return item;
                    }).filter(item => item.quantity > 0);
                    
                    localStorage.setItem('cart', JSON.stringify(updatedCart));
                    // Перерисовывать не нужно, так как уведомление висит, пользователь увидит изменения после авто-рефреша или ручного
                    setTimeout(() => window.location.href = '/checkout', 4000);
                }
            });
    }

    function showSplash(text) {
        const toast = document.createElement('div');
        toast.className = 'notification-toast';
        toast.textContent = text;
        document.body.appendChild(toast);
        setTimeout(() => toast.remove(), 5000);
    }
});
