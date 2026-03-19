package com.anticheat.check;

import com.anticheat.AnticheatPlugin;
import com.anticheat.data.PlayerData;
import com.anticheat.mitigation.MitigationEngine;
import org.bukkit.entity.Player;

public abstract class Check {

    protected final AnticheatPlugin plugin;
    private final String name;
    private final String type;
    private final float maxViolations;
    private final float decayRate;

    protected Check(AnticheatPlugin plugin, String name, String type, float maxViolations, float decayRate) {
        this.plugin = plugin;
        this.name = name;
        this.type = type;
        this.maxViolations = maxViolations;
        this.decayRate = decayRate;
    }

    /**
     * Run the check against the player's data.
     * @param data   the player's accumulated data
     * @param context  check-specific context object (MovementFrame, Entity target, etc.)
     * @return a CheckResult indicating pass / flag / exempt
     */
    public abstract CheckResult check(PlayerData data, Object context);

    /**
     * Called when this check flags the player. Increments VL and delegates to the MitigationEngine.
     */
    public void onFlag(Player player, PlayerData data, CheckResult result) {
        float newVl = data.addViolation(name, result.getSeverity());
        MitigationEngine engine = plugin.getMitigationEngine();
        engine.handleViolation(player, data, name, newVl);
        plugin.getAlertManager().sendAlert(name, player, newVl, result.getDescription());
    }

    public String getName() { return name; }
    public String getType() { return type; }
    public float getMaxViolations() { return maxViolations; }
    public float getDecayRate() { return decayRate; }
}
