package com.konkors.autismpvp.modules;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.IntSetting;
import autismclient.mixin.accessor.AutismMultiPlayerGameModeAccessor;
import autismclient.modules.KillAuraModule;
import autismclient.modules.Module;
import autismclient.modules.ModuleRegistry;
import com.konkors.autismpvp.Tier;
import com.konkors.autismpvp.api.RangeSetting;
import com.konkors.autismpvp.mixin.MinecraftMissTimeAccessor;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;

import java.util.Random;

// Clicks at a tuned CPS, either automatically while the client KillAura is targeting ("Attack with
// KillAura" on) or only while you hold the attack button ("Attack with KillAura" off, a classic
// autoclicker). The click rate is a min/max range on one slider with two handles. While active it
// keeps the host clickers quiet (via MC.missTime) so the total click rate is exactly the CPS here.
// Aiming always stays with the host KillAura; this module owns only the clicking. Hop-crits are
// handled by the separate Auto Critout module.
public final class BetterAutoClickerModule extends Module {

    public static final String ID = "autismpvp:better-auto-clicker";
    public static volatile long lastClickMs;

    private final BoolSetting withKillAura = add(new BoolSetting("with-killaura", "Attack with KillAura", true)
        .group("Mode")
        .description("On: automatically clicks while the client KillAura is targeting an enemy. Off: classic autoclicker that only clicks while you hold the attack (left mouse) button."));
    private final RangeSetting cps = add(new RangeSetting("cps", "CPS", 10, 13, 1, 20, 1)
        .group("Clicking")
        .description("Click rate range on one slider: drag either handle to set the lowest and highest clicks per second. Every click is randomized somewhere between the two. Vape v4 legit configs run 8-14 CPS."));
    private final BoolSetting attackCooldown = add(new BoolSetting("attack-cooldown", "Attack cooldown", true)
        .group("Clicking")
        .description("Only attack when the cooldown is charged (see 'Swing when cooldown at') so 1.9+ hits deal full damage and never outpace the server cooldown. Off = pure 1.8-style clicking at the CPS slider (likely flagged on modern servers)."));
    private final IntSetting readyScale = add(new IntSetting("ready-scale", "Swing when cooldown at (%)", 100, 50, 100, 5)
        .group("Clicking")
        .description("Minimum attack-cooldown charge before attacking. 100 = only when fully charged, which is what modern servers expect (100 is also the safest against anticheats)."));
    private final BoolSetting requireFacing = add(new BoolSetting("require-facing", "Require facing", true)
        .group("Clicking")
        .description("Only click once the target is actually under your crosshair. During KillAura's rotation snap the look is still off-target, and clicking then is what anticheats read as aimbot. Off = click the instant the timer fires regardless of aim."));
    private final IntSetting postFaceDelay = add(new IntSetting("post-face-delay", "Post-face delay (ticks)", 1, 0, 4, 1)
        .group("Anti-Cheat")
        .description("Extra ticks to wait after you first face the target before clicking. Grim reads clicks that fire immediately when rotation snaps onto a target as aimbot; an extra tick or two of facing-before-clicking looks human."));

    private final Random random = new Random();
    private long nextClickAtMs;
    private LivingEntity pendingCrit;
    private LivingEntity facingTargetAt;
    private int facingTicks;

    public BetterAutoClickerModule() {
        super(ID, "BetterAutoClicker", "Clicks at your tuned CPS range (one slider, two handles). Attack with KillAura for automatic clicking, or hold the attack button for a classic autoclicker.");
    }

    @Override
    public String info() {
        return cps.minValue() + "-" + cps.maxValue() + " " + tier().label();
    }

    public static Tier tier() {
        Module module = ModuleRegistry.get(ID);
        if (!(module instanceof BetterAutoClickerModule m)) return Tier.CLOSET;
        int avg = (m.cps.minValue() + m.cps.maxValue()) / 2;
        if (avg < 9) return Tier.CLOSET;
        if (avg < 12) return Tier.LEGIT;
        if (avg < 15) return Tier.RISKY;
        if (avg < 18) return Tier.BLATANT;
        return Tier.IMPOSSIBLE;
    }

    public static boolean active() {
        Module module = ModuleRegistry.get(ID);
        return module != null && module.isEnabled();
    }

    public static int readyScalePct() {
        Module module = ModuleRegistry.get(ID);
        return module instanceof BetterAutoClickerModule m ? m.readyScale.get() : 100;
    }

    public static boolean attackCooldownOn() {
        Module module = ModuleRegistry.get(ID);
        return !(module instanceof BetterAutoClickerModule m) || m.attackCooldown.get();
    }

    // The enemy the clicker is currently trying to hit: the KillAura target when "Attack with
    // KillAura" is on, otherwise the entity under the crosshair while the attack key is held.
    public static LivingEntity currentTarget() {
        Module module = ModuleRegistry.get(ID);
        return module instanceof BetterAutoClickerModule m && module.isEnabled() ? m.resolveTarget() : null;
    }

    private LivingEntity resolveTarget() {
        if (withKillAura.get()) {
            KillAuraModule aura = killAura();
            return aura != null ? aura.currentTarget() : null;
        }
        if (MC.options == null || !MC.options.keyAttack.isDown()) {
            return null;
        }
        LivingEntity pick = MC.crosshairPickEntity instanceof LivingEntity living && living != MC.player
            ? living : null;
        return pick;
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        reset();
        setMissTimeBlocked(false);
    }

    @Override
    public void onGameLeft() {
        reset();
        setMissTimeBlocked(false);
    }

