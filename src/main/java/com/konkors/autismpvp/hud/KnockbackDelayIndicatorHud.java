package com.konkors.autismpvp.hud;

import com.konkors.autismpvp.AutismPVP;
import com.konkors.autismpvp.modules.KnockbackDelayModule;

public final class KnockbackDelayIndicatorHud extends FlashIndicatorHud {
    public static final String ID = AutismPVP.ID + ":knockback-delay-indicator";

    @Override public String id() { return ID; }
    @Override public String label() { return "KnockbackDelay Indicator"; }
    @Override public String description() { return "Briefly lights up when knockback is delayed."; }
    @Override protected String indicatorText() { return "KBDLY"; }
    @Override protected boolean indicatorActive() { return KnockbackDelayModule.active(); }
    @Override protected long lastFlashMs() { return KnockbackDelayModule.lastDelayMs; }
    @Override protected int tierColor() { return KnockbackDelayModule.tier().color(); }
    @Override public int defaultY() { return 200; }
}
