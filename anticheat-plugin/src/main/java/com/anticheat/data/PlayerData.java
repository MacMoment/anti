package com.anticheat.data;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class PlayerData {

    private static final int MAX_MOVEMENT_HISTORY = 200;
    private static final int MAX_CLICK_HISTORY = 200;

    private final UUID playerUUID;
    private final String playerName;

    private final Deque<MovementFrame> movementHistory = new ArrayDeque<>();
    private final List<Long> clickTimestamps = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, Float> violationLevels = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastFlagTime = new ConcurrentHashMap<>();

    private volatile double lastX;
    private volatile double lastY;
    private volatile double lastZ;

    private final long sessionStart;

    private volatile boolean isMitigated = false;
    private final List<String> activeMitigations = new CopyOnWriteArrayList<>();

    private volatile long lastCloudSync = 0;

    private volatile boolean receivedVelocity = false;
    private volatile double pendingVelocityX = 0;
    private volatile double pendingVelocityY = 0;
    private volatile double pendingVelocityZ = 0;
    private volatile int ticksInAir = 0;
    private volatile boolean isFlying = false;

    // Track packet timestamps for timer check
    private final List<Long> packetTimestamps = new CopyOnWriteArrayList<>();

    // Track item use start time for FastUse check
    private volatile long itemUseStartTime = -1;
    private volatile boolean isBlocking = false;
    private volatile boolean isEating = false;

    // Track velocity tick counter (how many ticks since receiving velocity)
    private volatile int velocityTicksLeft = 0;

    public PlayerData(UUID playerUUID, String playerName) {
        this.playerUUID = playerUUID;
        this.playerName = playerName;
        this.sessionStart = System.currentTimeMillis();
    }

    public synchronized void addMovementFrame(MovementFrame frame) {
        if (frame == null) return;
        movementHistory.addLast(frame);
        while (movementHistory.size() > MAX_MOVEMENT_HISTORY) {
            movementHistory.pollFirst();
        }
    }

    public synchronized MovementFrame getLastMovementFrame() {
        return movementHistory.peekLast();
    }

    public synchronized MovementFrame getPreviousMovementFrame() {
        if (movementHistory.size() < 2) return null;
        MovementFrame last = movementHistory.pollLast();
        MovementFrame prev = movementHistory.peekLast();
        movementHistory.addLast(last);
        return prev;
    }

    public synchronized List<MovementFrame> getMovementHistory(int count) {
        List<MovementFrame> result = new java.util.ArrayList<>();
        MovementFrame[] arr = movementHistory.toArray(new MovementFrame[0]);
        int start = Math.max(0, arr.length - count);
        for (int i = start; i < arr.length; i++) {
            result.add(arr[i]);
        }
        return result;
    }

    public void addClick(long timestamp) {
        clickTimestamps.add(timestamp);
        if (clickTimestamps.size() > MAX_CLICK_HISTORY) {
            clickTimestamps.remove(0);
        }
    }

    public void addPacketTimestamp(long timestamp) {
        packetTimestamps.add(timestamp);
        // Keep only last 2 seconds worth
        long cutoff = timestamp - 2000;
        packetTimestamps.removeIf(t -> t < cutoff);
    }

    public float getViolationLevel(String checkName) {
        return violationLevels.getOrDefault(checkName, 0.0f);
    }

    public float addViolation(String checkName, float amount) {
        float current = violationLevels.getOrDefault(checkName, 0.0f);
        float newVl = current + amount;
        violationLevels.put(checkName, newVl);
        lastFlagTime.put(checkName, System.currentTimeMillis());
        return newVl;
    }

    public void decayViolation(String checkName, float decayRate) {
        violationLevels.computeIfPresent(checkName, (k, v) -> {
            float decayed = v - decayRate;
            return decayed <= 0 ? null : decayed;
        });
    }

    public void setLastValidPosition(double x, double y, double z) {
        this.lastX = x;
        this.lastY = y;
        this.lastZ = z;
    }

    public void recordVelocity(double vx, double vy, double vz) {
        this.pendingVelocityX = vx;
        this.pendingVelocityY = vy;
        this.pendingVelocityZ = vz;
        this.receivedVelocity = true;
        this.velocityTicksLeft = 3;
    }

    public void tickVelocity() {
        if (velocityTicksLeft > 0) {
            velocityTicksLeft--;
            if (velocityTicksLeft == 0) {
                receivedVelocity = false;
            }
        }
    }

    // Getters and setters
    public UUID getPlayerUUID() { return playerUUID; }
    public String getPlayerName() { return playerName; }
    public List<Long> getClickTimestamps() { return clickTimestamps; }
    public List<Long> getPacketTimestamps() { return packetTimestamps; }
    public ConcurrentHashMap<String, Float> getViolationLevels() { return violationLevels; }
    public ConcurrentHashMap<String, Long> getLastFlagTime() { return lastFlagTime; }
    public double getLastX() { return lastX; }
    public double getLastY() { return lastY; }
    public double getLastZ() { return lastZ; }
    public long getSessionStart() { return sessionStart; }
    public boolean isMitigated() { return isMitigated; }
    public void setMitigated(boolean mitigated) { this.isMitigated = mitigated; }
    public List<String> getActiveMitigations() { return activeMitigations; }
    public long getLastCloudSync() { return lastCloudSync; }
    public void setLastCloudSync(long lastCloudSync) { this.lastCloudSync = lastCloudSync; }
    public boolean isReceivedVelocity() { return receivedVelocity; }
    public void setReceivedVelocity(boolean receivedVelocity) { this.receivedVelocity = receivedVelocity; }
    public double getPendingVelocityX() { return pendingVelocityX; }
    public double getPendingVelocityY() { return pendingVelocityY; }
    public double getPendingVelocityZ() { return pendingVelocityZ; }
    public int getTicksInAir() { return ticksInAir; }
    public void setTicksInAir(int ticksInAir) { this.ticksInAir = ticksInAir; }
    public boolean isFlying() { return isFlying; }
    public void setFlying(boolean flying) { this.isFlying = flying; }
    public long getItemUseStartTime() { return itemUseStartTime; }
    public void setItemUseStartTime(long itemUseStartTime) { this.itemUseStartTime = itemUseStartTime; }
    public boolean isBlocking() { return isBlocking; }
    public void setBlocking(boolean blocking) { this.isBlocking = blocking; }
    public boolean isEating() { return isEating; }
    public void setEating(boolean eating) { this.isEating = eating; }
    public int getVelocityTicksLeft() { return velocityTicksLeft; }
    public void setVelocityTicksLeft(int ticks) { this.velocityTicksLeft = ticks; }
}
