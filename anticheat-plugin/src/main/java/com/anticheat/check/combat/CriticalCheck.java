package com.anticheat.check.combat;

import com.anticheat.AnticheatPlugin;
import com.anticheat.check.Check;
import com.anticheat.check.CheckResult;
import com.anticheat.data.MovementFrame;
import com.anticheat.data.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public class CriticalCheck extends Check {

    public CriticalCheck(AnticheatPlugin plugin) {
        super(plugin, "Critical", "combat", 10.0f, 0.2f);
    }

    @Override
    public CheckResult check(PlayerData data, Object context) {
        if (!(context instanceof Entity)) return CheckResult.exempt();

        Player player = plugin.getServer().getPlayer(data.getPlayerUUID());
        if (player == null) return CheckResult.exempt();

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return CheckResult.exempt();
        }

        // Critical hits require: falling (not on ground), not sprinting, not in liquid, not blind
        MovementFrame latest = data.getLastMovementFrame();
        if (latest == null) return CheckResult.pass();

        boolean onGround = latest.isOnGround();
        boolean sprinting = latest.isSprinting();
        double velocityY = latest.getVelocityY();

        // If on ground or velocity is upward/zero, can't be a crit
        if (onGround) {
            // The player is sending attack while claiming to be on ground
            // Vanilla crits require being in the air (falling)
            // A crit on the ground is possible with NoFall/Crit hacks
            if (velocityY >= 0) {
                return CheckResult.flag(1.5f,
                    String.format("critical on ground: velY=%.4f onGround=%s", velocityY, onGround));
            }
        }

        // Sprinting crits are not possible in vanilla
        if (sprinting && velocityY < -0.05) {
            return CheckResult.flag(1.0f, "sprinting crit attempt");
        }

        return CheckResult.pass();
    }
}
