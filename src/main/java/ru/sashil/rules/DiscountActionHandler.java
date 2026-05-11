package ru.sashil.rules;

import org.springframework.stereotype.Component;
import java.util.Map;

@Component("discountActionHandler")
public class DiscountActionHandler implements RuleActionHandler {
    @Override
    public void execute(RuleContext context, Map<String, Object> params) {
        if (params != null && params.containsKey("percent")) {
            double percent = ((Number) params.get("percent")).doubleValue();
            context.setDiscountPercent(context.getDiscountPercent() + percent);
        }
        if (params != null && Boolean.TRUE.equals(params.get("freeShipping"))) {
            context.setFreeShipping(true);
        }
    }
}
