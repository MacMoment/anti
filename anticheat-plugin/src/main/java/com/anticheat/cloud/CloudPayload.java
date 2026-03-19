package com.anticheat.cloud;

import com.anticheat.data.MovementFrame;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CloudPayload {

    private String playerUUID;
    private String playerName;
    private String serverID;
    private List<MovementSample> movementSamples;
    private List<CombatSample> combatSamples;
    private List<Long> clickTimestamps;
    private Map<String, Float> violationHistory;
    private long sessionDuration;
    private long timestamp;

    private CloudPayload() {}

    public static Builder builder() { return new Builder(); }

    // Getters
    public String getPlayerUUID() { return playerUUID; }
    public String getPlayerName() { return playerName; }
    public String getServerID() { return serverID; }
    public List<MovementSample> getMovementSamples() { return movementSamples; }
    public List<CombatSample> getCombatSamples() { return combatSamples; }
    public List<Long> getClickTimestamps() { return clickTimestamps; }
    public Map<String, Float> getViolationHistory() { return violationHistory; }
    public long getSessionDuration() { return sessionDuration; }
    public long getTimestamp() { return timestamp; }

    public static class MovementSample {
        public double x, y, z;
        public float yaw, pitch;
        public boolean onGround;
        public double velocityX, velocityY, velocityZ;
        public long timestamp;

        public static MovementSample from(MovementFrame frame) {
            MovementSample s = new MovementSample();
            s.x = frame.getX();
            s.y = frame.getY();
            s.z = frame.getZ();
            s.yaw = frame.getYaw();
            s.pitch = frame.getPitch();
            s.onGround = frame.isOnGround();
            s.velocityX = frame.getVelocityX();
            s.velocityY = frame.getVelocityY();
            s.velocityZ = frame.getVelocityZ();
            s.timestamp = frame.getTimestamp();
            return s;
        }
    }

    public static class CombatSample {
        public String targetUUID;
        public double distance;
        public float cps;
        public long timestamp;
    }

    public static class Builder {
        private final CloudPayload payload = new CloudPayload();

        public Builder() {
            payload.movementSamples = new ArrayList<>();
            payload.combatSamples = new ArrayList<>();
            payload.clickTimestamps = new ArrayList<>();
            payload.violationHistory = new HashMap<>();
        }

        public Builder playerUUID(String uuid) { payload.playerUUID = uuid; return this; }
        public Builder playerName(String name) { payload.playerName = name; return this; }
        public Builder serverID(String id) { payload.serverID = id; return this; }
        public Builder movementSamples(List<MovementSample> samples) { payload.movementSamples = samples; return this; }
        public Builder combatSamples(List<CombatSample> samples) { payload.combatSamples = samples; return this; }
        public Builder clickTimestamps(List<Long> ts) { payload.clickTimestamps = ts; return this; }
        public Builder violationHistory(Map<String, Float> history) { payload.violationHistory = history; return this; }
        public Builder sessionDuration(long duration) { payload.sessionDuration = duration; return this; }
        public Builder timestamp(long ts) { payload.timestamp = ts; return this; }

        public CloudPayload build() {
            payload.timestamp = System.currentTimeMillis();
            return payload;
        }
    }
}
