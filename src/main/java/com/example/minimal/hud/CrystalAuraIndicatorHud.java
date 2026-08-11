package com.example.minimal.hud;

import com.example.minimal.MinimalAddon;
import com.example.minimal.modules.CrystalAuraModule;

public final class CrystalAuraIndicatorHud extends FlashIndicatorHud {
    public static final String ID = MinimalAddon.ID + ":crystal-aura-indicator";

    @Override public String id() { return ID; }
    @Override public String label() { return "Crystal Aura Indicator"; }
    @Override public String description() { return "Shows while auto crystal aura is on."; }
    @Override protected String indicatorText() { return "CAURA"; }
    @Override protected boolean indicatorActive() { return CrystalAuraModule.active(); }
    @Override protected long lastFlashMs() { return 0; }
    @Override protected int tierColor() { return CrystalAuraModule.tier().color(); }
    @Override public int defaultY() { return 258; }
}
