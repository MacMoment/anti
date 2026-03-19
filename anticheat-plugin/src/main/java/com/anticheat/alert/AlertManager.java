package com.anticheat.alert;

import com.anticheat.AnticheatPlugin;
import com.anticheat.cloud.CloudResponse;
import com.google.gson.JsonObject;
import okhttp3.*;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class AlertManager {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final AnticheatPlugin plugin;
    private final Set<UUID> alertReceivers = ConcurrentHashMap.newKeySet();
    private final OkHttpClient httpClient;

    public AlertManager(AnticheatPlugin plugin) {
        this.plugin = plugin;
        this.httpClient = new OkHttpClient();
    }

    public void sendAlert(String checkName, Player flagged, float vl, String detail) {
        String message = AlertMessage.format(checkName, flagged.getName(), vl, detail);

        // Send to all online players with alerts enabled and the permission
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (Player online : plugin.getServer().getOnlinePlayers()) {
                if (isReceiving(online) && online.hasPermission("anticheat.alerts")) {
                    online.sendMessage(message);
                }
            }
        });

        // Log to console if debug enabled
        if (plugin.getAnticheatConfig().isDebug()) {
            plugin.getLogger().info("[Flag] " + checkName + " | " + flagged.getName()
                + " | VL:" + String.format("%.1f", vl) + " | " + detail);
        }

        // Send to Discord if enabled
        if (plugin.getAnticheatConfig().isDiscordEnabled()) {
            String discordMsg = "**[Anti]** " + flagged.getName() + " failed **" + checkName
                + "** (VL:" + String.format("%.1f", vl) + ") " + detail;
            sendDiscordAlert(discordMsg);
        }
    }

    public void sendCloudAlert(Player flagged, CloudResponse response) {
        String message = AlertMessage.formatCloudResponse(flagged.getName(), response);

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (Player online : plugin.getServer().getOnlinePlayers()) {
                if (isReceiving(online) && online.hasPermission("anticheat.alerts")) {
                    online.sendMessage(message);
                }
            }
        });

        if (plugin.getAnticheatConfig().isDiscordEnabled()) {
            String discordMsg = "**[Anti|Cloud]** " + flagged.getName()
                + " | Probability: " + String.format("%.1f%%", response.getCheatProbability() * 100)
                + " | " + response.getRecommendedAction();
            sendDiscordAlert(discordMsg);
        }
    }

    public void sendDiscordAlert(String message) {
        String webhookUrl = plugin.getAnticheatConfig().getDiscordWebhookUrl();
        if (webhookUrl == null || webhookUrl.isEmpty()) return;

        String mentionRole = plugin.getAnticheatConfig().getDiscordMentionRole();
        String content = (mentionRole != null && !mentionRole.isEmpty())
            ? "<@&" + mentionRole + "> " + message
            : message;

        JsonObject json = new JsonObject();
        json.addProperty("content", content);
        json.addProperty("username", "Anti Anticheat");

        RequestBody body = RequestBody.create(json.toString(), JSON);
        Request request = new Request.Builder().url(webhookUrl).post(body).build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                plugin.getLogger().log(Level.WARNING, "Discord webhook failed: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                response.close();
            }
        });
    }

    public void toggleAlerts(Player player) {
        if (alertReceivers.contains(player.getUniqueId())) {
            alertReceivers.remove(player.getUniqueId());
            player.sendMessage(org.bukkit.ChatColor.RED + "[Anti] Alerts disabled.");
        } else {
            alertReceivers.add(player.getUniqueId());
            player.sendMessage(org.bukkit.ChatColor.GREEN + "[Anti] Alerts enabled.");
        }
    }

    public boolean isReceiving(Player player) {
        return alertReceivers.contains(player.getUniqueId());
    }

    public void removePlayer(UUID uuid) {
        alertReceivers.remove(uuid);
    }
}
