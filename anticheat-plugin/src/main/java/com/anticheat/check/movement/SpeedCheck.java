package com.anticheat.check.movement;

import com.anticheat.AnticheatPlugin;
import com.anticheat.check.Check;
import com.anticheat.check.CheckResult;
import com.anticheat.data.MovementFrame;
import com.anticheat.data.PlayerData;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class SpeedCheck extends Check {

    private static final double BASE_WALK_SPEED = 0.221;
    private static final double BASE_SPRINT_SPEED = 0.2806;
    private static final double BASE_SNEAK_SPEED = 0.074;
    private static final double BASE_FLY_SPEED = 1.0;
    private static final double SPEED_TOLERANCE = 1.1; // 10% buffer

    public SpeedCheck(AnticheatPlugin plugin) {
        super(plugin, "Speed", "movement", 20.0f, 0.05f);
    }

    @Override
    public CheckResult check(PlayerData data, Object context) {
        if (!(context instanceof MovementFrame current)) return CheckResult.exempt();

        MovementFrame previous = data.getPreviousMovementFrame();
        if (previous == null) return CheckResult.pass();

        // Exempt when receiving velocity
        if (data.isReceivedVelocity() || data.getVelocityTicksLeft() > 0) {
            return CheckResult.exempt();
        }

        double horizontalDistance = current.getHorizontalDistance(previous);

        // Look up the player from the server to get potion effects and block below
        Player player = plugin.getServer().getPlayer(data.getPlayerUUID());
        if (player == null) return CheckResult.exempt();

        // Exempt if in vehicle
        if (player.isInsideVehicle()) return CheckResult.exempt();

        // Exempt flying in creative/spectator
        if (data.isFlying()) return CheckResult.exempt();

        double maxAllowed = calculateMaxSpeed(player, current);

        // Apply sensitivity multiplier
        double sensitivity = plugin.getAnticheatConfig().getSensitivity(getName());
        double adjustedMax = maxAllowed * SPEED_TOLERANCE / sensitivity;

        if (horizontalDistance > adjustedMax) {
            float severity = (float) Math.min((horizontalDistance - adjustedMax) * 5.0, 5.0);
            return CheckResult.flag(severity, String.format(
                "moved %.4f, max %.4f (x%.2f)",
                horizontalDistance, adjustedMax, horizontalDistance / adjustedMax));
        }
        return CheckResult.pass();
    }

    private double calculateMaxSpeed(Player player, MovementFrame frame) {
        double base;
        if (frame.isSprinting()) {
            base = BASE_SPRINT_SPEED;
        } else if (frame.isSneaking()) {
            base = BASE_SNEAK_SPEED;
        } else {
            base = BASE_WALK_SPEED;
        }

        // Apply Speed potion effect
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (effect.getType().equals(PotionEffectType.SPEED)) {
                // Each level adds ~20% speed
                base += (effect.getAmplifier() + 1) * 0.2 * BASE_WALK_SPEED;
            }
            if (effect.getType().equals(PotionEffectType.SLOW)) {
                base -= (effect.getAmplifier() + 1) * 0.15 * BASE_WALK_SPEED;
                base = Math.max(base, 0.01);
            }
        }

        // Check block below for speed modifiers
        org.bukkit.Location loc = player.getLocation();
        Material blockBelow = loc.getBlock().getType();
        if (blockBelow == Material.AIR) {
            blockBelow = loc.clone().subtract(0, 1, 0).getBlock().getType();
        }
        if (blockBelow == Material.SOUL_SAND || blockBelow == Material.SOUL_SOIL) {
            base *= 0.4; // Soul sand slows by ~60%
        } else if (blockBelow == Material.ICE || blockBelow == Material.PACKED_ICE
                || blockBelow == Material.BLUE_ICE || blockBelow == Material.FROSTED_ICE) {
            base *= 2.5; // Ice allows more speed due to sliding
        } else if (blockBelow == Material.SLIME_BLOCK) {
            base *= 0.8;
        }

        // Account for water / swimming  
        if (player.isSwimming()) {
            base *= 1.3; // Dolphin's Grace or swimming
        }

        return base;
    }
}
