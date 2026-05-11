package ru.sashil.rules;

import org.springframework.stereotype.Component;
import java.util.Map;

@Component("blockActionHandler")
public class BlockActionHandler implements RuleActionHandler {
    @Override
    public void execute(RuleContext context, Map<String, Object> params) {
        context.setBlocked(true);
        context.setBlockReason(params != null ? (String) params.getOrDefault("reason", "Order blocked by rules") : "Order blocked");
    }
}
