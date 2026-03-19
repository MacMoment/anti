package com.anticheat.listener;

import com.anticheat.AnticheatPlugin;
import com.anticheat.data.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.*;

public class PlayerEventListener implements Listener {

    private final AnticheatPlugin plugin;

    public PlayerEventListener(AnticheatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(attacker.getUniqueId());
        if (data == null) return;

        // Record click for CPS / combat checks
        data.addClick(System.currentTimeMillis());

        // Run combat checks against the damaged entity
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () ->
            plugin.getCheckManager().processCombat(attacker, data, event.getEntity()));
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(event.getPlayer().getUniqueId());
        if (data == null) return;
        // Sneaking state tracked in movement frames; no extra action needed
    }

    @EventHandler
    public void onSprint(PlayerToggleSprintEvent event) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(event.getPlayer().getUniqueId());
        if (data == null) return;
        // Sprinting state tracked in movement frames; no extra action needed
    }

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(event.getPlayer().getUniqueId());
        if (data == null) return;
        var newMode = event.getNewGameMode();
        data.setFlying(newMode == org.bukkit.GameMode.CREATIVE || newMode == org.bukkit.GameMode.SPECTATOR);
    }

    @EventHandler
    public void onFly(PlayerToggleFlightEvent event) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(event.getPlayer().getUniqueId());
        if (data == null) return;
        data.setFlying(event.isFlying());
    }

    @EventHandler
    public void onItemUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data == null) return;

        // Track shield blocking
        if (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                || event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            var item = player.getInventory().getItemInMainHand();
            if (item.getType() == org.bukkit.Material.SHIELD) {
                data.setBlocking(true);
            }
        }
    }
}
