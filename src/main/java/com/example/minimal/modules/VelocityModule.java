package com.example.minimal.modules;

import autismclient.api.module.IntSetting;
import autismclient.modules.Module;
import autismclient.modules.ModuleRegistry;
import com.example.minimal.Tier;

import java.util.Random;

// Scales the knockback the local player takes. Applied from VelocityMixin on the client's
// LivingEntity.knockback, after vanilla knockback resistance, so the game stays authoritative.
public final class VelocityModule extends Module {

    public static final String ID = "autism-minimal-addon-template:velocity";
    public static volatile long lastKbMs;

    private static final Random ROLL = new Random();

    private final IntSetting horizontal = add(new IntSetting("horizontal", "Horizontal (%)", 0, 0, 100, 5)
        .unit("%").group("General"));
    private final IntSetting vertical = add(new IntSetting("vertical", "Vertical (%)", 0, 0, 100, 5)
        .unit("%").group("General"));
    private final IntSetting chance = add(new IntSetting("chance", "Chance (%)", 100, 0, 100, 5)
        .group("Timing"));

    public VelocityModule() {
        super(ID, "Velocity", "Scales the knockback you take. 0% means almost no knockback.");
    }

    @Override
    public String info() {
        return "H" + horizontal.get() + " V" + vertical.get() + " " + tier().label();
    }

    public static boolean active() {
        Module module = ModuleRegistry.get(ID);
        return module != null && module.isEnabled();
    }

    public static int horizontalPct() {
        Module module = ModuleRegistry.get(ID);
        return module instanceof VelocityModule m ? m.horizontal.get() : 0;
    }

    public static int verticalPct() {
        Module module = ModuleRegistry.get(ID);
        return module instanceof VelocityModule m ? m.vertical.get() : 0;
    }

    public static Tier tier() {
        Module module = ModuleRegistry.get(ID);
        return module instanceof VelocityModule m
            ? Tier.forVelocity(m.horizontal.get(), m.vertical.get()) : Tier.CLOSET;
    }

    // One roll per knockback so horizontal and vertical stay in sync.
    public static boolean rollPasses() {
        Module module = ModuleRegistry.get(ID);
        int chance = module instanceof VelocityModule m ? m.chance.get() : 100;
        return chance >= 100 || ROLL.nextInt(100) < chance;
    }

    public static void notifyKnockback() {
        lastKbMs = System.currentTimeMillis();
    }
}
