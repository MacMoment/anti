package com.anticheat.util;

import com.anticheat.data.MovementFrame;
import com.anticheat.data.PlayerData;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.entity.Player;

public class PacketUtil {

    public static double safeGetDouble(PacketContainer packet, int index, double defaultVal) {
        try {
            return packet.getDoubles().read(index);
        } catch (Exception e) {
            return defaultVal;
        }
    }

    public static float safeGetFloat(PacketContainer packet, int index, float defaultVal) {
        try {
            return packet.getFloat().read(index);
        } catch (Exception e) {
            return defaultVal;
        }
    }

    public static boolean safeGetBoolean(PacketContainer packet, int index, boolean defaultVal) {
        try {
            return packet.getBooleans().read(index);
        } catch (Exception e) {
            return defaultVal;
        }
    }

    /**
     * Parse a movement-related packet into a MovementFrame.
     * Handles FLYING, POSITION, POSITION_LOOK, and LOOK packet types.
     */
    public static MovementFrame parseMovementPacket(PacketEvent event, PlayerData data) {
        PacketContainer packet = event.getPacket();
        PacketType type = event.getPacketType();
        Player player = event.getPlayer();

        if (player == null) return null;

        // Start with previous position as defaults
        MovementFrame last = data.getLastMovementFrame();
        double prevX = (last != null) ? last.getX() : player.getLocation().getX();
        double prevY = (last != null) ? last.getY() : player.getLocation().getY();
        double prevZ = (last != null) ? last.getZ() : player.getLocation().getZ();
        float prevYaw = (last != null) ? last.getYaw() : player.getLocation().getYaw();
        float prevPitch = (last != null) ? last.getPitch() : player.getLocation().getPitch();
        boolean prevOnGround = (last != null) ? last.isOnGround() : player.isOnGround();

        double x = prevX, y = prevY, z = prevZ;
        float yaw = prevYaw, pitch = prevPitch;
        boolean onGround = prevOnGround;

        try {
            if (type == PacketType.Play.Client.POSITION) {
                x = safeGetDouble(packet, 0, prevX);
                y = safeGetDouble(packet, 1, prevY);
                z = safeGetDouble(packet, 2, prevZ);
                onGround = safeGetBoolean(packet, 0, prevOnGround);
            } else if (type == PacketType.Play.Client.POSITION_LOOK) {
                x = safeGetDouble(packet, 0, prevX);
                y = safeGetDouble(packet, 1, prevY);
                z = safeGetDouble(packet, 2, prevZ);
                yaw = safeGetFloat(packet, 0, prevYaw);
                pitch = safeGetFloat(packet, 1, prevPitch);
                onGround = safeGetBoolean(packet, 0, prevOnGround);
            } else if (type == PacketType.Play.Client.LOOK) {
                yaw = safeGetFloat(packet, 0, prevYaw);
                pitch = safeGetFloat(packet, 1, prevPitch);
                onGround = safeGetBoolean(packet, 0, prevOnGround);
            } else if (type == PacketType.Play.Client.FLYING) {
                onGround = safeGetBoolean(packet, 0, prevOnGround);
            }
        } catch (Exception e) {
            // If parsing fails, return null to skip the frame
            return null;
        }

        // Validate values
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return null;
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) return null;

        // Compute velocity from position delta
        double velX = x - prevX;
        double velY = y - prevY;
        double velZ = z - prevZ;

        return MovementFrame.builder()
            .x(x).y(y).z(z)
            .yaw(yaw).pitch(pitch)
            .onGround(onGround)
            .sprinting(player.isSprinting())
            .sneaking(player.isSneaking())
            .timestamp(System.currentTimeMillis())
            .velocityX(velX)
            .velocityY(velY)
            .velocityZ(velZ)
            .build();
    }

    public static boolean isMovementPacket(PacketType type) {
        return type == PacketType.Play.Client.FLYING
            || type == PacketType.Play.Client.POSITION
            || type == PacketType.Play.Client.POSITION_LOOK
            || type == PacketType.Play.Client.LOOK;
    }
}
