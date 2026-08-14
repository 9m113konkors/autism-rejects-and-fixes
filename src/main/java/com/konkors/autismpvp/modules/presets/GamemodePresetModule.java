package com.konkors.autismpvp.modules.presets;

import autismclient.api.module.ActionSetting;
import autismclient.api.module.BoolSetting;
import autismclient.api.module.ChoiceSetting;
import autismclient.api.module.IntSetting;
import autismclient.modules.Module;
import autismclient.modules.ModuleRegistry;
import autismclient.modules.ModuleCategory;
import com.konkors.autismpvp.Tier;
import com.konkors.autismpvp.api.RangeSetting;

import java.util.Locale;

// Shared preset engine behind the per-gamemode preset modules. A gamemode is a real MCTIERS
// mode (mctiers.com: Vanilla, UHC, Pot, NethOP, SMP, Sword, Axe, Mace) and a blatantness. Each
// preset module lives in its own addon category of the same name, so every gamemode gets a
// dedicated screen in the module menu. Applying a preset writes tuned values and enable states
// across the addon's combat modules through the public Module API.
public abstract class GamemodePresetModule extends Module {

    protected final String gamemode;

    protected GamemodePresetModule(String id, String name, String categoryName, String gamemode, String description) {
        super(id, name, ModuleCategory.registerAddon(categoryName, categoryName), description);
        this.gamemode = gamemode;
    }

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

    private final IntSetting blatant = add(new IntSetting("blatant", "Blatantness", 40, 0, 100, 5)
        .group("Preset")
        .description("0 = closet, 100 = max. Even the max stays playable and human-looking."));
    private final ActionSetting apply = add(new ActionSetting("apply", "Apply config", this::applyPreset)
        .group("Preset").buttonLabel("Apply"));

    @Override
    public String info() {
        return tierFor(blatant.get()).label();
    }

    @Override
    protected void onOptionValueChanged(String settingId) {
        if ("blatant".equals(settingId)) {
            applyPreset();
        }
    }

    private void applyPreset() {
        int tierIndex = tierIndex(blatant.get());

        double reach = clampDouble(REACH[tierIndex] + reachOffset(), 3.0, 3.5);
        int horizontal = clampInt(HORIZONTAL[tierIndex] + kbOffset(), 40, 100);
        int vertical = clampInt(VERTICAL[tierIndex] + kbOffset(), 50, 100);
        int chance = CHANCE[tierIndex];
        int cpsMin = clampInt(CPS_MIN[tierIndex] + cpsOffset(), 4, 20);
        int cpsMax = clampInt(CPS_MAX[tierIndex] + cpsOffset(), 4, 20);
        boolean wtap = wtapFor();

        setEnabled("kill-aura", true);
        setEnabled(ReachModuleId(), true);
        setValue(ReachModuleId(), "reach", String.format(Locale.ROOT, "%.1f", reach));

        setEnabled(VelocityModuleId(), true);
        setValue(VelocityModuleId(), "horizontal", Integer.toString(horizontal));
        setValue(VelocityModuleId(), "vertical", Integer.toString(vertical));
        setValue(VelocityModuleId(), "chance", Integer.toString(chance));
        setValue(VelocityModuleId(), "mode", "Reduce");
        setValue(VelocityModuleId(), "delay", "0");
        setValue(VelocityModuleId(), "jitter", "10");

        setEnabled(AutoCritoutModuleId(), true);
        setValue(AutoCritoutModuleId(), "chance", Integer.toString(chance));
        setValue(AutoCritoutModuleId(), "min-interval", "6");
        setValue(AutoCritoutModuleId(), "min-still", "6");
        setValue(AutoCritoutModuleId(), "require-fall", "true");
        setValue(AutoCritoutModuleId(), "pre-delay", "true");

        setEnabled(BetterAutoClickerModuleId(), true);
        setValue(BetterAutoClickerModuleId(), "with-killaura", "true");
        setValue(BetterAutoClickerModuleId(), "cps", RangeSetting.encode(cpsMin, cpsMax));
        setValue(BetterAutoClickerModuleId(), "ready-scale", "100");
        setValue(BetterAutoClickerModuleId(), "attack-cooldown", "true");
        setValue(BetterAutoClickerModuleId(), "require-facing", "true");
        setValue(BetterAutoClickerModuleId(), "post-face-delay", "1");

        setEnabled(BowAimbotModuleId(), true);

        setEnabled(AutoWTapModuleId(), wtap);
        if (wtap) {
            setValue(AutoWTapModuleId(), "mode", "Sprint-Tap");
            setValue(AutoWTapModuleId(), "chance", Integer.toString(chance));
            setValue(AutoWTapModuleId(), "accuracy", "85");
        }

        setEnabled(AutoJumpResetModuleId(), true);
        setValue(AutoJumpResetModuleId(), "chance", Integer.toString(chance));
        setValue(AutoJumpResetModuleId(), "accuracy", "85");

        setEnabled(LegitAutoTotemModuleId(), true);
        setValue(LegitAutoTotemModuleId(), "trigger", "On totem pop");
        setValue(LegitAutoTotemModuleId(), "chance", Integer.toString(chance));
        setValue(LegitAutoTotemModuleId(), "double-hand", "true");
    }

    protected String ReachModuleId() {
        return "autismpvp:reach";
    }
    protected String VelocityModuleId() {
        return "autismpvp:velocity";
    }
    protected String AutoCritoutModuleId() {
        return "autismpvp:auto-critout";
    }
    protected String BetterAutoClickerModuleId() {
        return "autismpvp:better-auto-clicker";
    }
    protected String BowAimbotModuleId() {
        return "autismpvp:bow-aimbot";
    }
    protected String AutoWTapModuleId() {
        return "autismpvp:auto-wtap";
    }
    protected String AutoJumpResetModuleId() {
        return "autismpvp:auto-jump-reset";
    }
    protected String LegitAutoTotemModuleId() {
        return "autismpvp:legit-auto-totem";
    }

    private double reachOffset() {
        return switch (gamemode) {
            case "UHC" -> 0.2;
            case "NethOP" -> 0.3;
            case "SMP" -> -0.4;
            case "Sword", "Axe" -> 0.1;
            default -> 0.0;
        };
    }

    private int kbOffset() {
        return switch (gamemode) {
            case "NethOP" -> -10;
            case "UHC" -> -5;
            default -> 0;
        };
    }

    private int cpsOffset() {
        return switch (gamemode) {
            case "Axe" -> -2;
            case "Mace" -> -1;
            default -> 0;
        };
    }

    private boolean wtapFor() {
        return switch (gamemode) {
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