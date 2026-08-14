package com.konkors.autismpvp;

import autismclient.api.ApiVersion;
import autismclient.api.AutismAddons;
import autismclient.api.SimpleAddon;
import autismclient.util.AutismConfig;
import autismclient.util.AutismHudManager;
import com.konkors.autismpvp.hud.AnchorMacroIndicatorHud;
import com.konkors.autismpvp.hud.AutoStrafeIndicatorHud;
import com.konkors.autismpvp.hud.BacktrackIndicatorHud;
import com.konkors.autismpvp.hud.CrystalAuraIndicatorHud;
import com.konkors.autismpvp.hud.CrystalMacroIndicatorHud;
import com.konkors.autismpvp.hud.CrystalWarpIndicatorHud;
import com.konkors.autismpvp.hud.JumpResetIndicatorHud;
import com.konkors.autismpvp.hud.AutoCritoutIndicatorHud;
import com.konkors.autismpvp.hud.KnockbackDelayIndicatorHud;
import com.konkors.autismpvp.hud.LitematicaPrinterIndicatorHud;
import com.konkors.autismpvp.hud.ReachIndicatorHud;
import com.konkors.autismpvp.hud.SpearKillIndicatorHud;
import com.konkors.autismpvp.hud.TotemIndicatorHud;
import com.konkors.autismpvp.hud.VelocityIndicatorHud;
import com.konkors.autismpvp.hud.WtapIndicatorHud;
import com.konkors.autismpvp.modules.AutoCritoutModule;
import com.konkors.autismpvp.modules.AutoJumpResetModule;
import com.konkors.autismpvp.modules.AutoShieldModule;
import com.konkors.autismpvp.modules.AutoStrafeModule;
import com.konkors.autismpvp.modules.AutoWTapModule;
import com.konkors.autismpvp.modules.AnchorMacroModule;
import com.konkors.autismpvp.modules.BacktrackModule;
import com.konkors.autismpvp.modules.BetterAutoClickerModule;
import com.konkors.autismpvp.modules.BetterClickGuiModule;
import com.konkors.autismpvp.modules.BetterNameTagsModule;
import com.konkors.autismpvp.modules.BowAimbotModule;
import com.konkors.autismpvp.modules.CrystalMacroModule;
import com.konkors.autismpvp.modules.CrystalAuraModule;
import com.konkors.autismpvp.modules.CrystalWarpModule;
import com.konkors.autismpvp.modules.GapMacroModule;
import com.konkors.autismpvp.modules.KnockbackDelayModule;
import com.konkors.autismpvp.modules.LegitAutoTotemModule;
import com.konkors.autismpvp.modules.LitematicaPrinterModule;
import com.konkors.autismpvp.modules.ReachModule;
import com.konkors.autismpvp.modules.SpearKillModule;
import com.konkors.autismpvp.modules.VelocityModule;
import com.konkors.autismpvp.modules.WorldChamsModule;
import com.konkors.autismpvp.modules.presets.Axe_PresetModule;
import com.konkors.autismpvp.modules.presets.BedwarsPresetModule;
import com.konkors.autismpvp.modules.presets.Mace_PresetModule;
import com.konkors.autismpvp.modules.presets.NethOP_PresetModule;
import com.konkors.autismpvp.modules.presets.Pot_PresetModule;
import com.konkors.autismpvp.modules.presets.SMP_PresetModule;
import com.konkors.autismpvp.modules.presets.Sword_PresetModule;
import com.konkors.autismpvp.modules.presets.UHC_PresetModule;
import com.konkors.autismpvp.modules.presets.Vanilla_PresetModule;

import java.util.List;

public final class AutismPVP extends SimpleAddon {
    public static final String ID = "autismpvp";

    // Matches ModuleCategory.toKey("ADDON " + addonId) in the host client.
    private static final String ADDON_CATEGORY = "ADDON_AUTISMPVP";

    public AutismPVP() {
        super(ApiVersion.CURRENT, "com.konkors.autismpvp");
    }

