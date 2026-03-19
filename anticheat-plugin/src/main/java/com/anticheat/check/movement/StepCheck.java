package com.anticheat.check.movement;

import com.anticheat.AnticheatPlugin;
import com.anticheat.check.Check;
import com.anticheat.check.CheckResult;
import com.anticheat.data.MovementFrame;
import com.anticheat.data.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class StepCheck extends Check {

    // Maximum vanilla step height in blocks
    private static final double MAX_STEP_HEIGHT = 0.6;
    // Jump boost adds some ascent; track if player just jumped
    private static final double JUMP_ASCENT = 0.42;

    public StepCheck(AnticheatPlugin plugin) {
        super(plugin, "Step", "movement", 15.0f, 0.1f);
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

        // Exempt in vehicles, gliding, in liquid
        if (player.isInsideVehicle() || player.isGliding()) return CheckResult.exempt();

        double deltaY = current.getY() - previous.getY();

        // Only flag upward movement from on-ground position
        if (!previous.isOnGround()) return CheckResult.pass();
        if (deltaY <= 0) return CheckResult.pass();

        // Allow a normal jump
        if (deltaY > MAX_STEP_HEIGHT && deltaY < JUMP_ASCENT + 0.05) {
            // This could be a jump, allow it
            return CheckResult.pass();
        }

        // Check if riding a horse (horses have larger step height)
        if (player.isInsideVehicle()) return CheckResult.exempt();

        // Check for scaffolding or slab transitions (max 0.5 step normally)
        Material blockAt = player.getLocation().getBlock().getType();
        if (blockAt == Material.SCAFFOLDING) return CheckResult.exempt();

        if (deltaY > MAX_STEP_HEIGHT && deltaY < JUMP_ASCENT - 0.05) {
            // Stepped higher than allowed but less than a jump - suspicious
            float severity = (float) Math.min((deltaY - MAX_STEP_HEIGHT) * 10.0, 3.0);
            return CheckResult.flag(severity,
                String.format("stepped %.4f blocks (max %.2f)", deltaY, MAX_STEP_HEIGHT));
        }

        return CheckResult.pass();
    }
}
