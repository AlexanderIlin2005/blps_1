# Business Rules Decision Table

| Rule Name | Priority | Condition (SpEL) | Action Handler | Description | Scenario |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **AntiFraud Check** | 1 | `redisTemplate.opsForValue().get('antifraud:user:' + user.getId() + ':orderCount') >= 5` | `blockActionHandler` | Blocks if > 5 orders in 1h | **Antifraud** |
| **Order Limit** | 2 | `!user.roles.stream().anyMatch(r -> r.name == 'ROLE_VIP') && order.items.stream().mapToDouble(i -> i.price * i.quantity).sum() > 10000` | `blockActionHandler` | Max 10k RUB for non-VIPs | **Dynamic Limits** |
| **VIP Discount** | 5 | `user.roles.stream().anyMatch(r -> r.name == 'ROLE_VIP')` | `discountActionHandler` | 10% discount for VIP users | **Personalized Tariffs** |
| **Flash Sale** | 6 | `T(java.time.LocalTime).now().getHour() >= 18 && T(java.time.LocalTime).now().getHour() < 20 && order.items.stream().anyMatch(i -> i.productId == 'FLASH')` | `discountActionHandler` | 15% discount for SKU 'FLASH' (18:00-20:00) | **Temporary Promotions** |
| **Volume Discount** | 10 | `order.items.stream().mapToDouble(i -> i.price * i.quantity).sum() >= 100000` | `discountActionHandler` | 5% discount for orders > 100k | - |
| **Free Shipping** | 11 | `order.items.stream().mapToDouble(i -> i.price * i.quantity).sum() >= 50000` | `discountActionHandler` | Free shipping for orders > 50k | - |

## Rule Engine Features:
- **Versioning**: Supports multiple versions (v1, v2) loaded from classpath. Active version stored in Redis.
- **REST Management**: `/api/rules/version` to switch versions dynamically.
- **Audit**: Every rule execution is logged with "triggered" or "condition not met" status.
- **Extensibility**: New rules can be added as JSON files without code changes. New actions can be added by implementing `RuleActionHandler`.
