package com.anticheat.physics;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.List;

public class CollisionHandler {

    // Player bounding box dimensions
    private static final double PLAYER_WIDTH = 0.6;
    private static final double PLAYER_HEIGHT = 1.8;
    private static final double HALF_WIDTH = PLAYER_WIDTH / 2.0;

    /**
     * Check whether the movement from {@code from} to {@code to} passes through any solid block.
     * @return true if a collision is detected (player phased through a solid block)
     */
    public boolean checkCollision(Location from, Location to, Player player) {
        World world = from.getWorld();
        if (world == null) return false;

        List<Block> blocksInPath = getBlocksInPath(from, to);
        if (blocksInPath.isEmpty()) return false;

        // Build a bounding box for the player's path
        double minX = Math.min(from.getX(), to.getX()) - HALF_WIDTH;
        double maxX = Math.max(from.getX(), to.getX()) + HALF_WIDTH;
        double minY = Math.min(from.getY(), to.getY());
        double maxY = Math.max(from.getY(), to.getY()) + PLAYER_HEIGHT;
        double minZ = Math.min(from.getZ(), to.getZ()) - HALF_WIDTH;
        double maxZ = Math.max(from.getZ(), to.getZ()) + HALF_WIDTH;
        BoundingBox pathBox = new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);

        for (Block block : blocksInPath) {
            if (!isSolid(block.getType())) continue;

            BoundingBox blockBox = block.getBoundingBox();
            if (pathBox.overlaps(blockBox)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get all blocks that lie along the path between two locations.
     */
    public List<Block> getBlocksInPath(Location from, Location to) {
        List<Block> blocks = new ArrayList<>();
        World world = from.getWorld();
        if (world == null) return blocks;

        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (dist == 0) return blocks;

        int steps = (int) Math.ceil(dist * 4); // sample every 0.25 blocks
        double stepX = dx / steps;
        double stepY = dy / steps;
        double stepZ = dz / steps;

        for (int i = 0; i <= steps; i++) {
            double x = from.getX() + stepX * i;
            double y = from.getY() + stepY * i;
            double z = from.getZ() + stepZ * i;
            Block block = world.getBlockAt((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
            if (!blocks.contains(block)) {
                blocks.add(block);
            }
        }

        return blocks;
    }

    private boolean isSolid(Material mat) {
        if (mat.isAir()) return false;
        // Exclude non-solid passable blocks
        if (mat == Material.GRASS || mat == Material.TALL_GRASS || mat == Material.FERN
            || mat == Material.LARGE_FERN || mat == Material.DEAD_BUSH
            || mat == Material.VINE || mat == Material.LADDER || mat == Material.WATER
            || mat == Material.LAVA || mat == Material.SNOW) {
            return false;
        }
        return mat.isSolid();
    }
}
