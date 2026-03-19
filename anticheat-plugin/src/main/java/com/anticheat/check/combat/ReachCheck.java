package com.anticheat.check.combat;

import com.anticheat.AnticheatPlugin;
import com.anticheat.check.Check;
import com.anticheat.check.CheckResult;
import com.anticheat.data.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

public class ReachCheck extends Check {

    private static final double SURVIVAL_MAX_REACH = 3.0;
    private static final double CREATIVE_MAX_REACH = 5.0;
    private static final double LAG_COMPENSATION = 0.1;

    public ReachCheck(AnticheatPlugin plugin) {
        super(plugin, "Reach", "combat", 10.0f, 0.2f);
    }

    @Override
    public CheckResult check(PlayerData data, Object context) {
        if (!(context instanceof Entity target)) return CheckResult.exempt();

        Player player = plugin.getServer().getPlayer(data.getPlayerUUID());
        if (player == null) return CheckResult.exempt();

        double maxReach = (player.getGameMode() == GameMode.CREATIVE) ? CREATIVE_MAX_REACH : SURVIVAL_MAX_REACH;

        // Use eye location for distance measurement
        Location eyeLocation = player.getEyeLocation();

        // Calculate closest distance from eye to target bounding box
        double distance = getDistanceToHitbox(eyeLocation, target);

        if (distance > maxReach + LAG_COMPENSATION) {
            float severity = (float) Math.min((distance - maxReach) * 2.0, 5.0);
            return CheckResult.flag(severity,
                String.format("reach=%.4f max=%.2f", distance, maxReach));
        }

        return CheckResult.pass();
    }

    private double getDistanceToHitbox(Location eye, Entity target) {
        BoundingBox box = target.getBoundingBox();

        // Clamp the eye position to the bounding box on each axis
        double closestX = Math.max(box.getMinX(), Math.min(eye.getX(), box.getMaxX()));
        double closestY = Math.max(box.getMinY(), Math.min(eye.getY(), box.getMaxY()));
        double closestZ = Math.max(box.getMinZ(), Math.min(eye.getZ(), box.getMaxZ()));

        double dx = eye.getX() - closestX;
        double dy = eye.getY() - closestY;
        double dz = eye.getZ() - closestZ;

        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