    @Override
    protected void initialize() {
        registerModule(new AutoWTapModule());
        registerModule(new AutoJumpResetModule());
        registerModule(new AutoStrafeModule());
        registerModule(new BacktrackModule());
        registerModule(new CrystalMacroModule());
        registerModule(new AnchorMacroModule());
        registerModule(new CrystalAuraModule());
        registerModule(new CrystalWarpModule());
        registerModule(new LitematicaPrinterModule());
        registerModule(new KnockbackDelayModule());
        registerModule(new ReachModule());
        registerModule(new SpearKillModule());
        registerModule(new LegitAutoTotemModule());
        registerModule(new AutoCritoutModule());
        registerModule(new BetterAutoClickerModule());
        registerModule(new AutoShieldModule());
        registerModule(new VelocityModule());
        registerModule(new BowAimbotModule());
        registerModule(new WorldChamsModule());
        registerModule(new GapMacroModule());
        registerModule(new BedwarsPresetModule());
        registerModule(new Vanilla_PresetModule());
        registerModule(new UHC_PresetModule());
        registerModule(new Pot_PresetModule());
        registerModule(new NethOP_PresetModule());
        registerModule(new SMP_PresetModule());
        registerModule(new Sword_PresetModule());
        registerModule(new Axe_PresetModule());
        registerModule(new Mace_PresetModule());
        registerModule(new BetterClickGuiModule());
        registerModule(new BetterNameTagsModule());
        AutismAddons.hud().register(new WtapIndicatorHud());
        AutismAddons.hud().register(new JumpResetIndicatorHud());
        AutismAddons.hud().register(new AutoStrafeIndicatorHud());
        AutismAddons.hud().register(new BacktrackIndicatorHud());
        AutismAddons.hud().register(new CrystalMacroIndicatorHud());
        AutismAddons.hud().register(new AnchorMacroIndicatorHud());
        AutismAddons.hud().register(new CrystalAuraIndicatorHud());
        AutismAddons.hud().register(new CrystalWarpIndicatorHud());
        AutismAddons.hud().register(new LitematicaPrinterIndicatorHud());
        AutismAddons.hud().register(new KnockbackDelayIndicatorHud());
        AutismAddons.hud().register(new ReachIndicatorHud());
        AutismAddons.hud().register(new SpearKillIndicatorHud());
        AutismAddons.hud().register(new TotemIndicatorHud());
        AutismAddons.hud().register(new AutoCritoutIndicatorHud());
        AutismAddons.hud().register(new VelocityIndicatorHud());

        positionCategoryWindow();
        AutismAddons.events().onTick(mc -> syncHudVisibility());
    }

    // Indicators should only be visible while their module is enabled.
    private static void syncHudVisibility() {
        setElementVisible(WtapIndicatorHud.ID, AutoWTapModule.active());
        setElementVisible(JumpResetIndicatorHud.ID, AutoJumpResetModule.active());
        setElementVisible(AutoStrafeIndicatorHud.ID, AutoStrafeModule.active());
        setElementVisible(BacktrackIndicatorHud.ID, BacktrackModule.active());
        setElementVisible(CrystalMacroIndicatorHud.ID, CrystalMacroModule.active());
        setElementVisible(AnchorMacroIndicatorHud.ID, AnchorMacroModule.active());
        setElementVisible(CrystalAuraIndicatorHud.ID, CrystalAuraModule.active());
        setElementVisible(CrystalWarpIndicatorHud.ID, CrystalWarpModule.active());
        setElementVisible(LitematicaPrinterIndicatorHud.ID, LitematicaPrinterModule.active());
        setElementVisible(KnockbackDelayIndicatorHud.ID, KnockbackDelayModule.active());
        setElementVisible(ReachIndicatorHud.ID, ReachModule.active());
        setElementVisible(SpearKillIndicatorHud.ID, SpearKillModule.active());
        setElementVisible(TotemIndicatorHud.ID, LegitAutoTotemModule.active());
        setElementVisible(AutoCritoutIndicatorHud.ID, AutoCritoutModule.active());
        setElementVisible(VelocityIndicatorHud.ID, VelocityModule.active());
    }

    private static void setElementVisible(String id, boolean visible) {
        try {
            AutismHudManager.state(id).enabled = visible;
        } catch (Throwable ignored) {
        }
    }

    // The addon category window defaults to the top-left corner (overlapping the logo) and only a
    // few rows tall, so seed a lower, roomier position on first run and respect a saved one after.
    private static void positionCategoryWindow() {
        try {
            AutismConfig config = AutismConfig.getGlobal();
            AutismConfig.ModuleCategoryLayout layout = config.moduleCategoryLayouts
                .computeIfAbsent(ADDON_CATEGORY, key -> new AutismConfig.ModuleCategoryLayout());
            if (layout.x <= 0 && layout.y <= 0) {
                layout.x = 4;
                layout.y = 210;
                layout.visibleRows = 10;
                config.save();
            }
        } catch (Throwable ignored) {
        }
    }

    }
