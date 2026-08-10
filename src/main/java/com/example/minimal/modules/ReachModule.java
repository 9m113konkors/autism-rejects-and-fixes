package com.example.minimal.modules;

import autismclient.api.module.DoubleSetting;
import autismclient.modules.Module;
import autismclient.modules.ModuleRegistry;
import com.example.minimal.Tier;

public final class ReachModule extends Module {

    public static final String ID = "autism-minimal-addon-template:reach";

    // Up to 40 blocks so blatant modes are possible; servers still enforce their own limit.
    private final DoubleSetting reach = add(new DoubleSetting("reach", "Reach", 4.0, 3.0, 40.0, 0.1)
        .unit("blocks")
        .group("General"));

    public ReachModule() {
        super(ID, "Reach", "Extends your interaction range beyond vanilla. Works for attacks and block interaction; servers still enforce their own limit.");
    }

    @Override
    public String info() {
        return String.format("%.1f %s", reach.get(), tier().label());
    }

    public static boolean active() {
        Module module = ModuleRegistry.get(ID);
        return module != null && module.isEnabled();
    }

    public static double reach() {
        Module module = ModuleRegistry.get(ID);
        return module instanceof ReachModule r ? r.reach.get() : 0.0;
    }

    public static Tier tier() {
        Module module = ModuleRegistry.get(ID);
        return module instanceof ReachModule r ? Tier.forReach(r.reach.get()) : Tier.CLOSET;
    }
}
