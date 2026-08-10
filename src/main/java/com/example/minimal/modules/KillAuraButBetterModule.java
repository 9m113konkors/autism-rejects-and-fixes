package com.example.minimal.modules;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.DoubleSetting;
import autismclient.api.module.IntSetting;
import autismclient.mixin.accessor.AutismMultiPlayerGameModeAccessor;
import autismclient.modules.KillAuraModule;
import autismclient.modules.Module;
import autismclient.modules.ModuleRegistry;
import autismclient.util.AutismKeyMappingBridge;
import com.example.minimal.Tier;
import com.example.minimal.mixin.MinecraftMissTimeAccessor;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;

import java.util.Random;

// Works alongside the host client's KillAura (aimbot): while it is targeting a player, this module
// takes over the clicking so you can tune the attack CPS (1.8-style, e.g. 15-18) with a min/max
// slider. It also hops right before a swing against a target that is standing completely still so
// those hits land as crits. KillAura keeps doing the aiming and rotation; this module's rate wins.
public final class KillAuraButBetterModule extends Module {

    public static final String ID = "autism-minimal-addon-template:killaura-better";
    public static volatile long lastCritMs;

    private enum Phase { IDLE, JUMP }

    private final IntSetting chance = add(new IntSetting("chance", "Chance (%)", 100, 0, 100, 5)
        .group("Timing"));
    private final IntSetting readyScale = add(new IntSetting("ready-scale", "Swing when cooldown at (%)", 80, 50, 100, 5)
        .group("Timing"));
    private final IntSetting minInterval = add(new IntSetting("min-interval", "Min between hops (ticks)", 6, 1, 20, 1)
        .group("Timing"));
    private final IntSetting minStill = add(new IntSetting("min-still", "Target still (ticks)", 6, 2, 60, 1)
        .group("Detection"));
    private final DoubleSetting tolerance = add(new DoubleSetting("tolerance", "Still tolerance (blocks)", 0.05, 0.0, 0.5, 0.01)
        .group("Detection"));
    private final IntSetting cpsMin = add(new IntSetting("cps-min", "CPS min", 15, 4, 20, 1)
        .group("Clicking").description("Lowest clicks per second while attacking."));
    private final IntSetting cpsMax = add(new IntSetting("cps-max", "CPS max", 18, 4, 20, 1)
        .group("Clicking").description("Highest clicks per second while attacking. Random jitter sits between min and max."));
    private final BoolSetting attackCooldown = add(new BoolSetting("attack-cooldown", "Attack cooldown", true)
        .group("Clicking").description("Only attack when the cooldown is ready so 1.9+ hits deal full damage. Off = pure 1.8-style clicking at the CPS sliders."));

    private final Random random = new Random();

    private Phase phase = Phase.IDLE;
    private int ticksLeft;
    private int stillTicks;
    private int sinceJump;
    private LivingEntity tracked;
    private double lastX;
    private double lastY;
    private double lastZ;
    private long nextClickAtMs;

    public KillAuraButBetterModule() {
        super(ID, "KillAura But Better", "Aim stays with the client KillAura while this module clicks at your tuned CPS (1.8-style) and hop-crits stationary targets.");
    }

    @Override
    public String info() {
        return cpsMin.get() + "-" + cpsMax.get() + " " + tier().label();
    }

    public static Tier tier() {
        Module module = ModuleRegistry.get(ID);
        return module instanceof KillAuraButBetterModule m ? Tier.forChance(m.chance.get()) : Tier.CLOSET;
    }

    public static boolean active() {
        Module module = ModuleRegistry.get(ID);
        return module != null && module.isEnabled();
    }

    @Override
    public void onEnable() {
        abort();
        nextClickAtMs = 0;
    }

    @Override
    public void onDisable() {
        abort();
        setMissTimeBlocked(false);
    }

    @Override
    public void onGameLeft() {
        abort();
        setMissTimeBlocked(false);
    }

