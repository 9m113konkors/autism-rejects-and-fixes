package com.konkors.autismpvp.modules;

import autismclient.api.module.ActionSetting;
import autismclient.api.module.ChoiceSetting;
import autismclient.api.module.IntSetting;
import autismclient.modules.Module;
import autismclient.modules.ModuleRegistry;
import com.konkors.autismpvp.Tier;

import java.util.Locale;

// One-click config presets per PvP gamemode (MCTIERS-style), scaled by a blatantness slider.
// "Apply" (and any change to the gamemode/blatant sliders) writes tuned values and enable states
// across the addon's combat modules through the public Module API, then re-pins this module to the
// top of the addon's category column so it is always the first thing you see.
public final class QuickConfigModule extends Module {

    public static final String ID = "autismpvp:quick-config";

    // Values indexed by tier bucket: 0 = Closet, 1 = Legit, 2 = Risky, 3 = Blatant, 4 = Impossible.
    private static final double[] REACH = {3.6, 4.0, 5.0, 7.0, 10.0};
    private static final int[] HORIZONTAL = {90, 70, 45, 20, 0};
    private static final int[] VERTICAL = {90, 70, 45, 20, 0};
    private static final int[] CHANCE = {60, 80, 90, 100, 100};
    private static final int[] CPS_MIN = {10, 12, 15, 17, 18};
    private static final int[] CPS_MAX = {12, 15, 18, 20, 20};

    private final ChoiceSetting gameMode = add(new ChoiceSetting("game-mode", "Gamemode", "Sword",
        "Sword", "Crystal", "Rod", "Bow").group("Preset"));
    private final IntSetting blatant = add(new IntSetting("blatant", "Blatantness", 40, 0, 100, 5)
        .group("Preset")
        .description("0 = closet, 100 = impossible. Tunes reach, velocity, CPS and chances."));
    private final ActionSetting apply = add(new ActionSetting("apply", "Apply config", this::applyPreset)
        .group("Preset").buttonLabel("Apply"));

    public QuickConfigModule() {
        super(ID, "Quick Config", "Applies a MCTIERS gamemode preset (sword, crystal, rod, bow) to the addon's combat modules, scaled by a blatantness slider. Stays pinned to the top of its category column.");
    }

    @Override
    public String info() {
        return gameMode.get() + " " + tier().label();
    }

    public static Tier tier() {
        Module module = ModuleRegistry.get(ID);
        return module instanceof QuickConfigModule m ? m.tierFor(m.blatant.get()) : Tier.CLOSET;
    }

    @Override
    protected void onOptionValueChanged(String settingId) {
        if ("game-mode".equals(settingId) || "blatant".equals(settingId)) {
            applyPreset();
        }
    }

    private void applyPreset() {
        String mode = gameMode.get();
        int tierIndex = tierIndex(blatant.get());

        double reach = REACH[tierIndex]
            + (isMode(mode, "Crystal") ? 0.5 : isMode(mode, "Rod") ? -0.3 : isMode(mode, "Bow") ? -0.2 : 0.0);
        int horizontal = clampInt(HORIZONTAL[tierIndex] - (isMode(mode, "Crystal") ? 10 : 0), 0, 100);
        int vertical = clampInt(VERTICAL[tierIndex] - (isMode(mode, "Crystal") ? 10 : 0), 0, 100);
        int chance = CHANCE[tierIndex];
        int cpsMin = clampInt(CPS_MIN[tierIndex] + (isMode(mode, "Crystal") ? 1 : isMode(mode, "Rod") ? -2 : 0), 4, 20);
        int cpsMax = clampInt(CPS_MAX[tierIndex] + (isMode(mode, "Crystal") ? 1 : isMode(mode, "Rod") ? -2 : 0), 4, 20);
        boolean wtap = !isMode(mode, "Crystal");

        setEnabled("kill-aura", true);
        setEnabled(ReachModule.ID, true);
        setValue(ReachModule.ID, "reach", String.format(Locale.ROOT, "%.1f", reach));

        setEnabled(VelocityModule.ID, true);
        setValue(VelocityModule.ID, "horizontal", Integer.toString(horizontal));
        setValue(VelocityModule.ID, "vertical", Integer.toString(vertical));
        setValue(VelocityModule.ID, "chance", Integer.toString(chance));

        setEnabled(KillAuraButBetterModule.ID, true);
        setValue(KillAuraButBetterModule.ID, "chance", Integer.toString(chance));
        setValue(KillAuraButBetterModule.ID, "ready-scale", "80");
        setValue(KillAuraButBetterModule.ID, "cps-min", Integer.toString(cpsMin));
        setValue(KillAuraButBetterModule.ID, "cps-max", Integer.toString(cpsMax));
        setValue(KillAuraButBetterModule.ID, "attack-cooldown", "true");

        setEnabled(AutoWTapModule.ID, wtap);
        if (wtap) {
            setValue(AutoWTapModule.ID, "mode", "Sprint-Tap");
            setValue(AutoWTapModule.ID, "chance", Integer.toString(chance));
        }

        setEnabled(AutoJumpResetModule.ID, true);
        setValue(AutoJumpResetModule.ID, "chance", Integer.toString(chance));

        setEnabled(LegitAutoTotemModule.ID, true);
        setValue(LegitAutoTotemModule.ID, "trigger", "On totem pop");
        setValue(LegitAutoTotemModule.ID, "chance", Integer.toString(chance));
        setValue(LegitAutoTotemModule.ID, "double-hand", "true");
    }

    private static boolean isMode(String mode, String expected) {
        return expected.equalsIgnoreCase(mode);
    }

    private static int tierIndex(int blatant) {
        if (blatant < 20) return 0;
        if (blatant < 45) return 1;
        if (blatant < 70) return 2;
        if (blatant < 90) return 3;
        return 4;
    }

    private Tier tierFor(int blatant) {
        return switch (tierIndex(blatant)) {
            case 1 -> Tier.LEGIT;
            case 2 -> Tier.RISKY;
            case 3 -> Tier.BLATANT;
            case 4 -> Tier.IMPOSSIBLE;
            default -> Tier.CLOSET;
        };
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void setValue(String moduleId, String settingId, String value) {
        Module module = ModuleRegistry.get(moduleId);
        if (module != null) module.setValue(settingId, value);
    }

    private static void setEnabled(String moduleId, boolean enabled) {
        Module module = ModuleRegistry.get(moduleId);
        if (module != null) module.setEnabled(enabled);
    }
}
