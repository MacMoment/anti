package com.anticheat.check.player;

import com.anticheat.AnticheatPlugin;
import com.anticheat.check.Check;
import com.anticheat.check.CheckResult;
import com.anticheat.data.MovementFrame;
import com.anticheat.data.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class ScaffoldCheck extends Check {

    private static final double MAX_PITCH_FOR_SCAFFOLD = 85.0;
    private static final double YAW_CHANGE_THRESHOLD = 45.0; // degrees in 1 tick

    public ScaffoldCheck(AnticheatPlugin plugin) {
        super(plugin, "Scaffold", "player", 15.0f, 0.05f);
    }

    @Override
    public CheckResult check(PlayerData data, Object context) {
        if (!(context instanceof MovementFrame current)) return CheckResult.exempt();

        Player player = plugin.getServer().getPlayer(data.getPlayerUUID());
        if (player == null) return CheckResult.exempt();

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return CheckResult.exempt();
        }

        // Check if player is holding a block
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || !hand.getType().isBlock() || hand.getType() == Material.AIR) {
            return CheckResult.pass();
        }

        MovementFrame previous = data.getPreviousMovementFrame();
        if (previous == null) return CheckResult.pass();

        List<MovementFrame> history = data.getMovementHistory(4);
        if (history.size() < 2) return CheckResult.pass();

        // Check for suspicious rapid yaw change while placing blocks below
        double yawDelta = Math.abs(current.getYaw() - previous.getYaw());
        if (yawDelta > 180) yawDelta = 360 - yawDelta;

        // Scaffold hacks rapidly snap rotation to face block placement
        if (yawDelta > YAW_CHANGE_THRESHOLD && current.getPitch() > MAX_PITCH_FOR_SCAFFOLD) {
            // Check if there's a block below the player's feet (recently placed)
            Location below = new Location(player.getWorld(),
                current.getX(), current.getY() - 1.0, current.getZ());
            Block blockBelow = below.getBlock();

            if (blockBelow.getType() != Material.AIR && blockBelow.getType() != Material.VOID_AIR) {
                // Player rapidly rotated and there's a block below — suspicious
                float severity = (float) Math.min(yawDelta / YAW_CHANGE_THRESHOLD, 3.0);
                return CheckResult.flag(severity,
                    String.format("scaffold yawDelta=%.2f pitch=%.2f", yawDelta, current.getPitch()));
            }
        }

        // Check placing blocks while falling at high speed (impossible angle)
        if (!current.isOnGround() && data.getTicksInAir() > 3) {
            double deltaY = current.getY() - previous.getY();
            if (deltaY < -0.3) {
                // Player falling; check if pitch is looking downward suspiciously
                // Scaffold bots often look straight down
                if (current.getPitch() > 75.0) {
                    Location belowFeet = new Location(player.getWorld(),
                        current.getX(), current.getY() - 0.1, current.getZ());
                    Block check = belowFeet.getBlock();
                    // If the block below was just placed (solid while falling)
                    if (check.getType().isSolid()) {
                        return CheckResult.flag(2.0f,
                            String.format("scaffold while falling: pitch=%.2f deltaY=%.4f",
                                current.getPitch(), deltaY));
                    }
                }
            }
        }

        return CheckResult.pass();
    }
}
