#!/bin/bash
# Скрипт для тестирования ключевых точек API (Keypoint Testing)

BASE_URL="https://gitea.timoapp.tech/api"
USER="testuser_api_$(date +%s)"
PASS="password123"

echo "1. Регистрация нового пользователя..."
REG_RES=$(curl -s -X POST "$BASE_URL/users/register" \
  -H "Content-Type: application/json" \
  -d "{
    \"username\": \"$USER\",
    \"email\": \"$USER@example.com\",
    \"password\": \"$PASS\",
    \"confirmPassword\": \"$PASS\",
    \"fullName\": \"API Test User\"
  }")

if [[ $REG_RES == *"id"* ]]; then
  echo "Успех: Пользователь зарегистрирован."
else
  echo "Ошибка: Регистрация провалена. $REG_RES"
  exit 1
fi

echo "2. Проверка доступа к списку заказов (Basic Auth)..."
# Сначала без авторизации (должно быть 401)
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/orders/customer")
if [ "$STATUS" -eq 401 ]; then
  echo "Успех: Доступ без авторизации запрещен (401)."
else
  echo "Ошибка: Неверный статус ответа без авторизации: $STATUS"
  exit 1
fi

# Теперь с авторизацией
AUTH_HEADER=$(echo -n "$USER:$PASS" | base64)
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/orders/customer" \
  -H "Authorization: Basic $AUTH_HEADER")

if [ "$STATUS" -eq 200 ]; then
  echo "Успех: Авторизация прошла успешно (200)."
else
  echo "Ошибка: Авторизация провалена: $STATUS"
  exit 1
fi

echo "3. Попытка создания заказа..."
# Используем существующий товар из DataInitializer (IPHONE-14-PRO)
ORDER_RES=$(curl -s -X POST "$BASE_URL/orders/create" \
  -H "Authorization: Basic $AUTH_HEADER" \
  -d "customerName=API Test User" \
  -d "customerEmail=$USER@example.com" \
  -d "customerPhone=123456" \
  -d "deliveryType=COURIER" \
  -d "deliveryAddress=Test Str 1" \
  -d "paymentMethod=card" \
  -d "items[0].productId=IPHONE-14-PRO" \
  -d "items[0].productName=iPhone 14 Pro" \
  -d "items[0].quantity=1" \
  -d "items[0].price=99999.99")

if [[ $ORDER_RES == *"orderNumber"* ]]; then
  ORDER_NUM=$(echo $ORDER_RES | grep -oP '"orderNumber":"\K[^"]+')
  echo "Успех: Заказ создан. Номер заказа: $ORDER_NUM"
else
  echo "Ошибка: Создание заказа провалено. $ORDER_RES"
  exit 1
fi

echo "Все тесты ключевых точек API пройдены успешно."
