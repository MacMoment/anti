package com.anticheat.listener;

import com.anticheat.AnticheatPlugin;
import com.anticheat.data.PlayerData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerConnectionListener implements Listener {

    private final AnticheatPlugin plugin;

    public PlayerConnectionListener(AnticheatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        PlayerData data = plugin.getPlayerDataManager().createPlayer(event.getPlayer());
        // Set flying state for creative / spectator
        var gm = event.getPlayer().getGameMode();
        data.setFlying(gm == org.bukkit.GameMode.CREATIVE || gm == org.bukkit.GameMode.SPECTATOR
                || event.getPlayer().isFlying());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var uuid = event.getPlayer().getUniqueId();
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(uuid);

        if (data != null && plugin.getAnticheatConfig().isCloudEnabled()) {
            // Fire-and-forget async sync on quit
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () ->
                plugin.getCloudConnector().sendAsync(data));
        }

        plugin.getPlayerDataManager().removePlayer(uuid);
        plugin.getMitigationEngine().removeProfile(uuid);
        plugin.getAlertManager().removePlayer(uuid);
    }
}
