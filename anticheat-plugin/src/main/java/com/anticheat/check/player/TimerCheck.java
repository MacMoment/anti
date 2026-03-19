package com.anticheat.check.player;

import com.anticheat.AnticheatPlugin;
import com.anticheat.check.Check;
import com.anticheat.check.CheckResult;
import com.anticheat.data.MovementFrame;
import com.anticheat.data.PlayerData;
import org.bukkit.entity.Player;

import java.util.List;

public class TimerCheck extends Check {

    // Normal Minecraft tick rate: 20 packets/second
    private static final int NORMAL_PACKETS_PER_SECOND = 20;
    // Allow up to 22 before flagging (2 extra for jitter)
    private static final int MAX_PACKETS_PER_SECOND = 22;

    public TimerCheck(AnticheatPlugin plugin) {
        super(plugin, "Timer", "player", 15.0f, 0.1f);
    }

    @Override
    public CheckResult check(PlayerData data, Object context) {
        if (!(context instanceof MovementFrame)) return CheckResult.exempt();

        Player player = plugin.getServer().getPlayer(data.getPlayerUUID());
        if (player == null) return CheckResult.exempt();

        // Record this packet's timestamp
        long now = System.currentTimeMillis();
        data.addPacketTimestamp(now);

        List<Long> timestamps = data.getPacketTimestamps();
        if (timestamps.size() < 5) return CheckResult.pass();

        // Count packets in the last second
        long oneSecAgo = now - 1000L;
        long count = timestamps.stream().filter(t -> t >= oneSecAgo).count();

        if (count > MAX_PACKETS_PER_SECOND) {
            float severity = (float) Math.min((count - MAX_PACKETS_PER_SECOND) * 0.5, 5.0);
            return CheckResult.flag(severity,
                String.format("%d packets/sec (max %d)", count, MAX_PACKETS_PER_SECOND));
        }

        return CheckResult.pass();
    }
}
