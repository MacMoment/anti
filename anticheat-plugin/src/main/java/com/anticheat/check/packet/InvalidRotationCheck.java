package com.anticheat.check.packet;

import com.anticheat.AnticheatPlugin;
import com.anticheat.check.Check;
import com.anticheat.check.CheckResult;
import com.anticheat.data.MovementFrame;
import com.anticheat.data.PlayerData;
import org.bukkit.entity.Player;

import java.util.List;

public class InvalidRotationCheck extends Check {

    private static final float MIN_PITCH = -90.0f;
    private static final float MAX_PITCH = 90.0f;
    private static final double ROTATION_LOCK_SAMPLES = 10;
    private static final double LOCK_VARIANCE_THRESHOLD = 1e-6;

    public InvalidRotationCheck(AnticheatPlugin plugin) {
        super(plugin, "InvalidRotation", "packet", 5.0f, 0.5f);
    }

    @Override
    public CheckResult check(PlayerData data, Object context) {
        if (!(context instanceof MovementFrame current)) return CheckResult.exempt();

        Player player = plugin.getServer().getPlayer(data.getPlayerUUID());
        if (player == null) return CheckResult.exempt();

        // Check pitch bounds
        if (current.getPitch() > MAX_PITCH || current.getPitch() < MIN_PITCH) {
            return CheckResult.flag(100.0f,
                String.format("invalid pitch=%.4f", current.getPitch()));
        }

        // Check for NaN/Infinite (redundant but safe)
        if (!Float.isFinite(current.getYaw()) || !Float.isFinite(current.getPitch())) {
            return CheckResult.flag(100.0f, "NaN/Infinite rotation");
        }

        // Detect rotation lock: yaw and pitch haven't changed in many frames
        List<MovementFrame> history = data.getMovementHistory((int) ROTATION_LOCK_SAMPLES);
        if (history.size() >= (int) ROTATION_LOCK_SAMPLES) {
            double sumYaw = 0, sumPitch = 0;
            double sumYaw2 = 0, sumPitch2 = 0;
            for (MovementFrame f : history) {
                sumYaw += f.getYaw();
                sumPitch += f.getPitch();
                sumYaw2 += f.getYaw() * f.getYaw();
                sumPitch2 += f.getPitch() * f.getPitch();
            }
            int n = history.size();
            double varYaw = (sumYaw2 / n) - (sumYaw / n) * (sumYaw / n);
            double varPitch = (sumPitch2 / n) - (sumPitch / n) * (sumPitch / n);

            // If both yaw and pitch have essentially zero variance across many frames
            // AND the player is moving (horizontal speed > 0), that's rotation lock
            MovementFrame latest = history.get(history.size() - 1);
            MovementFrame oldest = history.get(0);
            double horizDist = latest.getHorizontalDistance(oldest);

            if (varYaw < LOCK_VARIANCE_THRESHOLD && varPitch < LOCK_VARIANCE_THRESHOLD && horizDist > 0.5) {
                return CheckResult.flag(3.0f,
                    String.format("rotation lock varYaw=%.2e varPitch=%.2e dist=%.4f",
                        varYaw, varPitch, horizDist));
            }
        }

        return CheckResult.pass();
    }
}
