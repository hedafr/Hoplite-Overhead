package com.heda.overhead;

import com.google.gson.JsonObject;
import com.heda.overhead.client.PlayerStatsRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OverHead implements ClientModInitializer {

	private static boolean isOnHoplite = false; // Flag to check if the player is on the Hoplite server

	@Override
	public void onInitializeClient() {
		// Register the "/killsleaderboard" command
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
			dispatcher.register(ClientCommandManager.literal("killsleaderboard")
					.executes(context -> {
						showKillsLeaderboard(); // Show the leaderboard
						return 1;
					}));
		});

		// Register handler for when the player joins a new server
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			PlayerDataCache.resetStats(); // Reset stats on joining
			checkIfOnHopliteServer(client); // Check if the server is Hoplite
		});

		// Listen to incoming chat messages
		ClientReceiveMessageEvents.ALLOW_GAME.register(this::handleChatMessage);

		// Register the player stats renderer
		PlayerStatsRenderer.register();
	}

	/**
	 * Check if the current server is Hoplite and set the flag accordingly.
	 */
	private void checkIfOnHopliteServer(MinecraftClient client) {
		if (client.getCurrentServerEntry() != null &&
				client.getCurrentServerEntry().address.contains("hoplite.gg")) {
			isOnHoplite = true;
		} else {
			isOnHoplite = false; // Reset if the player is not on Hoplite server
		}
	}

	/**
	 * Displays the top 10 players based on kill count in the leaderboard.
	 */
	private void showKillsLeaderboard() {
		Map<String, JsonObject> statsMap = PlayerDataCache.getAllStats();
		List<Map.Entry<String, JsonObject>> sortedList = sortPlayersByKills(statsMap);

		StringBuilder leaderboard = new StringBuilder("§7Kills Leaderboard:\n");
		int count = 0;
		// Build the leaderboard text for the top 10 players
		for (Map.Entry<String, JsonObject> entry : sortedList) {
			if (count >= 10) break;
			int kills = entry.getValue().has("kills") ? entry.getValue().get("kills").getAsInt() : 0;
			leaderboard.append(++count)
					.append(". ")
					.append("§f" + entry.getKey())
					.append("§7 - ")
					.append("§f" + kills)
					.append("§7 kills\n");
		}

		// Send the leaderboard to the player's chat
		MinecraftClient.getInstance().player.sendMessage(Text.of(leaderboard.toString()), false);
	}

	/**
	 * Sorts players by the number of kills in descending order.
	 */
	private List<Map.Entry<String, JsonObject>> sortPlayersByKills(Map<String, JsonObject> statsMap) {
		List<Map.Entry<String, JsonObject>> sortedList = new ArrayList<>(statsMap.entrySet());

		sortedList.sort((entry1, entry2) -> {
			int kills1 = entry1.getValue().has("kills") ? entry1.getValue().get("kills").getAsInt() : 0;
			int kills2 = entry2.getValue().has("kills") ? entry2.getValue().get("kills").getAsInt() : 0;
			return Integer.compare(kills2, kills1); // Sort in descending order by kill count
		});

		return sortedList;
	}

	/**
	 * Handles incoming chat messages for player eliminations.
	 */
	private boolean handleChatMessage(Text message, boolean overlay) {
		if (!overlay && isOnHoplite) {
			processEliminationMessage(message.getString()); // Process the message for eliminations
		}
		return true;
	}

	/**
	 * Process chat messages to detect player eliminations and update kill counts.
	 */
	private static void processEliminationMessage(String message) {
		Pattern pattern = Pattern.compile(
				".* ELIMINATION! (.+?) (?:was (?:slain|killed) by|hit the ground too hard while trying to escape|went up in flames while fighting|was shot by) (.+)"
		);
		Matcher matcher = pattern.matcher(message);
		if (matcher.find()) {
			String killer = matcher.group(2).trim();
			if (isValidKiller(killer)) {
				PlayerDataCache.incrementKill(killer); // Increment the kill count for the killer
			}
		}
	}

	/**
	 * Validates whether the killer is a player and not a mob/environmental cause.
	 */
	private static boolean isValidKiller(String killer) {
		return !(killer.startsWith("a ") || killer.startsWith("an ") || killer.contains(" ") ||
				killer.contains("'s Corpse") || killer.contains("disconnected"));
	}
}
