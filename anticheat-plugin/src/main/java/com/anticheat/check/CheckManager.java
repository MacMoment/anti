package com.anticheat.check;

import com.anticheat.AnticheatPlugin;
import com.anticheat.check.combat.*;
import com.anticheat.check.movement.*;
import com.anticheat.check.packet.*;
import com.anticheat.check.player.*;
import com.anticheat.data.PlayerData;
import com.anticheat.data.MovementFrame;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class CheckManager {

    private final AnticheatPlugin plugin;
    private final List<Check> allChecks = new ArrayList<>();

    public CheckManager(AnticheatPlugin plugin) {
        this.plugin = plugin;
        registerDefaultChecks();
    }

    private void registerDefaultChecks() {
        // Movement checks
        registerCheck(new SpeedCheck(plugin));
        registerCheck(new FlyCheck(plugin));
        registerCheck(new NoFallCheck(plugin));
        registerCheck(new StepCheck(plugin));
        registerCheck(new PhaseCheck(plugin));
        registerCheck(new VelocityCheck(plugin));

        // Combat checks
        registerCheck(new ReachCheck(plugin));
        registerCheck(new KillAuraCheck(plugin));
        registerCheck(new AimbotCheck(plugin));
        registerCheck(new AutoClickerCheck(plugin));
        registerCheck(new CriticalCheck(plugin));

        // Player checks
        registerCheck(new NoSlowCheck(plugin));
        registerCheck(new ScaffoldCheck(plugin));
        registerCheck(new TimerCheck(plugin));
        registerCheck(new FastUseCheck(plugin));

        // Packet checks
        registerCheck(new BadPacketsCheck(plugin));
        registerCheck(new InvalidRotationCheck(plugin));
    }

    public void registerCheck(Check check) {
        allChecks.add(check);
    }

    public void unregisterCheck(String name) {
        allChecks.removeIf(c -> c.getName().equalsIgnoreCase(name));
    }

    public void processMovement(Player player, PlayerData data, MovementFrame frame) {
        for (Check check : allChecks) {
            if (!check.getType().equals("movement") && !check.getType().equals("player") && !check.getType().equals("packet")) {
                continue;
            }
            if (!plugin.getAnticheatConfig().isCheckEnabled(check.getName())) continue;
            try {
                CheckResult result = check.check(data, frame);
                if (result.isFlagged()) {
                    check.onFlag(player, data, result);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                    "Exception in check " + check.getName() + " for player " + player.getName(), e);
            }
        }
    }

    public void processCombat(Player player, PlayerData data, Entity target) {
        for (Check check : allChecks) {
            if (!check.getType().equals("combat")) continue;
            if (!plugin.getAnticheatConfig().isCheckEnabled(check.getName())) continue;
            try {
                CheckResult result = check.check(data, target);
                if (result.isFlagged()) {
                    check.onFlag(player, data, result);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                    "Exception in combat check " + check.getName() + " for player " + player.getName(), e);
            }
        }
    }

    public void processPacket(Player player, PlayerData data, PacketEvent event) {
        for (Check check : allChecks) {
            if (!check.getType().equals("packet")) continue;
            if (!plugin.getAnticheatConfig().isCheckEnabled(check.getName())) continue;
            try {
                CheckResult result = check.check(data, event);
                if (result.isFlagged()) {
                    check.onFlag(player, data, result);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                    "Exception in packet check " + check.getName() + " for player " + player.getName(), e);
            }
        }
    }

    public void startDecayTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (PlayerData data : plugin.getPlayerDataManager().getAllPlayers()) {
                    for (Check check : allChecks) {
                        data.decayViolation(check.getName(), check.getDecayRate());
                    }
                }
            }
        }.runTaskTimerAsynchronously(plugin, 40L, 40L);
    }

    public List<Check> getAllChecks() { return allChecks; }
}
