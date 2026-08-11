package com.konkors.autismpvp.hud;

import com.konkors.autismpvp.AutismPVP;
import com.konkors.autismpvp.modules.VelocityModule;

public final class VelocityIndicatorHud extends FlashIndicatorHud {
    public static final String ID = AutismPVP.ID + ":velocity-indicator";

    @Override public String id() { return ID; }
    @Override public String label() { return "Velocity Indicator"; }
    @Override public String description() { return "Briefly lights up when knockback is scaled."; }
    @Override protected String indicatorText() { return "KB"; }
    @Override protected boolean indicatorActive() { return VelocityModule.active(); }
    @Override protected long lastFlashMs() { return VelocityModule.lastKbMs; }
    @Override protected int tierColor() { return VelocityModule.tier().color(); }
    @Override public int defaultY() { return 186; }
}
