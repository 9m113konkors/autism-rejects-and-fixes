package com.example.minimal.hud;

import autismclient.api.hud.HudElementProvider;
import com.example.minimal.MinimalAddon;
import com.example.minimal.modules.AutoWTapModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

// Small, non-intrusive indicator that lights up briefly whenever Auto WTap fires.
public final class WtapIndicatorHud implements HudElementProvider {
    private static final long FLASH_MS = 350L;
    private static final String TEXT = "WTAP";

    @Override public String id() { return MinimalAddon.ID + ":wtap-indicator"; }
    @Override public String label() { return "WTap Indicator"; }
    @Override public String description() { return "Briefly lights up when Auto WTap fires."; }

    @Override
    public int width() {
        Font font = Minecraft.getInstance().font;
        int textWidth = font != null ? font.width(TEXT) : TEXT.length() * 6;
        return textWidth + 6;
    }

    @Override public int height() { return 12; }

    @Override
    public void render(GuiGraphicsExtractor ctx, Font font, int x, int y, float alpha) {
        boolean active = AutoWTapModule.active();
        long since = System.currentTimeMillis() - AutoWTapModule.lastTapMs;
        float progress = since >= FLASH_MS ? 0.0f : 1.0f - (float) since / FLASH_MS;

        int color;
        if (active && progress > 0.0f) {
            int r = 80 + (int) ((220 - 80) * progress);
            color = 0xFF000000 | (r << 16) | (0xDC << 8) | 0x78;
        } else if (active) {
            color = 0xFF3E4A40;
        } else {
            color = 0xFF3A3A3A;
        }

        int lineHeight = font != null ? font.lineHeight : 9;
        ctx.text(font, TEXT, x + 2, y + (height() - lineHeight) / 2, color);

        int barY = y + height() - 2;
        int barW = width() - 4;
        int fill = (int) (barW * progress);
        if (active && fill > 0) {
            ctx.fill(x + 2, barY, x + 2 + fill, barY + 1, 0x66FFFFFF);
        }
    }

    @Override public boolean defaultEnabled() { return true; }
    @Override public String defaultAnchor() { return "TOP_LEFT"; }
    @Override public int defaultX() { return 4; }
    @Override public int defaultY() { return 130; }
}