    @Override
    public void tick() {
        if (MC.player == null || MC.gameMode == null || MC.getConnection() == null) {
            abort();
            return;
        }
        if (MC.gui == null || MC.gui.screen() != null || !MC.player.isAlive()) {
            releaseJump();
            phase = Phase.IDLE;
            setMissTimeBlocked(false);
            return;
        }

        // KillAura aims and tracks; we take over the clicking. Keep its own clicker out so the
        // total click rate is exactly what the CPS sliders say instead of KillAura + ours.
        setMissTimeBlocked(killAura() != null);

        if (sinceJump > 0) {
            sinceJump--;
        }

        if (phase == Phase.JUMP) {
            ticksLeft--;
            if (ticksLeft <= 0) {
                releaseJump();
                phase = Phase.IDLE;
                sinceJump = minInterval.get();
                if (clickNow()) {
                    lastCritMs = System.currentTimeMillis();
                }
            }
            return;
        }

        KillAuraModule aura = killAura();
        LivingEntity target = aura != null ? aura.currentTarget() : null;
        if (target == null) {
            tracked = null;
            stillTicks = 0;
            nextClickAtMs = 0;
            return;
        }

        updateStillness(target);

        long now = System.currentTimeMillis();
        if (nextClickAtMs == 0) {
            nextClickAtMs = now + 80 + random.nextInt(120);
        }
        if (now < nextClickAtMs) return;

        if (wantHop()) {
            pressJump();
            phase = Phase.JUMP;
            ticksLeft = 1 + random.nextInt(2);
        } else {
            clickNow();
        }
        scheduleNextClick();
    }

    private KillAuraModule killAura() {
        Module module = ModuleRegistry.get("kill-aura");
        return module instanceof KillAuraModule aura && aura.isEnabled() ? aura : null;
    }

    private boolean wantHop() {
        if (sinceJump > 0) return false;
        if (stillTicks < minStill.get()) return false;
        if (!MC.player.onGround()) return false;
        if (!eligibleToHop()) return false;
        return chance.get() >= 100 || random.nextInt(100) < chance.get();
    }

    private boolean clickNow() {
        KillAuraModule aura = killAura();
        LivingEntity target = aura != null ? aura.currentTarget() : null;
        if (target == null) return false;
        if (target == MC.player) return false;
        if (MC.player.isBlocking() || MC.player.isUsingItem()) return false;
        if (MC.player.isHandsBusy()) return false;
        double range = ReachModule.active() ? ReachModule.reach() : 4.0;
        if (MC.player.distanceToSqr(target) > range * range) return false;

        if (attackCooldown.get()
            && MC.player.getAttackStrengthScale(0.5F) * 100.0F < readyScale.get()) {
            return false;
        }

        ((AutismMultiPlayerGameModeAccessor) MC.gameMode).autism$ensureHasSentCarriedItem();
        MC.getConnection().send(new ServerboundAttackPacket(target.getId()));
        MC.player.swing(InteractionHand.MAIN_HAND);
        if (attackCooldown.get()) {
            MC.player.resetAttackStrengthTicker();
        }
        return true;
    }

    private void scheduleNextClick() {
        int min = Math.max(1, cpsMin.get());
        int max = Math.max(min, cpsMax.get());
        double cps = min + random.nextDouble() * (max - min);
        nextClickAtMs = System.currentTimeMillis() + (long) (1000.0 / cps);
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
        return MC.player.getAttackStrengthScale(0.5F) * 100.0F >= readyScale.get();
    }

    private void pressJump() {
        AutismKeyMappingBridge.of(MC.options.keyJump).autism$simulatePress(true);
        phase = Phase.JUMP;
    }

    private void releaseJump() {
        if (MC != null && MC.options != null) {
            AutismKeyMappingBridge.of(MC.options.keyJump).autism$simulatePress(false);
        }
    }

    private void setMissTimeBlocked(boolean blocked) {
        try {
            ((MinecraftMissTimeAccessor) MC).autismMinimal$setMissTime(blocked ? 100 : 0);
        } catch (Throwable ignored) {
        }
    }

    private void abort() {
        releaseJump();
        phase = Phase.IDLE;
        tracked = null;
        stillTicks = 0;
    }
}
