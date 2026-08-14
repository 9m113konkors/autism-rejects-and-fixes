package com.konkors.autismpvp.modules;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.IntSetting;
import autismclient.modules.KillAuraModule;
import autismclient.modules.Module;
import autismclient.modules.ModuleRegistry;
import com.konkors.autismpvp.Tier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

// Vape v4-style KnockbackDelay: instead of reducing the knockback you take, it HOLDS the incoming
// knockback motion packet (plus any packets that arrive behind it) for a short window and releases
// them late. You stay in place for that window, so during a combo you keep hitting while the
// opponent is still being pushed away -- which reads as extra reach on weak servers. Like Vape, it
// only engages while a valid target is near your crosshair and the chance check passes, and it
// skips liquids so it never looks suspicious there. Pairs with Velocity (delayed AND reduced).
public final class KnockbackDelayModule extends Module {

    public static final String ID = "autismpvp:knockback-delay";
    public static volatile long lastDelayMs;

    private static final Random ROLL = new Random();
    private static final int GROUNDED_FOR_TICKS = 5;

    private final IntSetting chance = add(new IntSetting("chance", "Chance (%)", 100, 0, 100, 5)
        .group("General")
        .description("Chance that incoming knockback is delayed instead of applied instantly."));
    private final IntSetting airDelay = add(new IntSetting("air-delay", "Air delay (ticks)", 4, 0, 20, 1)
        .group("Timing")
        .description("Delay used when you were not grounded long before the hit (mid-fight knockback)."));
    private final IntSetting groundDelay = add(new IntSetting("ground-delay", "Ground delay (ticks)", 10, 0, 20, 1)
        .group("Timing")
        .description("Delay used once you have been grounded for a few ticks before the hit."));
    private final IntSetting range = add(new IntSetting("range", "Target range (blocks)", 4, 3, 8, 1)
        .group("Activation")
        .description("The delay only engages while an enemy is within this distance of your crosshair."));
    private final BoolSetting waterCheck = add(new BoolSetting("water-check", "Disable in water", true)
        .group("Activation")
        .description("Never delays knockback while you are in water or lava (keeps it from looking suspicious)."));

    private int groundedTicks;
    private Vec3 heldMotion;
    private int heldTicksLeft;

    public KnockbackDelayModule() {
        super(ID, "KnockbackDelay", "Holds incoming knockback for a short window so you keep hitting while the opponent flies. Pair with Velocity.");
    }

    @Override
    public String info() {
        return "G" + groundDelay.get() + "/A" + airDelay.get() + " " + tier().label();
    }

    public static Tier tier() {
        Module module = ModuleRegistry.get(ID);
        int max = module instanceof KnockbackDelayModule m
            ? Math.max(m.airDelay.get(), m.groundDelay.get()) : 0;
        if (max <= 2) return Tier.CLOSET;
        if (max <= 5) return Tier.LEGIT;
        if (max <= 10) return Tier.RISKY;
        if (max <= 15) return Tier.BLATANT;
        return Tier.IMPOSSIBLE;
    }

    public static boolean active() {
        Module module = ModuleRegistry.get(ID);
        return module != null && module.isEnabled();
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        releaseHeldMotion();
        reset();
    }

    @Override
    public void onGameLeft() {
        reset();
    }

    // Called from VelocityMotionMixin when the server motion packet targets the local player.
    // Returns true when the motion was held for the delay window; the mixin then skips applying it.
    public static boolean holdIfNeeded(Vec3 movement) {
        Module module = ModuleRegistry.get(ID);
        if (!(module instanceof KnockbackDelayModule m) || !module.isEnabled()) return false;
        if (MC.player == null) return false;
        if (m.waterCheck.get() && (MC.player.isInWater() || MC.player.isInLava())) return false;
        if (!m.enemyNearCrosshair()) return false;
        if (m.chance.get() < 100 && ROLL.nextInt(100) >= m.chance.get()) return false;

        int delay = m.groundedTicks >= GROUNDED_FOR_TICKS ? m.groundDelay.get() : m.airDelay.get();
        if (delay <= 0) return false;
        // Motion packets can arrive back-to-back during a combo. Preserve every contribution
        // instead of silently losing all but the latest packet.
        m.heldMotion = m.heldMotion == null ? movement : m.heldMotion.add(movement);
        m.heldTicksLeft = Math.max(m.heldTicksLeft, delay);
        lastDelayMs = System.currentTimeMillis();
        return true;
    }

    // Called once per client tick from VelocityDelayMixin; drives the grounded tracker and releases
    // a held knockback once its delay elapses (routed through Velocity so the two combine).
    public static void onTick() {
        Module module = ModuleRegistry.get(ID);
        if (MC.player == null) {
            if (module instanceof KnockbackDelayModule m) m.reset();
            return;
        }
        if (module instanceof KnockbackDelayModule m && module.isEnabled()) {
            if (MC.player.onGround()) {
                m.groundedTicks++;
            } else {
                m.groundedTicks = 0;
            }
            if (m.heldMotion != null && --m.heldTicksLeft <= 0) {
                Vec3 motion = m.heldMotion;
                m.heldMotion = null;
                MC.player.setDeltaMovement(MC.player.getDeltaMovement().add(VelocityModule.reduceMotion(motion)));
                VelocityModule.notifyKnockback();
                if (VelocityModule.jumpResets()) {
                    VelocityModule.scheduleJump();
                }
            }
        }
    }

    private boolean enemyNearCrosshair() {
        LivingEntity target = null;
        Module clicker = ModuleRegistry.get(BetterAutoClickerModule.ID);
        if (clicker instanceof BetterAutoClickerModule better && better.isEnabled()) {
            target = BetterAutoClickerModule.currentTarget();
        }
        if (target == null) {
            Module aura = ModuleRegistry.get("kill-aura");
            if (aura instanceof KillAuraModule killAura && killAura.isEnabled()) {
                target = killAura.currentTarget();
            }
        }
        if (target == null && MC.crosshairPickEntity instanceof LivingEntity living && living != MC.player) {
            target = living;
        }
        if (target == null || target == MC.player) return false;
        if (MC.player.distanceToSqr(target) > (double) range.get() * range.get()) return false;
        Vec3 eye = MC.player.getEyePosition(1.0F);
        Vec3 to = target.getBoundingBox().getCenter().subtract(eye);
        double distSq = to.lengthSqr();
        if (distSq < 1.0e-4) return true;
        return MC.player.getLookAngle().dot(to.scale(1.0 / Math.sqrt(distSq))) >= 0.5;
    }

    private void reset() {
        heldMotion = null;
        heldTicksLeft = 0;
        groundedTicks = 0;
    }

    private void releaseHeldMotion() {
        if (heldMotion == null || MC.player == null || MC.getConnection() == null) return;
        MC.player.setDeltaMovement(MC.player.getDeltaMovement().add(heldMotion));
        heldMotion = null;
        heldTicksLeft = 0;
    }
}
