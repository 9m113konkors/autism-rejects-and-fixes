package com.example.minimal.hud;

import com.example.minimal.MinimalAddon;
import com.example.minimal.modules.VelocityModule;

public final class VelocityIndicatorHud extends FlashIndicatorHud {
    public static final String ID = MinimalAddon.ID + ":velocity-indicator";

    @Override public String id() { return ID; }
    @Override public String label() { return "Velocity Indicator"; }
    @Override public String description() { return "Briefly lights up when knockback is scaled."; }
    @Override protected String indicatorText() { return "KB"; }
    @Override protected boolean indicatorActive() { return VelocityModule.active(); }
    @Override protected long lastFlashMs() { return VelocityModule.lastKbMs; }
    @Override protected int tierColor() { return VelocityModule.tier().color(); }
    @Override public int defaultY() { return 186; }
}
