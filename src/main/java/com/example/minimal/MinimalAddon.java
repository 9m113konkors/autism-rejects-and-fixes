package com.example.minimal;

import autismclient.api.ApiVersion;
import autismclient.api.AutismAddons;
import autismclient.api.SimpleAddon;
import com.example.minimal.hud.WtapIndicatorHud;
import com.example.minimal.modules.AutoWTapModule;

public final class MinimalAddon extends SimpleAddon {
    public static final String ID = "autism-minimal-addon-template";

    public MinimalAddon() {
        super(ApiVersion.CURRENT, "com.example.minimal");
    }

    @Override
    protected void initialize() {
        registerModule(new AutoWTapModule());
        AutismAddons.hud().register(new WtapIndicatorHud());
    }
}
