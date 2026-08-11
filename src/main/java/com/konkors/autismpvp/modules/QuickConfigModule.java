package com.konkors.autismpvp.modules;

import autismclient.api.module.ActionSetting;
import autismclient.api.module.ChoiceSetting;
import autismclient.api.module.IntSetting;
import autismclient.modules.Module;
import autismclient.modules.ModuleRegistry;
<<<<<<< HEAD:src/main/java/com/example/minimal/modules/QuickConfigModule.java
import com.example.minimal.Tier;
import com.example.minimal.api.RangeSetting;
=======
import com.konkors.autismpvp.Tier;
>>>>>>> b46b0d5ba813af2c2b0d4860bde92eae69a4568e:src/main/java/com/konkors/autismpvp/modules/QuickConfigModule.java

import java.util.Locale;

// One-click config presets per real MCTIERS gamemode (mctiers.com: Vanilla, UHC, Pot, NethOP,
// SMP, Sword, Axe, Mace), scaled by a blatantness slider. Presets are deliberately realistic:
// even at max blatantness they stay within what strong players actually run, so the config reads
// as skill rather than obvious cheats. "Apply" (and any change to the gamemode/blatant sliders)
// writes tuned values and enable states across the addon's combat modules through the public
// Module API, then re-pins this module to the top of the addon's category column.
public final class QuickConfigModule extends Module {

    public static final String ID = "autismpvp:quick-config";

    // Values indexed by tier bucket: 0 = Closet, 1 = Legit, 2 = Risky, 3 = Blatant, 4 = Impossible.
    // Velocity values are the % of knockback you KEEP (higher = more realistic). These mirror the
    // real Vape v4 Hypixel Bedwars config: reach 3.1-3.4 (Grim flags ~3.06+), velocity 85-95%
    // kept, autoclicker 8-14 CPS.
    private static final double[] REACH = {3.0, 3.1, 3.2, 3.3, 3.4};
    private static final int[] HORIZONTAL = {95, 92, 88, 85, 80};
    private static final int[] VERTICAL = {95, 93, 90, 87, 85};
    private static final int[] CHANCE = {60, 70, 80, 90, 100};
    private static final int[] CPS_MIN = {8, 9, 9, 10, 11};
    private static final int[] CPS_MAX = {10, 11, 12, 13, 14};

    private final ChoiceSetting gameMode = add(new ChoiceSetting("game-mode", "Gamemode", "Sword",
        "Vanilla", "UHC", "Pot", "NethOP", "SMP", "Sword", "Axe", "Mace").group("Preset"));
    private final IntSetting blatant = add(new IntSetting("blatant", "Blatantness", 40, 0, 100, 5)
        .group("Preset")
        .description("0 = closet, 100 = max. Even the max stays playable and human-looking."));
    private final ActionSetting apply = add(new ActionSetting("apply", "Apply config", this::applyPreset)
        .group("Preset").buttonLabel("Apply"));

    public QuickConfigModule() {
        super(ID, "Quick Config", "Applies a preset for the real MCTIERS gamemodes (vanilla, UHC, pot, nethOP, SMP, sword, axe, mace) to the addon's combat modules, scaled by a blatantness slider that stays realistic even at the top. Stays pinned to the top of its category column.");
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

        double reach = clampDouble(REACH[tierIndex] + reachOffset(mode), 3.0, 3.5);
        int horizontal = clampInt(HORIZONTAL[tierIndex] + kbOffset(mode), 40, 100);
        int vertical = clampInt(VERTICAL[tierIndex] + kbOffset(mode), 50, 100);
        int chance = CHANCE[tierIndex];
        int cpsMin = clampInt(CPS_MIN[tierIndex] + cpsOffset(mode), 4, 20);
        int cpsMax = clampInt(CPS_MAX[tierIndex] + cpsOffset(mode), 4, 20);
        boolean wtap = wtapFor(mode);

        setEnabled("kill-aura", true);
        setEnabled(ReachModule.ID, true);
        setValue(ReachModule.ID, "reach", String.format(Locale.ROOT, "%.1f", reach));

        setEnabled(VelocityModule.ID, true);
        setValue(VelocityModule.ID, "horizontal", Integer.toString(horizontal));
        setValue(VelocityModule.ID, "vertical", Integer.toString(vertical));
        setValue(VelocityModule.ID, "chance", Integer.toString(chance));
        setValue(VelocityModule.ID, "mode", "Reduce");
        setValue(VelocityModule.ID, "delay", "0");
        setValue(VelocityModule.ID, "jitter", "10");

        setEnabled(AutoCritoutModule.ID, true);
        setValue(AutoCritoutModule.ID, "chance", Integer.toString(chance));
        setValue(AutoCritoutModule.ID, "min-interval", "6");
        setValue(AutoCritoutModule.ID, "min-still", "6");
        setValue(AutoCritoutModule.ID, "require-fall", "true");
        setValue(AutoCritoutModule.ID, "pre-delay", "true");

        setEnabled(BetterAutoClickerModule.ID, true);
        setValue(BetterAutoClickerModule.ID, "with-killaura", "true");
        setValue(BetterAutoClickerModule.ID, "cps", RangeSetting.encode(cpsMin, cpsMax));
        setValue(BetterAutoClickerModule.ID, "ready-scale", "100");
        setValue(BetterAutoClickerModule.ID, "attack-cooldown", "true");
        setValue(BetterAutoClickerModule.ID, "require-facing", "true");
        setValue(BetterAutoClickerModule.ID, "post-face-delay", "1");

        setEnabled(BowAimbotModule.ID, true);

        setEnabled(AutoWTapModule.ID, wtap);
        if (wtap) {
            setValue(AutoWTapModule.ID, "mode", "Sprint-Tap");
            setValue(AutoWTapModule.ID, "chance", Integer.toString(chance));
            setValue(AutoWTapModule.ID, "accuracy", "85");
        }

        setEnabled(AutoJumpResetModule.ID, true);
        setValue(AutoJumpResetModule.ID, "chance", Integer.toString(chance));
        setValue(AutoJumpResetModule.ID, "accuracy", "85");

        setEnabled(LegitAutoTotemModule.ID, true);
        setValue(LegitAutoTotemModule.ID, "trigger", "On totem pop");
        setValue(LegitAutoTotemModule.ID, "chance", Integer.toString(chance));
        setValue(LegitAutoTotemModule.ID, "double-hand", "true");
    }

    private static double reachOffset(String mode) {
        return switch (mode) {
            case "UHC" -> 0.2;
            case "NethOP" -> 0.3;
            case "SMP" -> -0.4;
            case "Sword", "Axe" -> 0.1;
            default -> 0.0;
        };
    }

    private static int kbOffset(String mode) {
        return switch (mode) {
            case "NethOP" -> -10;
            case "UHC" -> -5;
            default -> 0;
        };
    }

    private static int cpsOffset(String mode) {
        return switch (mode) {
            case "Axe" -> -2;
            case "Mace" -> -1;
            default -> 0;
        };
    }

    private static boolean wtapFor(String mode) {
        return switch (mode) {
            case "NethOP", "SMP", "Mace" -> false;
            default -> true;
        };
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

    private static double clampDouble(double value, double min, double max) {
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
