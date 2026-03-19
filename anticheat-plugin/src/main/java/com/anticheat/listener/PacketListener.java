package com.anticheat.listener;

import com.anticheat.AnticheatPlugin;
import com.anticheat.data.MovementFrame;
import com.anticheat.data.PlayerData;
import com.anticheat.util.PacketUtil;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.entity.Player;

public class PacketListener extends PacketAdapter {

    private final AnticheatPlugin plugin;

    public PacketListener(AnticheatPlugin plugin) {
        super(plugin, ListenerPriority.LOWEST,
            PacketType.Play.Client.FLYING,
            PacketType.Play.Client.POSITION,
            PacketType.Play.Client.POSITION_LOOK,
            PacketType.Play.Client.LOOK,
            PacketType.Play.Client.ARM_ANIMATION,
            PacketType.Play.Client.USE_ENTITY,
            PacketType.Play.Client.ABILITIES
        );
        this.plugin = plugin;
    }

    @Override
    public void onPacketReceiving(PacketEvent event) {
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        if (player == null) return;

        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data == null) return;

        PacketType type = event.getPacketType();

        if (PacketUtil.isMovementPacket(type)) {
            MovementFrame frame = PacketUtil.parseMovementPacket(event, data);
            if (frame == null) return;

            data.addMovementFrame(frame);

            // Tick velocity countdown
            data.tickVelocity();

            // Update last valid position if on ground
            if (frame.isOnGround()) {
                data.setLastValidPosition(frame.getX(), frame.getY(), frame.getZ());
            }

            // Run movement checks async
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () ->
                plugin.getCheckManager().processMovement(player, data, frame));

        } else if (type == PacketType.Play.Client.ARM_ANIMATION) {
            // Record click timestamp for CPS tracking
            data.addClick(System.currentTimeMillis());

        } else if (type == PacketType.Play.Client.ABILITIES) {
            // Update flying state from client abilities packet
            try {
                boolean flying = event.getPacket().getBooleans().read(1);
                data.setFlying(flying || player.getGameMode() == org.bukkit.GameMode.CREATIVE
                    || player.getGameMode() == org.bukkit.GameMode.SPECTATOR);
            } catch (Exception ignored) {}
        }
    }
}
