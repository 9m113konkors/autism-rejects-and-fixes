package com.example.minimal.modules;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.DoubleSetting;
import autismclient.api.module.IntSetting;
import autismclient.modules.Module;
import autismclient.modules.ModuleRegistry;
import autismclient.util.AutismKeyMappingBridge;
import com.example.minimal.Tier;
import net.minecraft.world.entity.LivingEntity;

import java.util.Random;

// Hops right before a swing so the hit lands as a crit. It only hops when the clicker (the addon's
// BetterAutoClicker) is actually attacking: the clicker pushes a per-tick update into this module
// and asks whether to hop before each click; if a hop starts, the clicker holds its swing until the
// hop is done (the ascending window), making that swing a crit. Detection is per target: the hop
// only fires once the target has stood still for a while, which is what stationary players at a
// knockback farm look like.
public final class AutoCritoutModule extends Module {

    public static final String ID = "autism-minimal-addon-template:auto-critout";
    public static volatile long lastCritMs;

    private enum Phase { IDLE, HOP }

    private final IntSetting chance = add(new IntSetting("chance", "Chance (%)", 100, 0, 100, 5)
        .group("Timing"));
    private final IntSetting minInterval = add(new IntSetting("min-interval", "Min between hops (ticks)", 6, 1, 20, 1)
        .group("Timing"));
    private final IntSetting minHold = add(new IntSetting("min-hold", "Min hop hold (ticks)", 1, 1, 3, 1)
        .group("Timing"));
    private final IntSetting maxHold = add(new IntSetting("max-hold", "Max hop hold (ticks)", 2, 1, 3, 1)
        .group("Timing"));
    private final IntSetting minStill = add(new IntSetting("min-still", "Target still (ticks)", 6, 2, 60, 1)
        .group("Detection"));
    private final DoubleSetting tolerance = add(new DoubleSetting("tolerance", "Still tolerance (blocks)", 0.05, 0.0, 0.5, 0.01)
        .group("Detection"));
    private final BoolSetting requireFall = add(new BoolSetting("require-fall", "Require falling", false)
        .group("Anti-Cheat")
        .description("Only hop when you are falling (negative Y motion), not when jumping up. Grim only registers crits during a descent, so this prevents false crits that ACs flag. Disable if you want to crit right off a jump."));
    private final BoolSetting preDelay = add(new BoolSetting("pre-delay", "Pre delay", false)
        .group("Anti-Cheat")
        .description("Add a 1-tick delay after deciding to hop before actually jumping. Spreads out the jump-attack timing so it reads less like a scripted 1-tick window on every hit."));

    private final Random random = new Random();

    private Phase phase = Phase.IDLE;
    private int ticksLeft;
    private int sinceJump;
    private boolean hopReady;
    private LivingEntity pendingTarget;
    private LivingEntity pendingHopTarget;
    private int preDelayTicks;
    private LivingEntity tracked;
    private double lastX;
    private double lastY;
    private double lastZ;
    private int stillTicks;

    public AutoCritoutModule() {
        super(ID, "Auto Critout", "Hops before your swings so stationary targets take crits. Works together with the addon's BetterAutoClicker in both of its modes.");
    }

    @Override
    public String info() {
        return chance.get() + "% " + tier().label();
    }

    public static Tier tier() {
        Module module = ModuleRegistry.get(ID);
        return module instanceof AutoCritoutModule m ? Tier.forChance(m.chance.get()) : Tier.CLOSET;
    }

    public static boolean active() {
        Module module = ModuleRegistry.get(ID);
        return module != null && module.isEnabled();
    }

    // ---- driven by BetterAutoClicker ----

    public static void onAutoClickerTick() {
        Module module = ModuleRegistry.get(ID);
        if (module instanceof AutoCritoutModule m && module.isEnabled()) {
            m.onTick();
        }
    }

    public static boolean attemptHop(LivingEntity target) {
        Module module = ModuleRegistry.get(ID);
        return module instanceof AutoCritoutModule m && module.isEnabled() && m.tryHop(target);
    }

    // Returns the target to swing at once the hop is done (or null if the hop is still rising).
    public static LivingEntity consumePendingClick() {
        Module module = ModuleRegistry.get(ID);
        return module instanceof AutoCritoutModule m && module.isEnabled() ? m.consume() : null;
    }

    public static void cancelHop() {
        Module module = ModuleRegistry.get(ID);
        if (module instanceof AutoCritoutModule m) {
            m.cancel();
        }
    }