    @Override
    public void tick() {
        if (MC.player == null || MC.gameMode == null || MC.getConnection() == null) {
            reset();
            setMissTimeBlocked(false);
            return;
        }
        if (MC.player.isSpectator() || MC.gui == null || MC.gui.screen() != null || !MC.player.isAlive()) {
            reset();
            setMissTimeBlocked(false);
            return;
        }

        // Auto Critout needs a per-tick push from the clicker so its hop timing stays in sync.
        AutoCritoutModule.onAutoClickerTick();

        // Keep the host clickers quiet while we own the clicking: with KillAura on (it aims and we
        // click), or while the attack key is held in autoclicker mode.
        boolean ownClicks = withKillAura.get() ? killAura() != null : MC.options.keyAttack.isDown();
        setMissTimeBlocked(ownClicks);
        if (!ownClicks) {
            reset();
            return;
        }

        LivingEntity target = currentTarget();
        if (target == null) {
            reset();
            return;
        }

        // A crit hop is in flight (started by Auto Critout); click as soon as it lands.
        if (pendingCrit != null) {
            LivingEntity critTarget = AutoCritoutModule.consumePendingClick();
            if (critTarget != null) {
                clickNow(critTarget);
                pendingCrit = null;
            }
            return;
        }

        long now = System.currentTimeMillis();
        if (nextClickAtMs == 0) {
            nextClickAtMs = now + 80 + random.nextInt(120);
        }
        if (now < nextClickAtMs) {
            return;
        }

        // Don't click while KillAura is still snapping its rotation onto the target: clicking off
        // the crosshair during the snap is exactly what anticheats flag as aimbot. Retry shortly.
        if (requireFacing.get() && !facingTarget(target)) {
            facingTargetAt = null;
            facingTicks = 0;
            nextClickAtMs = now + 70 + random.nextInt(60);
            return;
        }

        // Post-face delay: once we ARE facing the target, wait a few extra ticks before clicking
        // so Grim doesn't read the click as firing the instant rotation snapped onto the target.
        int delay = postFaceDelay.get();
        if (delay > 0) {
            if (facingTargetAt != target) {
                facingTargetAt = target;
                facingTicks = 0;
            }
            facingTicks++;
            if (facingTicks < delay + 1) {
                return;
            }
        }

        if (AutoCritoutModule.attemptHop(target)) {
            pendingCrit = target;
        } else {
            clickNow(target);
        }
        scheduleNextClick();
    }

    private KillAuraModule killAura() {
        Module module = ModuleRegistry.get("kill-aura");
        return module instanceof KillAuraModule aura && aura.isEnabled() ? aura : null;
    }

    private boolean clickNow(LivingEntity target) {
        if (target == null || target == MC.player) return false;
        if (MC.player.isBlocking() || MC.player.isUsingItem()) return false;
        if (MC.player.isHandsBusy()) return false;
        if (requireFacing.get() && !facingTarget(target)) return false;
        // Vanilla fallback reach when the Reach module is off: vanilla attack range is 3.0 blocks,
        // and anything further is what Grim flags as reach on EVERY click regardless of CPS.
        double range = ReachModule.active() ? Math.min(ReachModule.reach(), 3.2) : 3.0;
        if (MC.player.distanceToSqr(target) > range * range) return false;

        if (attackCooldown.get() && MC.player.getAttackStrengthScale(0.0F) * 100.0F < readyScale.get()) {
            return false;
        }

        ((AutismMultiPlayerGameModeAccessor) MC.gameMode).autism$ensureHasSentCarriedItem();
        // AutoShield: drop the block exactly when the swing goes out so the hit actually lands.
        AutoShieldModule.willAttack();
        MC.getConnection().send(new ServerboundAttackPacket(target.getId()));
        MC.player.swing(InteractionHand.MAIN_HAND);
        if (attackCooldown.get()) {
            MC.player.resetAttackStrengthTicker();
        }
        lastClickMs = System.currentTimeMillis();
        return true;
    }

    // True when the target sits roughly under the crosshair (within ~60 degrees of the look
    // direction). Used so we never click while KillAura is still rotating onto the target.
    private boolean facingTarget(LivingEntity target) {
        if (target == null) return false;
        var eye = MC.player.getEyePosition(1.0F);
        var toTarget = target.getBoundingBox().getCenter().subtract(eye);
        double distSqr = toTarget.lengthSqr();
        if (distSqr < 1.0e-4) return true;
        return MC.player.getLookAngle().dot(toTarget.scale(1.0 / Math.sqrt(distSqr))) >= 0.5;
    }

    private void scheduleNextClick() {
        int min = Math.max(1, cps.minValue());
        int max = Math.max(min, cps.maxValue());
        double cps = min + random.nextDouble() * (max - min);
        double interval = 1000.0 / cps;
        // Human-like variance: never a perfect interval (fixed spacing is the classic Grim
        // autoclick tell), plus occasional sub-100ms noise and a ~6% skipped beat.
        interval *= 0.8 + random.nextDouble() * 0.4;
        interval += 10 + random.nextDouble() * 30;
        if (random.nextInt(100) < 6) {
            interval *= 2;
        }
        nextClickAtMs = System.currentTimeMillis() + (long) interval;
    }

    private void setMissTimeBlocked(boolean blocked) {
        try {
            ((MinecraftMissTimeAccessor) MC).autismMinimal$setMissTime(blocked ? 100 : 0);
        } catch (Throwable ignored) {
        }
    }

    private void reset() {
        nextClickAtMs = 0;
        pendingCrit = null;
        facingTargetAt = null;
        facingTicks = 0;
        AutoCritoutModule.cancelHop();
    }
}
