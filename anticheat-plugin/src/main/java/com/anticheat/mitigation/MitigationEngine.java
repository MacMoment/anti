package com.anticheat.mitigation;

import com.anticheat.AnticheatPlugin;
import com.anticheat.data.PlayerData;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class MitigationEngine {

    private final AnticheatPlugin plugin;
    private final Map<UUID, MitigationProfile> profiles = new ConcurrentHashMap<>();

    public MitigationEngine(AnticheatPlugin plugin) {
        this.plugin = plugin;
    }

    public void handleViolation(Player player, PlayerData data, String checkName, float violationLevel) {
        float threshold = plugin.getAnticheatConfig().getMitigationThreshold(checkName);
        if (violationLevel < threshold) return;

        String actionStr = plugin.getAnticheatConfig().getMitigationAction(checkName);
        MitigationAction action = MitigationAction.fromString(actionStr);

        MitigationProfile profile = getMitigationProfile(player.getUniqueId());
        profile.setAction(checkName, action);

        applyMitigation(player, data, action);

        if (plugin.getAnticheatConfig().isDebug()) {
            plugin.getLogger().info(String.format("[Mitigation] %s | check=%s vl=%.2f action=%s",
                player.getName(), checkName, violationLevel, action.name()));
        }
    }

    public void applyMitigation(Player player, PlayerData data, MitigationAction action) {
        switch (action) {
            case RUBBERBAND -> {
                // Must run on main thread
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    Location safe = new Location(
                        player.getWorld(),
                        data.getLastX(), data.getLastY(), data.getLastZ(),
                        player.getLocation().getYaw(),
                        player.getLocation().getPitch()
                    );
                    player.teleport(safe);
                    data.setMitigated(true);
                    if (!data.getActiveMitigations().contains("RUBBERBAND")) {
                        data.getActiveMitigations().add("RUBBERBAND");
                    }
                });
            }
            case LIMIT_REACH -> {
                data.setMitigated(true);
                if (!data.getActiveMitigations().contains("LIMIT_REACH")) {
                    data.getActiveMitigations().add("LIMIT_REACH");
                }
            }
            case LIMIT_CPS -> {
                data.setMitigated(true);
                if (!data.getActiveMitigations().contains("LIMIT_CPS")) {
                    data.getActiveMitigations().add("LIMIT_CPS");
                }
            }
            case SLOW_MOVEMENT -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.addPotionEffect(new PotionEffect(
                        PotionEffectType.SLOW, 60, 2, false, false, false
                    ));
                    data.setMitigated(true);
                });
            }
            case FLAG_FOR_BAN -> {
                MitigationProfile profile = getMitigationProfile(player.getUniqueId());
                profile.setFlagged(true);
                plugin.getAlertManager().sendAlert(
                    "BanReview", player, 100.0f,
                    "Flagged for ban review - multiple checks exceeded"
                );
                logViolationToFile(player, data);
            }
            case MONITOR -> {
                // No action besides alerting (already done in Check#onFlag)
            }
        }
    }

    private void logViolationToFile(Player player, PlayerData data) {
        try {
            java.io.File logFile = new java.io.File(plugin.getDataFolder(), "violations.log");
            if (!logFile.exists()) {
                plugin.getDataFolder().mkdirs();
                logFile.createNewFile();
            }
            String entry = String.format("[%s] Player %s (%s) flagged for ban review. VLs: %s%n",
                java.time.Instant.now().toString(),
                player.getName(),
                player.getUniqueId().toString(),
                data.getViolationLevels().toString()
            );
            try (java.io.FileWriter fw = new java.io.FileWriter(logFile, true)) {
                fw.write(entry);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to write violation log", e);
        }
    }

    public MitigationProfile getMitigationProfile(UUID uuid) {
        return profiles.computeIfAbsent(uuid, k -> new MitigationProfile());
    }

    public void removeProfile(UUID uuid) {
        profiles.remove(uuid);
    }
}
