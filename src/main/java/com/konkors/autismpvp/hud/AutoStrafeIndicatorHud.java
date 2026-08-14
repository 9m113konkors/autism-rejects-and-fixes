package com.konkors.autismpvp.hud;

import com.konkors.autismpvp.AutismPVP;
import com.konkors.autismpvp.modules.AutoStrafeModule;

public final class AutoStrafeIndicatorHud extends FlashIndicatorHud {
    public static final String ID = AutismPVP.ID + ":auto-strafe-indicator";

    @Override public String id() { return ID; }
    @Override public String label() { return "Auto Strafe Indicator"; }
    @Override public String description() { return "Shows while auto-strafing is on."; }
    @Override protected String indicatorText() { return "STRAF"; }
    @Override protected boolean indicatorActive() { return AutoStrafeModule.active(); }
    @Override protected long lastFlashMs() { return 0; }
    @Override protected int tierColor() { return AutoStrafeModule.tier().color(); }
    @Override public int defaultY() { return 210; }
}
