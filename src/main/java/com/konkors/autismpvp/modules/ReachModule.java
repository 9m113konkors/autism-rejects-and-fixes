package com.konkors.autismpvp.modules;

import autismclient.api.module.DoubleSetting;
import autismclient.modules.Module;
import autismclient.modules.ModuleRegistry;
import com.konkors.autismpvp.Tier;

public final class ReachModule extends Module {

    public static final String ID = "autismpvp:reach";

    // Up to 40 blocks so blatant modes are possible; servers still enforce their own limit.
    // Default is 3.05 (Vape v4 legit range): Grim simulates combat and flags attacks at ~3.06+
    // blocks, so anything above 3.1-3.2 is a ban risk on modern servers.
    private final DoubleSetting reach = add(new DoubleSetting("reach", "Reach", 3.05, 3.0, 40.0, 0.1)
        .unit("blocks")
        .group("General")
        .description("Attack/block reach in blocks. Grim flags attacks above ~3.05 blocks; keep 3.0-3.2 for legit play. Vape v4 legit configs use 3.1-3.4."));

    public ReachModule() {
        super(ID, "Reach", "Extends your interaction range beyond vanilla. Works for attacks and block interaction; servers still enforce their own limit. Grim detects attacks above ~3.05 blocks, so keep it low.");
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
