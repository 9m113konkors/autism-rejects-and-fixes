package com.konkors.autismpvp.hud;

import autismclient.api.hud.HudElementProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

// Base for the small, non-intrusive per-module flash indicators. A subclass supplies the text,
// whether its module is active, and a millisecond timestamp that is bumped whenever the module fires.
public abstract class FlashIndicatorHud implements HudElementProvider {
    protected static final long FLASH_MS = 350L;

    protected abstract String indicatorText();
    protected abstract boolean indicatorActive();
    protected abstract long lastFlashMs();

    // Idle text color: subclasses tint it by their module's risk tier.
    protected int tierColor() {
        return 0xFF3E4A40;
    }

    @Override
    public int width() {
        Font font = Minecraft.getInstance().font;
        String text = indicatorText();
        int textWidth = font != null ? font.width(text) : text.length() * 6;
        return textWidth + 6;
    }

    @Override public int height() { return 12; }

    @Override
    public void render(GuiGraphicsExtractor ctx, Font font, int x, int y, float alpha) {
        boolean active = indicatorActive();
        if (!active) {
            return;
        }
        long since = System.currentTimeMillis() - lastFlashMs();
        float progress = since >= FLASH_MS ? 0.0f : 1.0f - (float) since / FLASH_MS;

        int color;
        if (progress > 0.0f) {
            int r = 80 + (int) ((220 - 80) * progress);
            color = 0xFF000000 | (r << 16) | (0xDC << 8) | 0x78;
        } else {
            color = tierColor();
        }

        int lineHeight = font != null ? font.lineHeight : 9;
        ctx.text(font, indicatorText(), x + 2, y + (height() - lineHeight) / 2, color);

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
    @Override public int defaultY() { return 110; }
}
