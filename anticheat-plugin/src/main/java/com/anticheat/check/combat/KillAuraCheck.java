package com.anticheat.check.combat;

import com.anticheat.AnticheatPlugin;
import com.anticheat.check.Check;
import com.anticheat.check.CheckResult;
import com.anticheat.data.MovementFrame;
import com.anticheat.data.PlayerData;
import com.anticheat.util.MathUtil;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.List;

public class KillAuraCheck extends Check {

    private static final double ROTATION_LOCK_TOLERANCE = 0.1;

    public KillAuraCheck(AnticheatPlugin plugin) {
        super(plugin, "KillAura", "combat", 15.0f, 0.1f);
    }

    @Override
    public CheckResult check(PlayerData data, Object context) {
        if (!(context instanceof Entity target)) return CheckResult.exempt();

        Player player = plugin.getServer().getPlayer(data.getPlayerUUID());
        if (player == null) return CheckResult.exempt();

        List<MovementFrame> history = data.getMovementHistory(5);
        if (history.size() < 2) return CheckResult.pass();

        // Check: hitting behind (angle between player facing and target direction)
        MovementFrame latest = history.get(history.size() - 1);
        double[] playerDir = yawPitchToVector(latest.getYaw(), latest.getPitch());
        double[] toTarget = getDirectionToTarget(player, target);
        double angle = MathUtil.angleBetween(
            playerDir[0], playerDir[1], playerDir[2],
            toTarget[0], toTarget[1], toTarget[2]
        );

        if (angle > 110.0) {
            return CheckResult.flag(3.0f,
                String.format("hit behind angle=%.2f degrees", angle));
        }

        // Check: perfect rotation lock - player facing exactly at target every tick
        if (history.size() >= 4) {
            double[] rotDeltas = new double[history.size() - 1];
            boolean allSmall = true;
            for (int i = 1; i < history.size(); i++) {
                MovementFrame prev = history.get(i - 1);
                MovementFrame curr = history.get(i);
                double yawDelta = Math.abs(curr.getYaw() - prev.getYaw());
                double pitchDelta = Math.abs(curr.getPitch() - prev.getPitch());
                rotDeltas[i - 1] = yawDelta + pitchDelta;
                if (yawDelta > 1.0 || pitchDelta > 1.0) allSmall = false;
            }

            if (allSmall) {
                // Rotation barely changed over 4+ frames while attacking — possible rotation lock
                double variance = MathUtil.standardDeviation(rotDeltas);
                if (variance < ROTATION_LOCK_TOLERANCE) {
                    return CheckResult.flag(2.0f,
                        String.format("rotation lock variance=%.6f", variance));
                }
            }
        }

        return CheckResult.pass();
    }

    private double[] yawPitchToVector(float yaw, float pitch) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double x = -Math.sin(yawRad) * Math.cos(pitchRad);
        double y = -Math.sin(pitchRad);
        double z = Math.cos(yawRad) * Math.cos(pitchRad);
        return new double[]{x, y, z};
    }

    private double[] getDirectionToTarget(Player player, Entity target) {
        double dx = target.getLocation().getX() - player.getEyeLocation().getX();
        double dy = target.getLocation().getY() + target.getHeight() / 2.0 - player.getEyeLocation().getY();
        double dz = target.getLocation().getZ() - player.getEyeLocation().getZ();
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len == 0) return new double[]{0, 0, 1};
        return new double[]{dx / len, dy / len, dz / len};
    }
}
