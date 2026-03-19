package com.anticheat.mitigation;

public enum MitigationAction {

    MONITOR("Monitor only"),
    RUBBERBAND("Teleport back"),
    LIMIT_REACH("Limit reach"),
    LIMIT_CPS("Limit CPS"),
    SLOW_MOVEMENT("Slow movement"),
    FLAG_FOR_BAN("Flag for ban review");

    private final String description;

    MitigationAction(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public static MitigationAction fromString(String name) {
        for (MitigationAction action : values()) {
            if (action.name().equalsIgnoreCase(name)) return action;
        }
        return MONITOR;
    }
}
