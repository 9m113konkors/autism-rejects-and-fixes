package com.example.minimal.modules;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.IntSetting;
import autismclient.modules.KillAuraModule;
import autismclient.modules.Module;
import autismclient.modules.ModuleRegistry;
import autismclient.util.macro.PingSpoofController;
import com.example.minimal.Tier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.List;

// Backtrack: a "delay adder" on your outgoing position packets (Vape v4 style). While the module is
// on, every ServerboundMovePlayerPacket is held by PingSpoofController and released N ms later, so
// the server still sees you where you were a moment ago while you have already moved — which reads
// as extra reach when you close the gap mid-fight. Weak-server module; not for Grim.
public final class BacktrackModule extends Module {

    public static final String ID = "autism-minimal-addon-template:backtrack";

    // Unique owner id so the override never collides with the client's own PingSpoof module slot.
    private static final long OWNER = 0x42_41_43_4B_54_52_4B_4CL; // "BACKTRK"

    private final IntSetting delay = add(new IntSetting("delay", "Delay (ms)", 150, 0, 500, 10)
        .group("Backtrack")
        .description("How long your position packets are held before being sent. Higher = the server sees you further behind your real position (more effective reach), but it is far more obvious. 50-100 subtle, 150+ very noticeable."));
    private final BoolSetting onlyInCombat = add(new BoolSetting("only-in-combat", "Only during combat", false)
        .group("Backtrack")
        .description("Only delay while an enemy is within target range of your crosshair / KillAura target. Off = delay constantly while the module is on."));
    private final IntSetting range = add(new IntSetting("range", "Target range (blocks)", 4, 3, 16, 1)
        .group("Backtrack")
        .description("Detection range for the combat gate."));

    private int appliedDelay = -1;

    public BacktrackModule() {
        super(ID, "Backtrack", "Delays your position packets so the server sees you where you were a moment ago. Pairs with Velocity + KnockbackDelay for the 15-block CatPVP combo.");
    }

    @Override
    public String info() {
        return delay.get() + "ms " + tier().label();
    }

    public static Tier tier() {
        Module module = ModuleRegistry.get(ID);
        if (module instanceof BacktrackModule backtrack) {
            return backtrack.delay.get() <= 100 ? Tier.RISKY : Tier.BLATANT;
        }
        return Tier.BLATANT;
    }

    public static boolean active() {
        Module module = ModuleRegistry.get(ID);
        return module != null && module.isEnabled();
    }

    public static int delayMs() {
        Module module = ModuleRegistry.get(ID);
        if (module instanceof BacktrackModule backtrack) {
            return backtrack.appliedDelay;
        }
        return -1;
    }

    @Override
    public void onDisable() {
        clearOverride();
    }

    @Override
    public void onGameLeft() {
        clearOverride();
    }

    @Override
    public void tick() {
        if (MC.player == null || MC.getConnection() == null) {
            clearOverride();
            return;
        }
        if (MC.gui != null && MC.gui.screen() != null) {
            return;
        }

        boolean gateOk = !onlyInCombat.get() || findTarget() != null;
        int want = gateOk ? Math.max(0, delay.get()) : 0;

        if (want > 0) {
            if (appliedDelay != want) {
                PingSpoofController.applyUntilCleared(OWNER, want, false, true);
                appliedDelay = want;
            }
        } else {
            clearOverride();
        }
    }

    private void clearOverride() {
        if (appliedDelay < 0) {
            return;
        }
        PingSpoofController.clearMacro(OWNER);
        appliedDelay = -1;
    }

    private LivingEntity findTarget() {
        double reach = range.get();
        Module aura = ModuleRegistry.get("kill-aura");
        if (aura instanceof KillAuraModule killAura && killAura.isEnabled()) {
            LivingEntity target = killAura.currentTarget();
            if (target != null && MC.player.distanceToSqr(target) <= reach * reach) {
                return target;
            }
        }
        AABB box = MC.player.getBoundingBox().inflate(reach);
        List<LivingEntity> candidates = MC.level.getEntitiesOfClass(LivingEntity.class, box,
            entity -> entity != MC.player
                && entity.isAlive()
                && !entity.isSpectator()
                && entity instanceof Player);
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity entity : candidates) {
            double d = MC.player.distanceToSqr(entity);
            if (d < bestDist) {
                bestDist = d;
                best = entity;
            }
        }
        return best;
    }
}
