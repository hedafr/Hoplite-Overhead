package com.heda.overhead.client;

import com.google.gson.JsonObject;
import com.heda.overhead.PlayerDataCache;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

public class PlayerStatsRenderer {

    public static void register() {
        WorldRenderEvents.LAST.register(PlayerStatsRenderer::onWorldRender);
    }

    /**
     * The main render method that iterates over all players and renders stats above their heads.
     *
     * @param context The current world render context.
     */
    private static void onWorldRender(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            return;
        }

        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider.Immediate immediate = client.getBufferBuilders().getEntityVertexConsumers();
        TextRenderer textRenderer = client.textRenderer;
        Vec3d cameraPos = context.camera().getPos();

        // Process each player in the world
        for (PlayerEntity player : client.world.getPlayers()) {
            if (player == client.player || player.isInvisible()) {
                continue;
            }
            renderPlayerStats(player, client, context, matrices, immediate, textRenderer, cameraPos);
        }

        // Flush all buffered vertex data
        immediate.draw();
    }

    /**
     * Renders the stats (health, kills) for a given player.
     *
     * @param player         The player entity whose stats are being rendered.
     * @param client         The Minecraft client instance.
     * @param context        The current world render context.
     * @param matrices       The matrix stack for transforming rendering.
     * @param immediate      The vertex consumer for immediate drawing.
     * @param textRenderer   The text renderer used for drawing the stats text.
     * @param cameraPos      The camera's current position.
     */
    private static void renderPlayerStats(PlayerEntity player, MinecraftClient client, WorldRenderContext context,
                                          MatrixStack matrices, VertexConsumerProvider.Immediate immediate,
                                          TextRenderer textRenderer, Vec3d cameraPos) {
        // Retrieve the player's stats from the cache; skip rendering if not available.
        JsonObject stats = PlayerDataCache.getStats(player.getName().getString());
        if (stats == null) {
            return;
        }

        // Extract kills (default to 0 if missing)
        int kills = stats.has("kills") ? stats.get("kills").getAsInt() : 0;

        // Retrieve the player's health from the scoreboard
        int health = getPlayerHealth(player, client);
        if (health <= 0) {
            return; // Skip rendering if health is 0 or less
        }
        String healthColor = getHealthColor(health);
        String statsText = String.format("§7[%s%d HP§7] §7%d K", healthColor, health, kills);

        // Interpolate the player's position for smooth rendering and offset above the head
        float partialTicks = RenderSystem.getShaderGameTime();
        Vec3d interpolatedPos = getInterpolatedPosition(player, partialTicks)
                .add(0, player.getHeight() + 1, 0);

        // Calculate a dynamic scaling factor based on the distance to the camera
        double distance = cameraPos.distanceTo(interpolatedPos);
        float scale = calculateScale(distance);

        // Translate the position relative to the camera
        Vec3d relativePos = interpolatedPos.subtract(cameraPos);
        matrices.push();
        matrices.translate(relativePos.x, relativePos.y, relativePos.z);

        // Rotate so that the text always faces the camera
        matrices.multiply(context.camera().getRotation());
        matrices.multiply(new Quaternionf().rotationY((float) Math.PI));

        // Apply scaling transformation
        matrices.scale(-scale, -scale, scale);

        // Center the text horizontally
        float textWidth = textRenderer.getWidth(statsText);
        float xOffset = -textWidth / 2.0f;
        int light = LightmapTextureManager.pack(15, 15);

        // Render the text with disabled depth testing to ensure visibility
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        textRenderer.draw(
                statsText,
                xOffset,
                0,
                0xFFFFFFFF,
                false,
                matrices.peek().getPositionMatrix(),
                immediate,
                TextRenderer.TextLayerType.NORMAL,
                0x80000000,
                light
        );
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        matrices.pop();
    }

    /**
     * Calculates the interpolated position of the player based on partial ticks.
     *
     * @param player       The player entity.
     * @param partialTicks The partial tick value for interpolation.
     * @return The interpolated position as a Vec3d.
     */
    private static Vec3d getInterpolatedPosition(PlayerEntity player, float partialTicks) {
        double x = player.prevX + (player.getX() - player.prevX) * partialTicks;
        double y = player.prevY + (player.getY() - player.prevY) * partialTicks;
        double z = player.prevZ + (player.getZ() - player.prevZ) * partialTicks;
        return new Vec3d(x, y, z);
    }

    /**
     * Calculates a dynamic scale for the text based on distance.
     *
     * @param distance The distance from the camera to the player.
     * @return The computed scale value.
     */
    private static float calculateScale(double distance) {
        float baseScale = 0.02f;
        float scale = (float) (baseScale * (1 + distance * 0.1f));
        return Math.max(0.03f, Math.min(scale, 0.12f));
    }

    /**
     * Retrieves the player's health score from the scoreboard.
     *
     * @param player The player entity.
     * @param client The Minecraft client instance.
     * @return The player's health value, or 0 if not available.
     */
    private static int getPlayerHealth(PlayerEntity player, MinecraftClient client) {
        Scoreboard scoreboard = client.world.getScoreboard();
        ScoreboardObjective objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.LIST);
        if (objective != null) {
            return scoreboard.getOrCreateScore(player, objective).getScore();
        }
        return 0;
    }

    /**
     * Determines the color code based on the player's health.
     *
     * @param health The player's health value.
     * @return A color code string.
     */
    private static String getHealthColor(int health) {
        if (health <= 10) {
            return "§c"; // Red: Critical health
        } else if (health <= 20) {
            return "§6"; // Gold: Low health
        } else if (health <= 30) {
            return "§e"; // Yellow: Moderate health
        } else {
            return "§a"; // Green: High health
        }
    }
}
