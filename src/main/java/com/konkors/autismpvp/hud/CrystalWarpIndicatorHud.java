package com.example.minimal.hud;

import com.example.minimal.MinimalAddon;
import com.example.minimal.modules.CrystalWarpModule;

public final class CrystalWarpIndicatorHud extends FlashIndicatorHud {
    public static final String ID = MinimalAddon.ID + ":crystal-warp-indicator";

    @Override public String id() { return ID; }
    @Override public String label() { return "CrystalWarp Indicator"; }
    @Override public String description() { return "Shows while the CrystalWarp bind is held."; }
    @Override protected String indicatorText() { return "CWARP"; }
    @Override protected boolean indicatorActive() { return CrystalWarpModule.active(); }
    @Override protected long lastFlashMs() { return 0; }
    @Override protected int tierColor() { return CrystalWarpModule.tier().color(); }
    @Override public int defaultY() { return 270; }
}
