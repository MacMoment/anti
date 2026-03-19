package com.anticheat.data;

public final class MovementFrame {

    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private final boolean onGround;
    private final boolean sprinting;
    private final boolean sneaking;
    private final long timestamp;
    private final double velocityX;
    private final double velocityY;
    private final double velocityZ;

    private MovementFrame(Builder builder) {
        this.x = builder.x;
        this.y = builder.y;
        this.z = builder.z;
        this.yaw = builder.yaw;
        this.pitch = builder.pitch;
        this.onGround = builder.onGround;
        this.sprinting = builder.sprinting;
        this.sneaking = builder.sneaking;
        this.timestamp = builder.timestamp;
        this.velocityX = builder.velocityX;
        this.velocityY = builder.velocityY;
        this.velocityZ = builder.velocityZ;
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public boolean isOnGround() { return onGround; }
    public boolean isSprinting() { return sprinting; }
    public boolean isSneaking() { return sneaking; }
    public long getTimestamp() { return timestamp; }
    public double getVelocityX() { return velocityX; }
    public double getVelocityY() { return velocityY; }
    public double getVelocityZ() { return velocityZ; }

    public double getHorizontalDistance(MovementFrame other) {
        double dx = this.x - other.x;
        double dz = this.z - other.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public double getVerticalDistance(MovementFrame other) {
        return this.y - other.y;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private double x;
        private double y;
        private double z;
        private float yaw;
        private float pitch;
        private boolean onGround;
        private boolean sprinting;
        private boolean sneaking;
        private long timestamp = System.currentTimeMillis();
        private double velocityX;
        private double velocityY;
        private double velocityZ;

        public Builder x(double x) { this.x = x; return this; }
        public Builder y(double y) { this.y = y; return this; }
        public Builder z(double z) { this.z = z; return this; }
        public Builder yaw(float yaw) { this.yaw = yaw; return this; }
        public Builder pitch(float pitch) { this.pitch = pitch; return this; }
        public Builder onGround(boolean onGround) { this.onGround = onGround; return this; }
        public Builder sprinting(boolean sprinting) { this.sprinting = sprinting; return this; }
        public Builder sneaking(boolean sneaking) { this.sneaking = sneaking; return this; }
        public Builder timestamp(long timestamp) { this.timestamp = timestamp; return this; }
        public Builder velocityX(double velocityX) { this.velocityX = velocityX; return this; }
        public Builder velocityY(double velocityY) { this.velocityY = velocityY; return this; }
        public Builder velocityZ(double velocityZ) { this.velocityZ = velocityZ; return this; }

        public MovementFrame build() {
            return new MovementFrame(this);
        }
    }
}
