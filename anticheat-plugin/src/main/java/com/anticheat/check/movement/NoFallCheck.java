package com.anticheat.check.movement;

import com.anticheat.AnticheatPlugin;
import com.anticheat.check.Check;
import com.anticheat.check.CheckResult;
import com.anticheat.data.MovementFrame;
import com.anticheat.data.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

public class NoFallCheck extends Check {

    // If falling faster than this (negative Y velocity) hitting ground should cause damage
    private static final double DAMAGE_VELOCITY_THRESHOLD = -0.5;

    public NoFallCheck(AnticheatPlugin plugin) {
        super(plugin, "NoFall", "movement", 10.0f, 0.1f);
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

        // Exempt in vehicles, gliding
        if (player.isInsideVehicle() || player.isGliding()) return CheckResult.exempt();

        // Only care about transitions from air to ground
        if (current.isOnGround() && !previous.isOnGround()) {
            // The Y delta over the last tick tells us the fall velocity
            double deltaY = current.getY() - previous.getY();

            // If they were falling fast enough to take damage
            if (deltaY < DAMAGE_VELOCITY_THRESHOLD) {
                // Estimate expected fall damage (vanilla: damage = (fall_distance - 3) hp)
                // The fact that they just landed with no apparent velocity recording is suspicious
                // We compare: the packet claims onGround=true BUT the velocity was strongly downward
                // This check detects clients that fake onGround=true early to cancel fall damage
                if (previous.getVelocityY() < DAMAGE_VELOCITY_THRESHOLD) {
                    float severity = (float) Math.min(Math.abs(deltaY) * 2.0, 4.0);
                    return CheckResult.flag(severity,
                        String.format("landed with deltaY=%.4f prevVelY=%.4f",
                            deltaY, previous.getVelocityY()));
                }
            }
        }

        return CheckResult.pass();
    }
}
