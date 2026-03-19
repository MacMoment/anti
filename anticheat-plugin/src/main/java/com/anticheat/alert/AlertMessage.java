package com.anticheat.alert;

import com.anticheat.cloud.CloudResponse;
import org.bukkit.ChatColor;

import java.util.List;

public class AlertMessage {

    public static String format(String checkName, String playerName, float violationLevel, String detail) {
        return ChatColor.RED + "[Anti] " +
               ChatColor.YELLOW + playerName +
               ChatColor.WHITE + " failed " +
               ChatColor.RED + checkName +
               ChatColor.WHITE + " (VL:" + String.format("%.1f", violationLevel) + ") " +
               ChatColor.GRAY + detail;
    }

    public static String formatCloud(String playerName, double probability, List<String> cheats) {
        StringBuilder sb = new StringBuilder();
        sb.append(ChatColor.DARK_RED).append("[Anti|Cloud] ");
        sb.append(ChatColor.YELLOW).append(playerName);
        sb.append(ChatColor.WHITE).append(" | Probability: ");
        sb.append(ChatColor.RED).append(String.format("%.1f%%", probability * 100));
        if (cheats != null && !cheats.isEmpty()) {
            sb.append(ChatColor.WHITE).append(" | Cheats: ");
            sb.append(ChatColor.RED).append(String.join(", ", cheats));
        }
        return sb.toString();
    }

    public static String formatCloudResponse(String playerName, CloudResponse response) {
        return formatCloud(
            playerName,
            response.getCheatProbability(),
            response.getDetectedCheats()
        ) + ChatColor.GRAY + " [" + response.getConfidence() + "] " + response.getRecommendedAction();
    }
}
