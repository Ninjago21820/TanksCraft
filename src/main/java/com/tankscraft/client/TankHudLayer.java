package com.tankscraft.client;

import com.tankscraft.entity.TankEntity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * HUD affiché quand le joueur pilote un char : rechargement du canon.
 */
public final class TankHudLayer {

    private static final int RELOAD_TICKS = 60;

    public static void render(GuiGraphics gui, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        if (!(mc.player.getControlledVehicle() instanceof TankEntity tank)) {
            return;
        }
        if (mc.level == null) {
            return;
        }

        long now = mc.level.getGameTime();
        float progress;
        if (ClientEvents.lastFireGameTime == Long.MIN_VALUE) {
            progress = 1.0F;
        } else {
            progress = Mth.clamp((now - ClientEvents.lastFireGameTime) / (float) RELOAD_TICKS, 0.0F, 1.0F);
        }

        int cx = gui.guiWidth() / 2;
        int y = gui.guiHeight() - 40;
        int barWidth = 120;

        // fond
        gui.fill(cx - barWidth / 2 - 2, y - 2, cx + barWidth / 2 + 2, y + 10, 0x90000000);
        // progression
        int filled = (int) ((barWidth - 4) * progress);
        int color = progress >= 1.0F ? 0xFF7DC95E : 0xFFD9A441;
        gui.fill(cx - barWidth / 2, y, cx - barWidth / 2 + Math.max(1, filled), y + 8, color);

        gui.drawString(mc.font, Component.translatable(progress >= 1.0F
                        ? "hud.tankscraft.ready" : "hud.tankscraft.reloading"),
                cx, y + 12, 0xFFE8E4D8, true);
    }

    private TankHudLayer() {
    }
}
