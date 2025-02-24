package com.heda.overhead;

import com.google.gson.JsonObject;
import com.heda.overhead.client.PlayerStatsRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OverHead implements ClientModInitializer {
	// Flag to determine if the player is connected to the Hoplite server
	private static boolean isOnHoplite = false;

	@Override
	public void onInitializeClient() {
		// Register the /killsleaderboard command
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
			dispatcher.register(ClientCommandManager.literal("killsleaderboard")
					.executes(context -> {
						showKillsLeaderboard();
						return 1;
					}));
		});

		// Register event handler for player joining a server
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			// Reset player stats upon joining a new server
			PlayerDataCache.resetStats();

			// Check if the connected server is the Hoplite server
			if (client.getCurrentServerEntry() != null &&
					client.getCurrentServerEntry().address.contains("hoplite.gg")) {
				isOnHoplite = true;
			} else {
				isOnHoplite = false; // Reset flag when leaving
			}
		});

		// Register event handler for receiving game messages
		ClientReceiveMessageEvents.ALLOW_GAME.register(this::handleChatMessage);

		// Register the player stats renderer
		PlayerStatsRenderer.register();
	}

	/**
	 * Displays the top 10 players with the highest kill counts druing the current game in the chat.
	 */
	private void showKillsLeaderboard() {
		// Retrieve the stats map from PlayerDataCache
		Map<String, JsonObject> statsMap = PlayerDataCache.getAllStats();
		// Create a list of map entries for sorting
		List<Map.Entry<String, JsonObject>> sortedList = new ArrayList<>(statsMap.entrySet());

		// Sort the list in descending order based on kill count
		sortedList.sort((entry1, entry2) -> {
			int kills1 = entry1.getValue().has("kills") && !entry1.getValue().get("kills").isJsonNull()
					? entry1.getValue().get("kills").getAsInt() : 0;
			int kills2 = entry2.getValue().has("kills") && !entry2.getValue().get("kills").isJsonNull()
					? entry2.getValue().get("kills").getAsInt() : 0;
			return Integer.compare(kills2, kills1);
		});

		// Build the leaderboard text (limit to top 10)
		StringBuilder leaderboard = new StringBuilder("§7Kills Leaderboard:\n");
		int count = 0;
		for (Map.Entry<String, JsonObject> entry : sortedList) {
			if (count >= 10) break;

			int kills = entry.getValue().has("kills") && !entry.getValue().get("kills").isJsonNull()
					? entry.getValue().get("kills").getAsInt() : 0;

			leaderboard.append(++count)
					.append(". ")
					.append("§f"+entry.getKey())
					.append("§7 - ")
					.append(kills)
					.append(" §7kills\n");
		}

		// Send the leaderboard to the player as a chat message
		MinecraftClient.getInstance().player.sendMessage(Text.of(leaderboard.toString()), false);
	}

	/**
	 * Handles incoming game messages to detect and process elimination events.
	 *
	 * @param message The received message text.
	 * @param overlay Indicates if the message is an overlay.
	 * @return true to allow the message to be displayed; false otherwise.
	 */
	private boolean handleChatMessage(Text message, boolean overlay) {
		if (!overlay && isOnHoplite) {
			processEliminationMessage(message.getString());
		}
		return true;
	}

	/**
	 * Processes elimination messages to update player kill counts.
	 *
	 * @param message The chat message string.
	 */
	private static void processEliminationMessage(String message) {
		// Pattern to match elimination messages (needs more work)
		Pattern pattern = Pattern.compile(
				".* ELIMINATION! (.+?) (?:was (?:slain|killed) by|hit the ground too hard while trying to escape|went up in flames while fighting|was shot by) (.+)"
		);
		Matcher matcher = pattern.matcher(message);
		if (matcher.find()) {
			String killer = matcher.group(2).trim();

			// Filter out non-player eliminations
			if (killer.startsWith("a ") || killer.startsWith("an ") || killer.contains(" ") ||
					killer.contains("'s Corpse") || killer.contains("disconnected")) {
				return;
			}

			// Increment the killer's kill count
			PlayerDataCache.incrementKill(killer);
		}
	}
}
