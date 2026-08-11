package com.example.minimal.hud;

import com.example.minimal.MinimalAddon;
import com.example.minimal.modules.BacktrackModule;

public final class BacktrackIndicatorHud extends FlashIndicatorHud {
    public static final String ID = MinimalAddon.ID + ":backtrack-indicator";

    @Override public String id() { return ID; }
    @Override public String label() { return "Backtrack Indicator"; }
    @Override public String description() { return "Shows while the position delay adder is on."; }
    @Override protected String indicatorText() { return "BKTRK"; }
    @Override protected boolean indicatorActive() { return BacktrackModule.active(); }
    @Override protected long lastFlashMs() { return 0; }
    @Override protected int tierColor() { return BacktrackModule.tier().color(); }
    @Override public int defaultY() { return 222; }
}
