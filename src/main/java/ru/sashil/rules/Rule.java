package ru.sashil.rules;

import java.util.Map;

public class Rule {
    private String name;
    private String description;
    private int priority;
    private String condition; // SpEL expression
    private Map<String, Object> action; // action name and parameters

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }
    public Map<String, Object> getAction() { return action; }
    public void setAction(Map<String, Object> action) { this.action = action; }
}
