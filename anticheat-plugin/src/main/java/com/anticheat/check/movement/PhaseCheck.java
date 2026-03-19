package com.anticheat.check.movement;

import com.anticheat.AnticheatPlugin;
import com.anticheat.check.Check;
import com.anticheat.check.CheckResult;
import com.anticheat.data.MovementFrame;
import com.anticheat.data.PlayerData;
import com.anticheat.physics.CollisionHandler;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class PhaseCheck extends Check {

    private static final double MIN_HORIZONTAL_MOVEMENT = 0.1;
    private final CollisionHandler collisionHandler = new CollisionHandler();

    public PhaseCheck(AnticheatPlugin plugin) {
        super(plugin, "Phase", "movement", 15.0f, 0.1f);
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

        double horizontalDist = current.getHorizontalDistance(previous);
        if (horizontalDist < MIN_HORIZONTAL_MOVEMENT) return CheckResult.pass();

        // Exempt if velocity was received recently (could be knockback)
        if (data.isReceivedVelocity() || data.getVelocityTicksLeft() > 0) return CheckResult.exempt();

        Location from = new Location(player.getWorld(), previous.getX(), previous.getY(), previous.getZ());
        Location to = new Location(player.getWorld(), current.getX(), current.getY(), current.getZ());

        if (collisionHandler.checkCollision(from, to, player)) {
            return CheckResult.flag(3.0f,
                String.format("phased from (%.2f,%.2f,%.2f) to (%.2f,%.2f,%.2f)",
                    previous.getX(), previous.getY(), previous.getZ(),
                    current.getX(), current.getY(), current.getZ()));
        }

        return CheckResult.pass();
    }
}
