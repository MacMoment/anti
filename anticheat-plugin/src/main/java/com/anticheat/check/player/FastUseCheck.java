package com.anticheat.check.player;

import com.anticheat.AnticheatPlugin;
import com.anticheat.check.Check;
import com.anticheat.check.CheckResult;
import com.anticheat.data.MovementFrame;
import com.anticheat.data.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;

public class FastUseCheck extends Check {

    // Eating takes 32 ticks = 1600ms
    private static final long MIN_EAT_DURATION_MS = 1400L; // allow small tolerance
    // Drinking takes same time
    private static final long MIN_DRINK_DURATION_MS = 1400L;

    public FastUseCheck(AnticheatPlugin plugin) {
        super(plugin, "FastUse", "player", 10.0f, 0.2f);
    }

    @Override
    public CheckResult check(PlayerData data, Object context) {
        if (!(context instanceof MovementFrame)) return CheckResult.exempt();

        Player player = plugin.getServer().getPlayer(data.getPlayerUUID());
        if (player == null) return CheckResult.exempt();

        // Track item use: when the player stops using an item, check elapsed time
        if (!player.isHandRaised()) {
            long startTime = data.getItemUseStartTime();
            if (startTime > 0 && data.isEating()) {
                long elapsed = System.currentTimeMillis() - startTime;
                ItemStack item = player.getInventory().getItemInMainHand();

                long requiredMs = getRequiredTime(item);
                if (requiredMs > 0 && elapsed < requiredMs) {
                    data.setItemUseStartTime(-1);
                    data.setEating(false);
                    float severity = (float) Math.min((requiredMs - elapsed) / 200.0, 3.0);
                    return CheckResult.flag(severity,
                        String.format("used item in %dms (min %dms)", elapsed, requiredMs));
                }
                data.setItemUseStartTime(-1);
                data.setEating(false);
            }
        } else {
            // Player started or continuing item use
            if (data.getItemUseStartTime() < 0) {
                data.setItemUseStartTime(System.currentTimeMillis());
                ItemStack item = player.getInventory().getItemInMainHand();
                if (item != null && isEdible(item.getType())) {
                    data.setEating(true);
                }
            }
        }

        return CheckResult.pass();
    }

    private long getRequiredTime(ItemStack item) {
        if (item == null) return -1;
        if (item.getType().isEdible()) return MIN_EAT_DURATION_MS;
        if (item.getType() == Material.POTION) return MIN_DRINK_DURATION_MS;
        if (item.getType() == Material.MILK_BUCKET) return MIN_DRINK_DURATION_MS;
        return -1;
    }

    private boolean isEdible(Material mat) {
        return mat.isEdible() || mat == Material.POTION || mat == Material.MILK_BUCKET;
    }
}
