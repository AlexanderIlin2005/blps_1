package ru.sashil.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;

@Service
public class RuleEngine {
    private static final Logger log = LoggerFactory.getLogger(RuleEngine.class);

    @Autowired
    private ApplicationContext context;

    @Autowired
    private RuleVersionService versionService;

    private final ObjectMapper mapper = new ObjectMapper();
    private final ExpressionParser parser = new SpelExpressionParser();

    private List<Rule> currentRules = new ArrayList<>();

    @PostConstruct
    public void init() {
        loadRules(versionService.getActiveVersion());
    }

    public void reload() {
        loadRules(versionService.getActiveVersion());
    }

    private void loadRules(String version) {
        try {
            String pattern = "classpath:rules/" + version + "/*.json";
            Resource[] resources = ((ResourcePatternResolver) context).getResources(pattern);
            List<Rule> rules = new ArrayList<>();
            for (Resource res : resources) {
                Rule rule = mapper.readValue(res.getInputStream(), Rule.class);
                rules.add(rule);
            }
            rules.sort(Comparator.comparingInt(Rule::getPriority));
            currentRules = rules;
            log.info("Loaded {} rules from version {}", rules.size(), version);
        } catch (Exception e) {
            log.error("Failed to load rules for version {}", version, e);
            currentRules = Collections.emptyList();
        }
    }

    public void evaluate(RuleContext ctx) {
        log.info("Evaluating rules against context for user {}", ctx.getUser().getUsername());
        StandardEvaluationContext spelCtx = new StandardEvaluationContext(ctx);
        // Allow direct access to context properties for SpEL
        spelCtx.setVariable("order", ctx.getOrderRequest());
        spelCtx.setVariable("user", ctx.getUser());

        for (Rule rule : currentRules) {
            log.info("Evaluating rule '{}' (priority {})", rule.getName(), rule.getPriority());
            try {
                Boolean result = parser.parseExpression(rule.getCondition()).getValue(spelCtx, Boolean.class);
                if (Boolean.TRUE.equals(result)) {
                    log.info("Rule '{}' triggered, applying action", rule.getName());
                    applyAction(ctx, rule.getAction());
                    if (ctx.isBlocked()) {
                        log.warn("Order blocked by rule '{}': {}", rule.getName(), ctx.getBlockReason());
                        break; // stop processing further rules
                    }
                } else {
                    log.info("Rule '{}' condition not met", rule.getName());
                }
            } catch (Exception e) {
                log.error("Error evaluating rule '{}': {}", rule.getName(), e.getMessage());
            }
        }
    }

    private void applyAction(RuleContext ctx, Map<String, Object> action) {
        if (action == null) return;
        String handlerName = (String) action.get("handler");
        Map<String, Object> params = (Map<String, Object>) action.get("params");
        if (handlerName != null) {
            RuleActionHandler handler = context.getBean(handlerName, RuleActionHandler.class);
            handler.execute(ctx, params);
            log.info("Action executed: handler={}, params={}", handlerName, params);
        }
    }

    public List<Rule> getCurrentRules() {
        return currentRules;
    }
}
