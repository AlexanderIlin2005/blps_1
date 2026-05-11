package ru.sashil.rules;

import java.util.Map;

public interface RuleActionHandler {
    void execute(RuleContext context, Map<String, Object> params);
}
