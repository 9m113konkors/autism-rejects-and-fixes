package com.example.minimal.hud;

import com.example.minimal.MinimalAddon;
import com.example.minimal.modules.LegitAutoTotemModule;

public final class TotemIndicatorHud extends FlashIndicatorHud {
    public static final String ID = MinimalAddon.ID + ":totem-indicator";

    @Override public String id() { return ID; }
    @Override public String label() { return "Totem Indicator"; }
    @Override public String description() { return "Briefly lights up when Legit AutoTotem swaps."; }
    @Override protected String indicatorText() { return "TOTEM"; }
    @Override protected boolean indicatorActive() { return LegitAutoTotemModule.active(); }
    @Override protected long lastFlashMs() { return LegitAutoTotemModule.lastSwapMs; }
    @Override protected int tierColor() { return LegitAutoTotemModule.tier().color(); }
    @Override public int defaultY() { return 172; }
}
