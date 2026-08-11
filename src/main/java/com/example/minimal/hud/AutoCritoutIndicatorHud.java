package com.example.minimal.hud;

import com.example.minimal.MinimalAddon;
import com.example.minimal.modules.AutoCritoutModule;

public final class AutoCritoutIndicatorHud extends FlashIndicatorHud {
    public static final String ID = MinimalAddon.ID + ":auto-critout-indicator";

    @Override public String id() { return ID; }
    @Override public String label() { return "Auto Critout Indicator"; }
    @Override public String description() { return "Briefly lights up when Auto Critout lands a hop-crit."; }
    @Override protected String indicatorText() { return "CRIT"; }
    @Override protected boolean indicatorActive() { return AutoCritoutModule.active(); }
    @Override protected long lastFlashMs() { return AutoCritoutModule.lastCritMs; }
    @Override protected int tierColor() { return AutoCritoutModule.tier().color(); }
    @Override public int defaultY() { return 158; }
}
