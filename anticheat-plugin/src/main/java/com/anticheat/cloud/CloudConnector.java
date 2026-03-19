package com.anticheat.cloud;

import com.anticheat.AnticheatPlugin;
import com.anticheat.data.PlayerData;
import com.anticheat.mitigation.MitigationAction;
import com.anticheat.mitigation.MitigationEngine;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import okhttp3.*;
import org.bukkit.entity.Player;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class CloudConnector {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final AnticheatPlugin plugin;
    private final OkHttpClient client;
    private final Gson gson;

    public CloudConnector(AnticheatPlugin plugin) {
        this.plugin = plugin;
        this.client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
        this.gson = new GsonBuilder().create();
    }

    public void startSyncScheduler() {
        long intervalTicks = plugin.getAnticheatConfig().getSyncInterval() * 20L;
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (!plugin.getAnticheatConfig().isCloudEnabled()) return;
            long now = System.currentTimeMillis();
            long syncInterval = plugin.getAnticheatConfig().getSyncInterval() * 1000L;
            for (PlayerData data : plugin.getPlayerDataManager().getAllPlayers()) {
                if (now - data.getLastCloudSync() >= syncInterval) {
                    sendAsync(data);
                    data.setLastCloudSync(now);
                }
            }
        }, intervalTicks, intervalTicks);
    }

    public void sendAsync(PlayerData data) {
        if (!plugin.getAnticheatConfig().isCloudEnabled()) return;
        String apiUrl = plugin.getAnticheatConfig().getCloudApiUrl();
        if (apiUrl == null || apiUrl.isEmpty()) return;

        List<CloudPayload.MovementSample> movementSamples = new ArrayList<>();
        for (var frame : data.getMovementHistory(50)) {
            movementSamples.add(CloudPayload.MovementSample.from(frame));
        }

        CloudPayload payload = CloudPayload.builder()
            .playerUUID(data.getPlayerUUID().toString())
            .playerName(data.getPlayerName())
            .serverID(plugin.getAnticheatConfig().getServerID())
            .movementSamples(movementSamples)
            .clickTimestamps(new ArrayList<>(data.getClickTimestamps()))
            .violationHistory(new HashMap<>(data.getViolationLevels()))
            .sessionDuration(System.currentTimeMillis() - data.getSessionStart())
            .build();

        String json = gson.toJson(payload);
        long ts = System.currentTimeMillis();
        String signature = sign(json + ts, plugin.getAnticheatConfig().getCloudApiKey());

        RequestBody body = RequestBody.create(json, JSON);
        Request request = new Request.Builder()
            .url(apiUrl + "/analyze")
            .header("X-Timestamp", String.valueOf(ts))
            .header("X-Signature", signature)
            .header("Content-Type", "application/json")
            .post(body)
            .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                plugin.getLogger().log(Level.WARNING,
                    "Cloud sync failed for " + data.getPlayerName() + ": " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody responseBody = response.body()) {
                    if (!response.isSuccessful() || responseBody == null) {
                        plugin.getLogger().warning("Cloud API returned " + response.code()
                            + " for " + data.getPlayerName());
                        return;
                    }
                    String responseJson = responseBody.string();
                    CloudResponse cloudResponse = gson.fromJson(responseJson, CloudResponse.class);
                    handleCloudResponse(data, cloudResponse);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to parse cloud response", e);
                }
            }
        });
    }

    private void handleCloudResponse(PlayerData data, CloudResponse response) {
        if (response == null) return;
        if (response.getCheatProbability() > 0.7) {
            Player player = plugin.getServer().getPlayer(data.getPlayerUUID());
            if (player == null) return;

            plugin.getAlertManager().sendCloudAlert(player, response);

            MitigationEngine engine = plugin.getMitigationEngine();
            if (response.getCheatProbability() > 0.9) {
                engine.applyMitigation(player, data, MitigationAction.FLAG_FOR_BAN);
            } else {
                engine.applyMitigation(player, data, MitigationAction.MONITOR);
            }
        }
    }

    private String sign(String data, String key) {
        if (key == null || key.isEmpty()) return "";
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "HMAC signing failed", e);
            return "";
        }
    }

    public void shutdown() {
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }
}
