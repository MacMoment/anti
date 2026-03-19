package com.anticheat.check.movement;

import com.anticheat.AnticheatPlugin;
import com.anticheat.check.Check;
import com.anticheat.check.CheckResult;
import com.anticheat.data.MovementFrame;
import com.anticheat.data.PlayerData;
import org.bukkit.entity.Player;

public class VelocityCheck extends Check {

    private static final double SIGNIFICANT_VELOCITY = 0.3;
    private static final int VELOCITY_WINDOW_TICKS = 3;

    public VelocityCheck(AnticheatPlugin plugin) {
        super(plugin, "Velocity", "movement", 10.0f, 0.1f);
    }

    @Override
    public CheckResult check(PlayerData data, Object context) {
        if (!(context instanceof MovementFrame current)) return CheckResult.exempt();
        MovementFrame previous = data.getPreviousMovementFrame();
        if (previous == null) return CheckResult.pass();

        Player player = plugin.getServer().getPlayer(data.getPlayerUUID());
        if (player == null) return CheckResult.exempt();

        // If a significant velocity packet was sent but the player didn't accept it
        if (data.getVelocityTicksLeft() > 0) {
            data.setVelocityTicksLeft(data.getVelocityTicksLeft() - 1);

            double pendingVY = data.getPendingVelocityY();
            double pendingVX = data.getPendingVelocityX();
            double pendingVZ = data.getPendingVelocityZ();

            double pendingMag = Math.sqrt(pendingVX * pendingVX + pendingVY * pendingVY + pendingVZ * pendingVZ);
            if (pendingMag > SIGNIFICANT_VELOCITY && data.getVelocityTicksLeft() == 0) {
                // Window expired - check if movement reflected the velocity
                double deltaX = current.getX() - previous.getX();
                double deltaY = current.getY() - previous.getY();
                double deltaZ = current.getZ() - previous.getZ();

                // If the actual movement doesn't align with the expected velocity at all, flag
                boolean acceptedX = Math.abs(deltaX) > Math.abs(pendingVX) * 0.3;
                boolean acceptedY = pendingVY > 0 ? deltaY > pendingVY * 0.3 : true;
                boolean acceptedZ = Math.abs(deltaZ) > Math.abs(pendingVZ) * 0.3;

                if (!acceptedX && !acceptedY && !acceptedZ && pendingMag > SIGNIFICANT_VELOCITY) {
                    return CheckResult.flag(2.0f,
                        String.format("ignored velocity (%.2f,%.2f,%.2f)",
                            pendingVX, pendingVY, pendingVZ));
                }
            }
        }

        return CheckResult.pass();
    }
}
