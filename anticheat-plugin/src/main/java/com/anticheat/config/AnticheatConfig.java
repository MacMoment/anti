package com.anticheat.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public class AnticheatConfig {

    private final JavaPlugin plugin;

    private String serverID;
    private boolean debug;
    private boolean logViolations;

    private boolean cloudEnabled;
    private String cloudApiUrl;
    private String cloudApiKey;
    private int syncInterval;

    private boolean discordEnabled;
    private String discordWebhookUrl;
    private String discordMentionRole;

    private final Map<String, Double> sensitivityMap = new HashMap<>();
    private final Map<String, Boolean> checkEnabled = new HashMap<>();
    private final Map<String, Float> mitigationThresholds = new HashMap<>();
    private final Map<String, String> mitigationActions = new HashMap<>();

    public AnticheatConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();

        serverID = cfg.getString("general.server-id", "default-server");
        debug = cfg.getBoolean("general.debug", false);
        logViolations = cfg.getBoolean("general.log-violations", true);

        cloudEnabled = cfg.getBoolean("cloud.enabled", false);
        cloudApiUrl = cfg.getString("cloud.api-url", "");
        cloudApiKey = cfg.getString("cloud.api-key", "");
        syncInterval = cfg.getInt("cloud.sync-interval", 300);

        discordEnabled = cfg.getBoolean("discord.enabled", false);
        discordWebhookUrl = cfg.getString("discord.webhook-url", "");
        discordMentionRole = cfg.getString("discord.mention-role", "");

        sensitivityMap.clear();
        checkEnabled.clear();
        mitigationThresholds.clear();
        mitigationActions.clear();

        if (cfg.isConfigurationSection("checks")) {
            for (String checkName : cfg.getConfigurationSection("checks").getKeys(false)) {
                String path = "checks." + checkName;
                checkEnabled.put(checkName, cfg.getBoolean(path + ".enabled", true));
                sensitivityMap.put(checkName, cfg.getDouble(path + ".sensitivity", 1.0));
            }
        }

        if (cfg.isConfigurationSection("mitigation.thresholds")) {
            for (String checkName : cfg.getConfigurationSection("mitigation.thresholds").getKeys(false)) {
                mitigationThresholds.put(checkName, (float) cfg.getDouble("mitigation.thresholds." + checkName, 10.0));
            }
        }

        if (cfg.isConfigurationSection("mitigation.actions")) {
            for (String checkName : cfg.getConfigurationSection("mitigation.actions").getKeys(false)) {
                mitigationActions.put(checkName, cfg.getString("mitigation.actions." + checkName, "MONITOR"));
            }
        }
    }

    public String getServerID() { return serverID; }
    public boolean isDebug() { return debug; }
    public boolean isLogViolations() { return logViolations; }

    public boolean isCloudEnabled() { return cloudEnabled; }
    public String getCloudApiUrl() { return cloudApiUrl; }
    public String getCloudApiKey() { return cloudApiKey; }
    public int getSyncInterval() { return syncInterval; }

    public boolean isDiscordEnabled() { return discordEnabled; }
    public String getDiscordWebhookUrl() { return discordWebhookUrl; }
    public String getDiscordMentionRole() { return discordMentionRole; }

    public double getSensitivity(String checkName) {
        return sensitivityMap.getOrDefault(checkName, 1.0);
    }

    public boolean isCheckEnabled(String checkName) {
        return checkEnabled.getOrDefault(checkName, true);
    }

    public float getMitigationThreshold(String checkName) {
        return mitigationThresholds.getOrDefault(checkName, 10.0f);
    }

    public String getMitigationAction(String checkName) {
        return mitigationActions.getOrDefault(checkName, "MONITOR");
    }

    public Map<String, Double> getSensitivityMap() { return sensitivityMap; }
    public Map<String, Boolean> getCheckEnabledMap() { return checkEnabled; }
    public Map<String, Float> getMitigationThresholds() { return mitigationThresholds; }
}
