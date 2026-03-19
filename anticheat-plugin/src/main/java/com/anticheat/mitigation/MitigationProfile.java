package com.anticheat.mitigation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MitigationProfile {

    private final Map<String, MitigationAction> activeActions = new ConcurrentHashMap<>();
    private volatile long lastMitigationTime = 0;
    private volatile int mitigationCount = 0;
    private volatile boolean isFlagged = false;

    public void setAction(String checkName, MitigationAction action) {
        activeActions.put(checkName, action);
        lastMitigationTime = System.currentTimeMillis();
        mitigationCount++;
    }

    public MitigationAction getAction(String checkName) {
        return activeActions.get(checkName);
    }

    public void clearAction(String checkName) {
        activeActions.remove(checkName);
    }

    public Map<String, MitigationAction> getActiveActions() { return activeActions; }
    public long getLastMitigationTime() { return lastMitigationTime; }
    public int getMitigationCount() { return mitigationCount; }
    public boolean isFlagged() { return isFlagged; }
    public void setFlagged(boolean flagged) { this.isFlagged = flagged; }
}
