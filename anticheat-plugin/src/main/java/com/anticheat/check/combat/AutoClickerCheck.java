package com.anticheat.check.combat;

import com.anticheat.AnticheatPlugin;
import com.anticheat.check.Check;
import com.anticheat.check.CheckResult;
import com.anticheat.data.PlayerData;
import com.anticheat.util.MathUtil;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.List;

public class AutoClickerCheck extends Check {

    private static final int MAX_HUMAN_CPS = 20;
    private static final double MIN_STDDEV_MS = 10.0;   // Below this = too consistent
    private static final double LOW_ENTROPY_THRESHOLD = 2.0;

    public AutoClickerCheck(AnticheatPlugin plugin) {
        super(plugin, "AutoClicker", "combat", 20.0f, 0.05f);
    }

    @Override
    public CheckResult check(PlayerData data, Object context) {
        if (!(context instanceof Entity)) return CheckResult.exempt();

        Player player = plugin.getServer().getPlayer(data.getPlayerUUID());
        if (player == null) return CheckResult.exempt();

        List<Long> timestamps = data.getClickTimestamps();
        if (timestamps.size() < 10) return CheckResult.pass();

        long now = System.currentTimeMillis();
        long oneSecAgo = now - 1000L;

        // Count clicks in the last second
        long recentClicks = timestamps.stream().filter(t -> t > oneSecAgo).count();
        int cps = (int) recentClicks;

        if (cps > MAX_HUMAN_CPS) {
            float severity = (float) Math.min((cps - MAX_HUMAN_CPS) * 0.5, 5.0);
            return CheckResult.flag(severity,
                String.format("CPS=%d (max %d)", cps, MAX_HUMAN_CPS));
        }

        // Analyze inter-click intervals for standard deviation
        long[] ts = timestamps.stream().mapToLong(Long::longValue).toArray();
        if (ts.length >= 10) {
            double[] intervals = new double[ts.length - 1];
            for (int i = 1; i < ts.length; i++) {
                intervals[i - 1] = ts[i] - ts[i - 1];
            }

            double stdDev = MathUtil.standardDeviation(intervals);
            if (stdDev < MIN_STDDEV_MS && intervals.length >= 10) {
                return CheckResult.flag(2.0f,
                    String.format("click stddev=%.2fms (min %.0fms)", stdDev, MIN_STDDEV_MS));
            }

            // Shannon entropy analysis
            double entropy = MathUtil.entropy(ts);
            if (entropy < LOW_ENTROPY_THRESHOLD && ts.length >= 20) {
                return CheckResult.flag(1.5f,
                    String.format("click entropy=%.3f (threshold %.1f)", entropy, LOW_ENTROPY_THRESHOLD));
            }
        }

        return CheckResult.pass();
    }
}
