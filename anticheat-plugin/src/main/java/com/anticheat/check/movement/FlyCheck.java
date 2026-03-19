package com.anticheat.check.movement;

import com.anticheat.AnticheatPlugin;
import com.anticheat.check.Check;
import com.anticheat.check.CheckResult;
import com.anticheat.data.MovementFrame;
import com.anticheat.data.PlayerData;
import com.anticheat.physics.PhysicsEngine;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class FlyCheck extends Check {

    private static final double Y_TOLERANCE = 0.1;
    private static final int MIN_TICKS_IN_AIR = 5;

    private final PhysicsEngine physics = new PhysicsEngine();

    public FlyCheck(AnticheatPlugin plugin) {
        super(plugin, "Fly", "movement", 20.0f, 0.05f);
    }

    @Override
    public CheckResult check(PlayerData data, Object context) {
        if (!(context instanceof MovementFrame current)) return CheckResult.exempt();
        MovementFrame previous = data.getPreviousMovementFrame();
        if (previous == null) return CheckResult.pass();

        Player player = plugin.getServer().getPlayer(data.getPlayerUUID());
        if (player == null) return CheckResult.exempt();

        // Exempt creative / spectator
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return CheckResult.exempt();
        }

        // Exempt if using elytra
        if (player.isGliding()) return CheckResult.exempt();

        // Exempt inside vehicle
        if (player.isInsideVehicle()) return CheckResult.exempt();

        // Exempt if velocity was received recently
        if (data.isReceivedVelocity() || data.getVelocityTicksLeft() > 0) {
            return CheckResult.exempt();
        }

        // Exempt in liquid
        if (physics.isInLiquid(player.getLocation())) return CheckResult.exempt();

        // Exempt on climbable
        if (physics.isClimbable(player.getLocation().getBlock().getType())) return CheckResult.exempt();

        // Track ticks in air
        if (!current.isOnGround()) {
            data.setTicksInAir(data.getTicksInAir() + 1);
        } else {
            data.setTicksInAir(0);
            return CheckResult.pass();
        }

        if (data.getTicksInAir() < MIN_TICKS_IN_AIR) return CheckResult.pass();

        // Check levitation
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (effect.getType().equals(PotionEffectType.LEVITATION)) return CheckResult.exempt();
            if (effect.getType().equals(PotionEffectType.SLOW_FALLING)) return CheckResult.exempt();
        }

        // Compute expected Y using physics prediction
        double expectedY = physics.predictNextY(
            previous.getY(),
            previous.getVelocityY(),
            previous.isOnGround(),
            player.getActivePotionEffects()
        );

        double actualDeltaY = current.getY() - previous.getY();
        double expectedDeltaY = expectedY - previous.getY();

        // If player is descending at expected gravity rate, they're not flying
        // If they're hovering (delta close to 0 when it should be falling), flag
        if (data.getTicksInAir() > MIN_TICKS_IN_AIR) {
            // After a few ticks player must be falling
            if (actualDeltaY > expectedDeltaY + Y_TOLERANCE && actualDeltaY > -0.01) {
                float severity = (float) Math.min(Math.abs(actualDeltaY - expectedDeltaY) * 3.0, 5.0);
                return CheckResult.flag(severity,
                    String.format("deltaY=%.4f expected=%.4f ticks=%d",
                        actualDeltaY, expectedDeltaY, data.getTicksInAir()));
            }
        }

        return CheckResult.pass();
    }
}
