package com.konkors.autismpvp.hud;

import com.konkors.autismpvp.AutismPVP;
import com.konkors.autismpvp.modules.AutoJumpResetModule;

public final class JumpResetIndicatorHud extends FlashIndicatorHud {
    public static final String ID = AutismPVP.ID + ":jump-reset-indicator";

    @Override public String id() { return ID; }
    @Override public String label() { return "JumpReset Indicator"; }
    @Override public String description() { return "Briefly lights up when Auto JumpReset fires."; }
    @Override protected String indicatorText() { return "JUMP"; }
    @Override protected boolean indicatorActive() { return AutoJumpResetModule.active(); }
    @Override protected long lastFlashMs() { return AutoJumpResetModule.lastJumpMs; }
    @Override protected int tierColor() { return AutoJumpResetModule.tier().color(); }
    @Override public int defaultY() { return 144; }
}
