package com.anticheat.data;

import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerDataManager {

    private final ConcurrentHashMap<UUID, PlayerData> players = new ConcurrentHashMap<>();

    public PlayerData createPlayer(Player player) {
        PlayerData data = new PlayerData(player.getUniqueId(), player.getName());
        data.setLastValidPosition(
            player.getLocation().getX(),
            player.getLocation().getY(),
            player.getLocation().getZ()
        );
        players.put(player.getUniqueId(), data);
        return data;
    }

    public void removePlayer(UUID uuid) {
        players.remove(uuid);
    }

    public PlayerData getPlayerData(UUID uuid) {
        return players.get(uuid);
    }

    public Collection<PlayerData> getAllPlayers() {
        return players.values();
    }

    public int getPlayerCount() {
        return players.size();
    }
}
