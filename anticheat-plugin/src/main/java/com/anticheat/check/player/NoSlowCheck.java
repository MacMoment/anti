package com.anticheat.check.player;

import com.anticheat.AnticheatPlugin;
import com.anticheat.check.Check;
import com.anticheat.check.CheckResult;
import com.anticheat.data.MovementFrame;
import com.anticheat.data.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

public class NoSlowCheck extends Check {

    private static final double BLOCKING_MAX_SPEED = 0.065;  // ~0.3x normal walk
    private static final double EATING_MAX_SPEED = 0.19;     // slightly slower than walk

    public NoSlowCheck(AnticheatPlugin plugin) {
        super(plugin, "NoSlow", "player", 10.0f, 0.1f);
    }

    @Override
    public CheckResult check(PlayerData data, Object context) {
        if (!(context instanceof MovementFrame current)) return CheckResult.exempt();
        MovementFrame previous = data.getPreviousMovementFrame();
        if (previous == null) return CheckResult.pass();

        Player player = plugin.getServer().getPlayer(data.getPlayerUUID());
        if (player == null) return CheckResult.exempt();

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return CheckResult.exempt();
        }

        double horizontalSpeed = current.getHorizontalDistance(previous);

        if (data.isBlocking() && horizontalSpeed > BLOCKING_MAX_SPEED) {
            float severity = (float) Math.min((horizontalSpeed - BLOCKING_MAX_SPEED) * 10.0, 3.0);
            return CheckResult.flag(severity,
                String.format("blocking speed=%.4f max=%.4f", horizontalSpeed, BLOCKING_MAX_SPEED));
        }

        if (data.isEating() && horizontalSpeed > EATING_MAX_SPEED) {
            float severity = (float) Math.min((horizontalSpeed - EATING_MAX_SPEED) * 5.0, 2.0);
            return CheckResult.flag(severity,
                String.format("eating speed=%.4f max=%.4f", horizontalSpeed, EATING_MAX_SPEED));
        }

        return CheckResult.pass();
    }
}
