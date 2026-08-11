package com.example.minimal.hud;

import autismclient.api.hud.HudElementProvider;
import com.example.minimal.MinimalAddon;
import com.example.minimal.modules.SpearKillModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

// Static value indicator: shows the spear range and lights up while the module is on.
public final class SpearKillIndicatorHud implements HudElementProvider {
    public static final String ID = MinimalAddon.ID + ":spear-kill-indicator";

    private String text() {
        return "SPEAR " + SpearKillModule.modeLabel() + " " + SpearKillModule.range() + "b";
    }

    @Override public String id() { return ID; }
    @Override public String label() { return "SpearKill Indicator"; }
    @Override public String description() { return "Shows the current spear mode and range."; }

    @Override
    public int width() {
        Font font = Minecraft.getInstance().font;
        String text = text();
        int textWidth = font != null ? font.width(text) : text.length() * 6;
        return textWidth + 6;
    }

    @Override public int height() { return 12; }

    @Override
    public void render(GuiGraphicsExtractor ctx, Font font, int x, int y, float alpha) {
        if (!SpearKillModule.active()) {
            return;
        }
        int color = SpearKillModule.tier().color();
        int lineHeight = font != null ? font.lineHeight : 9;
        ctx.text(font, text(), x + 2, y + (height() - lineHeight) / 2, color);
    }

    @Override public boolean defaultEnabled() { return true; }
    @Override public String defaultAnchor() { return "TOP_LEFT"; }
    @Override public int defaultX() { return 4; }
    @Override public int defaultY() { return 164; }
}
