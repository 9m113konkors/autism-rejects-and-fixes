package com.konkors.autismpvp.hud;

import com.konkors.autismpvp.AutismPVP;
import com.konkors.autismpvp.modules.CrystalMacroModule;

public final class CrystalMacroIndicatorHud extends FlashIndicatorHud {
    public static final String ID = AutismPVP.ID + ":crystal-macro-indicator";

    @Override public String id() { return ID; }
    @Override public String label() { return "Crystal Macro Indicator"; }
    @Override public String description() { return "Shows while the crystal macro bind is held."; }
    @Override protected String indicatorText() { return "CRYST"; }
    @Override protected boolean indicatorActive() { return CrystalMacroModule.active(); }
    @Override protected long lastFlashMs() { return 0; }
    @Override protected int tierColor() { return CrystalMacroModule.tier().color(); }
    @Override public int defaultY() { return 234; }
}
