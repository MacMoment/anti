package com.anticheat.command;

import com.anticheat.AnticheatPlugin;
import com.anticheat.data.PlayerData;
import com.anticheat.mitigation.MitigationAction;
import com.anticheat.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AnticheatCommand implements CommandExecutor, TabCompleter {

    private final AnticheatPlugin plugin;

    private static final List<String> SUB_COMMANDS = Arrays.asList(
        "alerts", "status", "profile", "mitigate", "reload", "info"
    );

    public AnticheatCommand(AnticheatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("anticheat.use")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission.");
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "alerts" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can toggle alerts.");
                    return true;
                }
                plugin.getAlertManager().toggleAlerts(player);
            }
            case "status" -> handleStatus(sender, args);
            case "profile" -> handleProfile(sender, args);
            case "mitigate" -> handleMitigate(sender, args);
            case "reload" -> {
                if (!sender.hasPermission("anticheat.admin")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission.");
                    return true;
                }
                plugin.getAnticheatConfig().reload();
                sender.sendMessage(ChatColor.GREEN + "[Anti] Configuration reloaded.");
            }
            case "info" -> handleInfo(sender);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void handleStatus(CommandSender sender, String[] args) {
        if (!sender.hasPermission("anticheat.staff")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /ac status <player>");
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
            return;
        }
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(target.getUniqueId());
        if (data == null) {
            sender.sendMessage(ChatColor.RED + "No data found for " + args[1]);
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "=== " + target.getName() + " VL Status ===");
        sender.sendMessage(ChatColor.YELLOW + "Session: " + TimeUtil.formatDuration(
            System.currentTimeMillis() - data.getSessionStart()));

        Map<String, Float> vls = data.getViolationLevels();
        if (vls.isEmpty()) {
            sender.sendMessage(ChatColor.GREEN + "No violations recorded.");
        } else {
            for (Map.Entry<String, Float> entry : vls.entrySet()) {
                float threshold = plugin.getAnticheatConfig().getMitigationThreshold(entry.getKey());
                ChatColor color = entry.getValue() >= threshold ? ChatColor.RED : ChatColor.YELLOW;
                sender.sendMessage(String.format("%s%s: %.1f / %.1f",
                    color, entry.getKey(), entry.getValue(), threshold));
            }
        }
    }

    private void handleProfile(CommandSender sender, String[] args) {
        if (!sender.hasPermission("anticheat.staff")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /ac profile <player>");
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
            return;
        }
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(target.getUniqueId());
        if (data == null) {
            sender.sendMessage(ChatColor.RED + "No data found for " + args[1]);
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "Triggering cloud profile sync for " + target.getName() + "...");
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getCloudConnector().sendAsync(data);
            sender.sendMessage(ChatColor.GREEN + "Cloud sync initiated for " + target.getName() + ".");
        });
    }

    private void handleMitigate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("anticheat.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission.");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /ac mitigate <player> <action>");
            sender.sendMessage(ChatColor.GRAY + "Actions: " +
                Arrays.stream(MitigationAction.values())
                    .map(Enum::name)
                    .collect(Collectors.joining(", ")));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
            return;
        }
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(target.getUniqueId());
        if (data == null) {
            sender.sendMessage(ChatColor.RED + "No data found for " + args[1]);
            return;
        }

        MitigationAction action = MitigationAction.fromString(args[2]);
        plugin.getMitigationEngine().applyMitigation(target, data, action);
        sender.sendMessage(ChatColor.GREEN + "Applied " + action.name() + " to " + target.getName());
    }

    private void handleInfo(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Anti Anticheat ===");
        sender.sendMessage(ChatColor.YELLOW + "Version: " + plugin.getDescription().getVersion());
        sender.sendMessage(ChatColor.YELLOW + "Checks loaded: " + plugin.getCheckManager().getAllChecks().size());
        sender.sendMessage(ChatColor.YELLOW + "Players monitored: " + plugin.getPlayerDataManager().getPlayerCount());
        sender.sendMessage(ChatColor.YELLOW + "Cloud enabled: " + plugin.getAnticheatConfig().isCloudEnabled());
        sender.sendMessage(ChatColor.YELLOW + "Discord enabled: " + plugin.getAnticheatConfig().isDiscordEnabled());
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Anti Anticheat Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/ac alerts" + ChatColor.WHITE + " - Toggle alert notifications");
        sender.sendMessage(ChatColor.YELLOW + "/ac status <player>" + ChatColor.WHITE + " - View player VLs");
        sender.sendMessage(ChatColor.YELLOW + "/ac profile <player>" + ChatColor.WHITE + " - Trigger cloud profile sync");
        sender.sendMessage(ChatColor.YELLOW + "/ac mitigate <player> <action>" + ChatColor.WHITE + " - Apply mitigation");
        sender.sendMessage(ChatColor.YELLOW + "/ac reload" + ChatColor.WHITE + " - Reload configuration");
        sender.sendMessage(ChatColor.YELLOW + "/ac info" + ChatColor.WHITE + " - Plugin information");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : SUB_COMMANDS) {
                if (sub.startsWith(args[0].toLowerCase())) completions.add(sub);
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("status") || sub.equals("profile") || sub.equals("mitigate")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(p.getName());
                    }
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("mitigate")) {
            for (MitigationAction action : MitigationAction.values()) {
                if (action.name().toLowerCase().startsWith(args[2].toLowerCase())) {
                    completions.add(action.name());
                }
            }
        }
        return completions;
    }
}
