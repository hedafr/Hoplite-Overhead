package com.heda.overhead;

import com.google.gson.JsonObject;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;
import java.util.Map;

/**
 * A thread-safe cache for storing and managing player statistics.
 */
public class PlayerDataCache {
    // Using ConcurrentHashMap for thread-safe operations
    private static final Map<String, JsonObject> playerStats = new ConcurrentHashMap<>();

    /**
     * Updates the stats for a given player.
     *
     * @param playerName The name of the player.
     * @param stats      The JsonObject containing the player's stats.
     */
    public static void updateStats(String playerName, JsonObject stats) {
        playerStats.put(playerName, stats);
    }

    /**
     * Retrieves the stats for a given player.
     *
     * @param playerName The name of the player.
     * @return The JsonObject containing the player's stats, or null if not found.
     */
    public static JsonObject getStats(String playerName) {
        return playerStats.get(playerName);
    }

    /**
     * Increments the kill count for a given player.
     *
     * @param playerName The name of the player.
     */
    public static void incrementKill(String playerName) {
        // Retrieve existing stats or create a new JsonObject if none exist
        JsonObject stats = playerStats.computeIfAbsent(playerName, k -> new JsonObject());
        // Get the current number of kills, defaulting to 0 if not present
        int kills = stats.has("kills") && !stats.get("kills").isJsonNull()
                ? stats.get("kills").getAsInt()
                : 0;
        // Update the kills count
        stats.addProperty("kills", kills + 1);
        // Save the updated stats back to the cache
        playerStats.put(playerName, stats);
    }

    /**
     * Clears all stored player stats.
     */
    public static void resetStats() {
        playerStats.clear();
    }

    /**
     * Retrieves a map of all player stats.
     *
     * @return A map where the key is the player's name and the value is their stats.
     */
    public static Map<String, JsonObject> getAllStats() {
        return new HashMap<>(playerStats);
    }
}