    private void onTick() {
        if (MC.player == null || MC.gameMode == null || MC.getConnection() == null) {
            abort();
            return;
        }
        if (MC.gui == null || MC.gui.screen() != null || MC.player.isSpectator() || !MC.player.isAlive()) {
            abort();
            return;
        }
        if (sinceJump > 0) {
            sinceJump--;
        }
        if (phase == Phase.HOP) {
            if (!hopReady && --ticksLeft <= 0) {
                hopReady = true;
            }
            return;
        }
        // Process pre-delay tick before looking for targets
        if (preDelayTicks > 0) {
            preDelayTicks--;
            if (preDelayTicks <= 0 && pendingHopTarget != null) {
                LivingEntity t = pendingHopTarget;
                pendingHopTarget = null;
                executeHop(t);
            }
            return;
        }
        LivingEntity target = BetterAutoClickerModule.currentTarget();
        if (target == null) {
            tracked = null;
            stillTicks = 0;
            return;
        }
        updateStillness(target);
    }

    private boolean tryHop(LivingEntity target) {
        if (phase != Phase.IDLE) return false;
        if (target == null || target == MC.player) return false;
        if (sinceJump > 0) return false;
        if (stillTicks < minStill.get()) return false;
        if (!MC.player.onGround()) return false;
        if (requireFall.get() && MC.player.getDeltaMovement().y > 0.01) return false;
        if (!eligibleToHop()) return false;
        if (chance.get() < 100 && random.nextInt(100) >= chance.get()) return false;

        if (preDelay.get()) {
            pendingHopTarget = target;
            preDelayTicks = 1;
            return false;
        }

        return executeHop(target);
    }

    private boolean executeHop(LivingEntity target) {
        if (phase != Phase.IDLE || target == null || target == MC.player) return false;
        pressJump();
        pendingTarget = target;
        phase = Phase.HOP;
        hopReady = false;
        int lo = Math.min(minHold.get(), maxHold.get());
        int hi = Math.max(minHold.get(), maxHold.get());
        ticksLeft = lo + random.nextInt(hi - lo + 1);
        return true;
    }

    private LivingEntity consume() {
        if (phase != Phase.HOP || !hopReady) {
            return null;
        }
        releaseJump();
        phase = Phase.IDLE;
        hopReady = false;
        sinceJump = minInterval.get();
        LivingEntity target = pendingTarget;
        pendingTarget = null;
        if (target != null) {
            lastCritMs = System.currentTimeMillis();
        }
        return target;
    }

    private void cancel() {
        if (phase == Phase.HOP) {
            releaseJump();
        }
        phase = Phase.IDLE;
        hopReady = false;
        pendingTarget = null;
        pendingHopTarget = null;
        preDelayTicks = 0;
    }

    private void updateStillness(LivingEntity target) {
        if (tracked != target) {
            tracked = target;
            lastX = target.getX();
            lastY = target.getY();
            lastZ = target.getZ();
            stillTicks = 0;
            return;
        }
        double dx = target.getX() - lastX;
        double dy = target.getY() - lastY;
        double dz = target.getZ() - lastZ;
        lastX = target.getX();
        lastY = target.getY();
        lastZ = target.getZ();
        double moved = Math.sqrt(dx * dx + dy * dy + dz * dz);
        stillTicks = moved <= tolerance.get() ? stillTicks + 1 : 0;
    }

    private boolean eligibleToHop() {
        if (MC.player.isInLiquid() || MC.player.isFallFlying()) return false;
        if (MC.player.getAbilities().flying) return false;
        if (MC.player.isHandsBusy()) return false;
        return MC.player.getAttackStrengthScale(0.0F) * 100.0F >= BetterAutoClickerModule.readyScalePct();
    }

    private void pressJump() {
        AutismKeyMappingBridge.of(MC.options.keyJump).autism$simulatePress(true);
    }

    private void releaseJump() {
        if (MC != null && MC.options != null) {
            AutismKeyMappingBridge.of(MC.options.keyJump).autism$simulatePress(false);
        }
    }

    private void abort() {
        if (phase == Phase.HOP) {
            releaseJump();
        }
        phase = Phase.IDLE;
        hopReady = false;
        pendingTarget = null;
        pendingHopTarget = null;
        preDelayTicks = 0;
        tracked = null;
        stillTicks = 0;
    }
}
