package com.konkors.autismpvp.hud;

import com.konkors.autismpvp.AutismPVP;
import com.konkors.autismpvp.modules.KillAuraButBetterModule;

public final class KillAuraButBetterIndicatorHud extends FlashIndicatorHud {
    public static final String ID = AutismPVP.ID + ":killaura-better-indicator";

    @Override public String id() { return ID; }
    @Override public String label() { return "KillAura But Better Indicator"; }
    @Override public String description() { return "Briefly lights up when KillAura But Better lands a hop-crit."; }
    @Override protected String indicatorText() { return "CRIT"; }
    @Override protected boolean indicatorActive() { return KillAuraButBetterModule.active(); }
    @Override protected long lastFlashMs() { return KillAuraButBetterModule.lastCritMs; }
    @Override protected int tierColor() { return KillAuraButBetterModule.tier().color(); }
    @Override public int defaultY() { return 158; }
}
