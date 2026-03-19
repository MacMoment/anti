package com.anticheat;

import com.anticheat.alert.AlertManager;
import com.anticheat.check.CheckManager;
import com.anticheat.cloud.CloudConnector;
import com.anticheat.command.AnticheatCommand;
import com.anticheat.command.AlertsCommand;
import com.anticheat.config.AnticheatConfig;
import com.anticheat.data.PlayerDataManager;
import com.anticheat.listener.PacketListener;
import com.anticheat.listener.PlayerConnectionListener;
import com.anticheat.listener.PlayerEventListener;
import com.anticheat.mitigation.MitigationEngine;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.logging.Level;

public class AnticheatPlugin extends JavaPlugin {

    private AnticheatConfig anticheatConfig;
    private PlayerDataManager playerDataManager;
    private CheckManager checkManager;
    private MitigationEngine mitigationEngine;
    private AlertManager alertManager;
    private CloudConnector cloudConnector;

    @Override
    public void onEnable() {
        // 1. Load configuration
        anticheatConfig = new AnticheatConfig(this);

        // 2. Init managers
        playerDataManager = new PlayerDataManager();
        mitigationEngine = new MitigationEngine(this);
        alertManager = new AlertManager(this);
        cloudConnector = new CloudConnector(this);
        checkManager = new CheckManager(this);

        // 3. Register ProtocolLib packet listener
        try {
            ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
            protocolManager.addPacketListener(new PacketListener(this));
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to register ProtocolLib packet listener.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 4. Register Bukkit event listeners
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerEventListener(this), this);

        // 5. Register commands
        Objects.requireNonNull(getCommand("ac")).setExecutor(new AnticheatCommand(this));
        Objects.requireNonNull(getCommand("ac")).setTabCompleter(new AnticheatCommand(this));
        Objects.requireNonNull(getCommand("alerts")).setExecutor(new AlertsCommand(this));

        // 6. Start violation decay scheduler (every 40 ticks = 2 seconds)
        checkManager.startDecayTask();

        // 7. Start cloud sync scheduler
        if (anticheatConfig.isCloudEnabled()) {
            cloudConnector.startSyncScheduler();
        }

        // 8. Create PlayerData for any already-online players (e.g., on reload)
        for (var player : getServer().getOnlinePlayers()) {
            playerDataManager.createPlayer(player);
        }

        getLogger().info("Anti Anticheat v" + getDescription().getVersion() + " enabled. "
            + checkManager.getAllChecks().size() + " checks loaded.");
    }

    @Override
    public void onDisable() {
        // Shutdown cloud connector gracefully
        if (cloudConnector != null) {
            cloudConnector.shutdown();
        }

        // Clear all data
        if (playerDataManager != null) {
            playerDataManager.getAllPlayers().clear();
        }

        getLogger().info("Anti Anticheat disabled.");
    }

    public AnticheatConfig getAnticheatConfig() { return anticheatConfig; }
    public PlayerDataManager getPlayerDataManager() { return playerDataManager; }
    public CheckManager getCheckManager() { return checkManager; }
    public MitigationEngine getMitigationEngine() { return mitigationEngine; }
    public AlertManager getAlertManager() { return alertManager; }
    public CloudConnector getCloudConnector() { return cloudConnector; }
}
