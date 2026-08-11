package com.konkors.autismpvp.hud;

import autismclient.api.hud.HudElementProvider;
import com.konkors.autismpvp.AutismPVP;
import com.konkors.autismpvp.modules.ReachModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

// Static value indicator: shows the configured reach and lights up when the module is on.
public final class ReachIndicatorHud implements HudElementProvider {
    public static final String ID = AutismPVP.ID + ":reach-indicator";

    private String text() {
        return "REACH " + String.format("%.1f", ReachModule.reach());
    }

    @Override public String id() { return ID; }
    @Override public String label() { return "Reach Indicator"; }
    @Override public String description() { return "Shows the current attack reach."; }

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
        if (!ReachModule.active()) {
            return;
        }
        int color = ReachModule.tier().color();
        int lineHeight = font != null ? font.lineHeight : 9;
        ctx.text(font, text(), x + 2, y + (height() - lineHeight) / 2, color);
    }

    @Override public boolean defaultEnabled() { return true; }
    @Override public String defaultAnchor() { return "TOP_LEFT"; }
    @Override public int defaultX() { return 4; }
    @Override public int defaultY() { return 158; }
}
