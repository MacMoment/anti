package com.anticheat.check;

public class CheckResult {

    public enum Type {
        PASS,
        FLAG,
        EXEMPT
    }

    private final Type type;
    private final float severity;
    private final String description;

    private CheckResult(Type type, float severity, String description) {
        this.type = type;
        this.severity = severity;
        this.description = description;
    }

    public static CheckResult pass() {
        return new CheckResult(Type.PASS, 0f, "");
    }

    public static CheckResult flag(float severity, String description) {
        return new CheckResult(Type.FLAG, severity, description);
    }

    public static CheckResult exempt() {
        return new CheckResult(Type.EXEMPT, 0f, "exempt");
    }

    public Type getType() { return type; }
    public float getSeverity() { return severity; }
    public String getDescription() { return description; }

    public boolean isFlagged() { return type == Type.FLAG; }
    public boolean isPass() { return type == Type.PASS; }
    public boolean isExempt() { return type == Type.EXEMPT; }
}
