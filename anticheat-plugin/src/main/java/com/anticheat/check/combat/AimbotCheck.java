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

public class AimbotCheck extends Check {

    private static final int HISTORY_FRAMES = 20;
    private static final double GCD_LOW_THRESHOLD = 0.001;
    private static final double SNAP_THRESHOLD = 90.0; // degrees per tick

    public AimbotCheck(AnticheatPlugin plugin) {
        super(plugin, "Aimbot", "combat", 15.0f, 0.1f);
    }

    @Override
    public CheckResult check(PlayerData data, Object context) {
        if (!(context instanceof Entity)) return CheckResult.exempt();

        Player player = plugin.getServer().getPlayer(data.getPlayerUUID());
        if (player == null) return CheckResult.exempt();

        List<MovementFrame> history = data.getMovementHistory(HISTORY_FRAMES);
        if (history.size() < 5) return CheckResult.pass();

        double[] yawDeltas = new double[history.size() - 1];
        double[] pitchDeltas = new double[history.size() - 1];

        for (int i = 1; i < history.size(); i++) {
            float prevYaw = history.get(i - 1).getYaw();
            float currYaw = history.get(i).getYaw();
            float prevPitch = history.get(i - 1).getPitch();
            float currPitch = history.get(i).getPitch();

            yawDeltas[i - 1] = Math.abs(currYaw - prevYaw);
            pitchDeltas[i - 1] = Math.abs(currPitch - prevPitch);
        }

        // Check for snap-to-target: very large rotation in 1 tick while attacking
        double lastYawDelta = yawDeltas[yawDeltas.length - 1];
        if (lastYawDelta > SNAP_THRESHOLD) {
            return CheckResult.flag(3.0f,
                String.format("snap rotation %.2f degrees/tick", lastYawDelta));
        }

        if (history.size() < HISTORY_FRAMES) return CheckResult.pass();

        // GCD analysis: real mouse movement has a GCD signature from DPI/sensitivity
        double gcdYaw = computeListGcd(yawDeltas);
        double gcdPitch = computeListGcd(pitchDeltas);

        // If GCD is nearly 0 across all deltas, the rotations are perfectly fluid (aimbot-like)
        if (gcdYaw < GCD_LOW_THRESHOLD && gcdPitch < GCD_LOW_THRESHOLD) {
            // Also verify there were actual rotations (not just staying still)
            double avgYawDelta = 0;
            for (double d : yawDeltas) avgYawDelta += d;
            avgYawDelta /= yawDeltas.length;

            if (avgYawDelta > 0.5) {
                return CheckResult.flag(2.0f,
                    String.format("GCD(yaw)=%.6f GCD(pitch)=%.6f", gcdYaw, gcdPitch));
            }
        }

        return CheckResult.pass();
    }

    private double computeListGcd(double[] values) {
        if (values.length == 0) return 0;
        double result = values[0];
        for (int i = 1; i < values.length; i++) {
            result = MathUtil.gcd(result, values[i]);
            if (result < 1e-10) return result;
        }
        return result;
    }
}
