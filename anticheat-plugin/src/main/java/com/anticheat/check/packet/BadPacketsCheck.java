package com.anticheat.check.packet;

import com.anticheat.AnticheatPlugin;
import com.anticheat.check.Check;
import com.anticheat.check.CheckResult;
import com.anticheat.data.MovementFrame;
import com.anticheat.data.PlayerData;
import org.bukkit.entity.Player;

public class BadPacketsCheck extends Check {

    private static final double MAX_TELEPORT_DISTANCE = 10.0;
    private static final double MAX_Y = 320.0;
    private static final double MIN_Y = -64.0;

    public BadPacketsCheck(AnticheatPlugin plugin) {
        super(plugin, "BadPackets", "packet", 10.0f, 0.1f);
    }

    @Override
    public CheckResult check(PlayerData data, Object context) {
        if (!(context instanceof MovementFrame current)) return CheckResult.exempt();

        Player player = plugin.getServer().getPlayer(data.getPlayerUUID());
        if (player == null) return CheckResult.exempt();

        // Check for NaN or Infinity in position
        if (!Double.isFinite(current.getX()) || !Double.isFinite(current.getY()) || !Double.isFinite(current.getZ())) {
            return CheckResult.flag(10.0f, "NaN/Infinite position packet");
        }

        if (!Float.isFinite(current.getYaw()) || !Float.isFinite(current.getPitch())) {
            return CheckResult.flag(10.0f, "NaN/Infinite rotation packet");
        }

        // Check world height bounds
        if (current.getY() > MAX_Y || current.getY() < MIN_Y) {
            return CheckResult.flag(5.0f,
                String.format("out of bounds Y=%.4f (range %.0f to %.0f)",
                    current.getY(), MIN_Y, MAX_Y));
        }

        // Check teleport distance (movement > 10 blocks in 1 tick without server teleport)
        MovementFrame previous = data.getPreviousMovementFrame();
        if (previous != null) {
            double dist = current.getHorizontalDistance(previous);
            double vertDist = Math.abs(current.getVerticalDistance(previous));

            if (dist > MAX_TELEPORT_DISTANCE || vertDist > MAX_TELEPORT_DISTANCE) {
                // Could be a server teleport — only flag if the velocity doesn't account for it
                if (!data.isReceivedVelocity()) {
                    return CheckResult.flag(5.0f,
                        String.format("teleport hack dist=%.4f vertDist=%.4f", dist, vertDist));
                }
            }
        }

        return CheckResult.pass();
    }
}
