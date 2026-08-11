package com.example.minimal.hud;

import com.example.minimal.MinimalAddon;
import com.example.minimal.modules.AnchorMacroModule;

public final class AnchorMacroIndicatorHud extends FlashIndicatorHud {
    public static final String ID = MinimalAddon.ID + ":anchor-macro-indicator";

    @Override public String id() { return ID; }
    @Override public String label() { return "Anchor Macro Indicator"; }
    @Override public String description() { return "Shows while the anchor macro bind is held."; }
    @Override protected String indicatorText() { return "ANCHR"; }
    @Override protected boolean indicatorActive() { return AnchorMacroModule.active(); }
    @Override protected long lastFlashMs() { return 0; }
    @Override protected int tierColor() { return AnchorMacroModule.tier().color(); }
    @Override public int defaultY() { return 246; }
}
