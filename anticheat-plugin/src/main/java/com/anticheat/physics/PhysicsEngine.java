package com.anticheat.physics;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Collection;

public class PhysicsEngine {

    public static final double GRAVITY = 0.08;
    public static final double DRAG = 0.98;
    public static final double JUMP_VELOCITY = 0.42;

    /**
     * Predict the next Y coordinate given current state and physics.
     */
    public double predictNextY(double currentY, double velocityY, boolean onGround,
                               Collection<PotionEffect> effects) {
        if (onGround) return currentY;

        double velY = velocityY;

        // Apply Jump Boost potion
        for (PotionEffect effect : effects) {
            if (effect.getType().equals(PotionEffectType.JUMP)) {
                // Jump boost increases jump velocity
                velY += (effect.getAmplifier() + 1) * 0.1;
            }
            if (effect.getType().equals(PotionEffectType.SLOW_FALLING)) {
                // Slow falling reduces gravity significantly
                return currentY + Math.max(velY * DRAG, -0.005);
            }
            if (effect.getType().equals(PotionEffectType.LEVITATION)) {
                double levitationVel = 0.05 * (effect.getAmplifier() + 1) - velY;
                return currentY + (velY + levitationVel) * DRAG;
            }
        }

        // Standard gravity: velocity decreases by gravity then multiplied by drag
        double newVelY = (velY - GRAVITY) * DRAG;
        return currentY + newVelY;
    }

    /**
     * Calculate the maximum horizontal speed for a player given their state.
     */
    public double getMaxHorizontalSpeed(boolean sprinting, boolean sneaking, boolean onGround,
                                        Collection<PotionEffect> effects, Material blockBelow) {
        double base;
        if (sprinting) {
            base = 0.2806;
        } else if (sneaking) {
            base = 0.074;
        } else {
            base = 0.221;
        }

        for (PotionEffect effect : effects) {
            if (effect.getType().equals(PotionEffectType.SPEED)) {
                base += (effect.getAmplifier() + 1) * 0.2 * 0.221;
            }
            if (effect.getType().equals(PotionEffectType.SLOW)) {
                base -= (effect.getAmplifier() + 1) * 0.15 * 0.221;
                base = Math.max(base, 0.01);
            }
        }

        if (blockBelow != null) {
            if (blockBelow == Material.SOUL_SAND || blockBelow == Material.SOUL_SOIL) {
                base *= 0.4;
            } else if (blockBelow == Material.ICE || blockBelow == Material.PACKED_ICE
                    || blockBelow == Material.BLUE_ICE || blockBelow == Material.FROSTED_ICE) {
                base *= 2.5;
            } else if (blockBelow == Material.SLIME_BLOCK) {
                base *= 0.8;
            }
        }

        return base;
    }

    /**
     * Returns true if the given material is a climbable surface.
     */
    public boolean isClimbable(Material material) {
        return material == Material.LADDER
            || material == Material.VINE
            || material == Material.TWISTING_VINES
            || material == Material.WEEPING_VINES
            || material == Material.TWISTING_VINES_PLANT
            || material == Material.WEEPING_VINES_PLANT
            || material == Material.SCAFFOLDING;
    }

    /**
     * Returns true if the location is inside a liquid block.
     */
    public boolean isInLiquid(Location location) {
        Block block = location.getBlock();
        Material mat = block.getType();
        return mat == Material.WATER || mat == Material.LAVA
            || mat == Material.BUBBLE_COLUMN;
    }

    /**
     * Apply one tick of movement physics to the given velocity, returning the new velocity.
     * @param velocity [vx, vy, vz]
     */
    public double[] applyMovementPhysics(double[] velocity, boolean onGround,
                                          boolean sprinting, boolean sneaking) {
        double vx = velocity[0];
        double vy = velocity[1];
        double vz = velocity[2];

        if (!onGround) {
            vy = (vy - GRAVITY) * DRAG;
        } else {
            vy = 0;
        }

        double horizDrag = onGround ? 0.546 : 0.91;
        vx *= horizDrag;
        vz *= horizDrag;

        return new double[]{vx, vy, vz};
    }
}
