package com.example.minimal.hud;

import com.example.minimal.MinimalAddon;
import com.example.minimal.modules.LitematicaPrinterModule;

public final class LitematicaPrinterIndicatorHud extends FlashIndicatorHud {
    public static final String ID = MinimalAddon.ID + ":litematica-printer-indicator";

    @Override public String id() { return ID; }
    @Override public String label() { return "Litematica Printer Indicator"; }
    @Override public String description() { return "Shows while the Litematica printer is enabled (dimmed when no schematic is loaded)."; }
    @Override protected String indicatorText() { return "PRINT"; }
    @Override protected boolean indicatorActive() { return LitematicaPrinterModule.active(); }
    @Override protected long lastFlashMs() { return 0; }
    @Override protected int tierColor() { return LitematicaPrinterModule.tier().color(); }
    @Override public int defaultY() { return 282; }
}
