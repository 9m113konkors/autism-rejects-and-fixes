package com.konkors.autismpvp;

import autismclient.api.ApiVersion;
import autismclient.api.AutismAddons;
import autismclient.api.SimpleAddon;
import autismclient.util.AutismConfig;
import autismclient.util.AutismHudManager;
<<<<<<< HEAD:src/main/java/com/example/minimal/MinimalAddon.java
import com.example.minimal.hud.AutoCritoutIndicatorHud;
import com.example.minimal.hud.AutoStrafeIndicatorHud;
import com.example.minimal.hud.AnchorMacroIndicatorHud;
import com.example.minimal.hud.BacktrackIndicatorHud;
import com.example.minimal.hud.CrystalAuraIndicatorHud;
import com.example.minimal.hud.CrystalMacroIndicatorHud;
import com.example.minimal.hud.CrystalWarpIndicatorHud;
import com.example.minimal.hud.JumpResetIndicatorHud;
import com.example.minimal.hud.KnockbackDelayIndicatorHud;
import com.example.minimal.hud.LitematicaPrinterIndicatorHud;
import com.example.minimal.hud.ReachIndicatorHud;
import com.example.minimal.hud.SpearKillIndicatorHud;
import com.example.minimal.hud.TotemIndicatorHud;
import com.example.minimal.hud.VelocityIndicatorHud;
import com.example.minimal.hud.WtapIndicatorHud;
import com.example.minimal.modules.AutoCritoutModule;
import com.example.minimal.modules.AutoJumpResetModule;
import com.example.minimal.modules.AutoStrafeModule;
import com.example.minimal.modules.AutoWTapModule;
import com.example.minimal.modules.AnchorMacroModule;
import com.example.minimal.modules.BacktrackModule;
import com.example.minimal.modules.BetterAutoClickerModule;
import com.example.minimal.modules.BetterNameTagsModule;
import com.example.minimal.modules.BowAimbotModule;
import com.example.minimal.modules.CrystalMacroModule;
import com.example.minimal.modules.CrystalAuraModule;
import com.example.minimal.modules.CrystalWarpModule;
import com.example.minimal.modules.GapMacroModule;
import com.example.minimal.modules.KnockbackDelayModule;
import com.example.minimal.modules.LegitAutoTotemModule;
import com.example.minimal.modules.LitematicaPrinterModule;
import com.example.minimal.modules.QuickConfigModule;
import com.example.minimal.modules.ReachModule;
import com.example.minimal.modules.SpearKillModule;
import com.example.minimal.modules.VelocityModule;
import com.example.minimal.modules.WorldChamsModule;
=======
import com.konkors.autismpvp.hud.JumpResetIndicatorHud;
import com.konkors.autismpvp.hud.KillAuraButBetterIndicatorHud;
import com.konkors.autismpvp.hud.ReachIndicatorHud;
import com.konkors.autismpvp.hud.TotemIndicatorHud;
import com.konkors.autismpvp.hud.VelocityIndicatorHud;
import com.konkors.autismpvp.hud.WtapIndicatorHud;
import com.konkors.autismpvp.modules.AutoJumpResetModule;
import com.konkors.autismpvp.modules.AutoWTapModule;
import com.konkors.autismpvp.modules.KillAuraButBetterModule;
import com.konkors.autismpvp.modules.LegitAutoTotemModule;
import com.konkors.autismpvp.modules.QuickConfigModule;
import com.konkors.autismpvp.modules.ReachModule;
import com.konkors.autismpvp.modules.VelocityModule;
>>>>>>> b46b0d5ba813af2c2b0d4860bde92eae69a4568e:src/main/java/com/konkors/autismpvp/AutismPVP.java

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
        registerModule(new VelocityModule());
        registerModule(new BowAimbotModule());
        registerModule(new WorldChamsModule());
        registerModule(new GapMacroModule());
        registerModule(new QuickConfigModule());
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
        pinQuickConfigToTop();
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

    // The module menu sorts each category column by the persisted per-category id list
    // (moduleCategoryOrder), appending unknown modules at the end. Force QuickConfig to index 0 so
    // it always renders at the top of the addon's column, every launch, without touching the client.
    private static void pinQuickConfigToTop() {
        try {
            AutismConfig config = AutismConfig.getGlobal();
            List<String> order = config.moduleCategoryOrder
                .computeIfAbsent(ADDON_CATEGORY, key -> new java.util.ArrayList<>());
            order.remove(QuickConfigModule.ID);
            order.add(0, QuickConfigModule.ID);
            config.save();
        } catch (Throwable ignored) {
        }
    }
}
