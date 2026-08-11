package com.konkors.autismpvp;

import autismclient.api.ApiVersion;
import autismclient.api.AutismAddons;
import autismclient.api.SimpleAddon;
import autismclient.util.AutismConfig;
import autismclient.util.AutismHudManager;
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
        registerModule(new ReachModule());
        registerModule(new LegitAutoTotemModule());
        registerModule(new KillAuraButBetterModule());
        registerModule(new VelocityModule());
        registerModule(new QuickConfigModule());
        AutismAddons.hud().register(new WtapIndicatorHud());
        AutismAddons.hud().register(new JumpResetIndicatorHud());
        AutismAddons.hud().register(new ReachIndicatorHud());
        AutismAddons.hud().register(new TotemIndicatorHud());
        AutismAddons.hud().register(new KillAuraButBetterIndicatorHud());
        AutismAddons.hud().register(new VelocityIndicatorHud());

        positionCategoryWindow();
        pinQuickConfigToTop();
        AutismAddons.events().onTick(mc -> syncHudVisibility());
    }

    // Indicators should only be visible while their module is enabled.
    private static void syncHudVisibility() {
        setElementVisible(WtapIndicatorHud.ID, AutoWTapModule.active());
        setElementVisible(JumpResetIndicatorHud.ID, AutoJumpResetModule.active());
        setElementVisible(ReachIndicatorHud.ID, ReachModule.active());
        setElementVisible(TotemIndicatorHud.ID, LegitAutoTotemModule.active());
        setElementVisible(KillAuraButBetterIndicatorHud.ID, KillAuraButBetterModule.active());
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
