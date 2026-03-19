package com.anticheat.command;

import com.anticheat.AnticheatPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AlertsCommand implements CommandExecutor {

    private final AnticheatPlugin plugin;

    public AlertsCommand(AnticheatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can toggle alerts.");
            return true;
        }
        if (!player.hasPermission("anticheat.alerts")) {
            player.sendMessage(ChatColor.RED + "You don't have permission.");
            return true;
        }

        plugin.getAlertManager().toggleAlerts(player);

        boolean nowReceiving = plugin.getAlertManager().isReceiving(player);
        player.sendMessage(ChatColor.GOLD + "[Anti] Alerts are now "
            + (nowReceiving ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled")
            + ChatColor.GOLD + ".");

        return true;
    }
}
