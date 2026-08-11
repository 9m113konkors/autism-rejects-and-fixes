package com.konkors.autismpvp.modules;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.IntSetting;
import autismclient.modules.Module;
import autismclient.modules.ModuleRegistry;
import com.konkors.autismpvp.Tier;

import java.util.Random;

public final class AutoJumpResetModule extends Module {

    public static final String ID = "autismpvp:auto-jump-reset";
    public static volatile long lastJumpMs;

    private enum Phase { IDLE, DELAY, HOLD }

    private final IntSetting chance = add(new IntSetting("chance", "Chance (%)", 100, 0, 100, 5)
        .group("Timing"));
    private final IntSetting minDelay = add(new IntSetting("min-delay", "Min reaction (ticks)", 0, 0, 4, 1)
        .group("Timing"));
    private final IntSetting maxDelay = add(new IntSetting("max-delay", "Max reaction (ticks)", 1, 0, 4, 1)
        .group("Timing"));
    private final IntSetting minHold = add(new IntSetting("min-hold", "Min hold (ticks)", 1, 1, 3, 1)
        .group("Timing"));
    private final IntSetting maxHold = add(new IntSetting("max-hold", "Max hold (ticks)", 2, 1, 3, 1)
        .group("Timing"));
    private final IntSetting accuracy = add(new IntSetting("accuracy", "Accuracy (%)", 85, 0, 100, 5)
        .group("Timing")
        .description("How often the reset is perfectly on time. The remaining percentage reacts a couple of ticks late, which reads as a slow human reaction instead of a perfect scripted jump every single hit."));
    private final BoolSetting requireGround = add(new BoolSetting("require-ground", "Require on ground", true)
        .group("Behavior"));

    private final Random random = new Random();

    private Phase phase = Phase.IDLE;
    private int ticksLeft;
    private boolean jumpHeld;
    private int prevHurtTime;

    public AutoJumpResetModule() {
        super(ID, "Auto JumpReset", "Jumps the moment you get hit to reduce knockback.");
    }

    @Override
    public String info() {
        return chance.get() + "% " + tier().label();
    }

    public static Tier tier() {
        Module module = ModuleRegistry.get(ID);
        return module instanceof AutoJumpResetModule m ? Tier.forChance(m.chance.get()) : Tier.CLOSET;
    }

    public static boolean active() {
        Module module = ModuleRegistry.get(ID);
        return module != null && module.isEnabled();
    }

    @Override
    public void onEnable() {
        abort();
    }

    @Override
    public void onDisable() {
        abort();
    }

    @Override
    public void onGameLeft() {
        abort();
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
            prevHurtTime = MC.player.hurtTime;
            return;
        }

        if (phase == Phase.IDLE && MC.player.hurtTime > prevHurtTime) {
            if (chance.get() >= 100 || random.nextInt(100) < chance.get()) {
                startReaction();
            }
        }
        prevHurtTime = MC.player.hurtTime;

        if (phase != Phase.IDLE) {
            advance();
        }
    }

    private void startReaction() {
        if (requireGround.get() && !MC.player.onGround()) {
            return;
        }
        int lo = Math.min(minDelay.get(), maxDelay.get());
        int hi = Math.max(minDelay.get(), maxDelay.get());
        if (accuracy.get() < 100 && random.nextInt(100) >= accuracy.get()) {
            lo += 2;
            hi += 4;
        }
        phase = Phase.DELAY;
        ticksLeft = lo + random.nextInt(hi - lo + 1);
    }

    private void advance() {
        if (phase == Phase.DELAY) {
            if (ticksLeft > 0) {
                ticksLeft--;
                return;
            }
            if (MC.player.onGround() && !MC.options.keyJump.isDown()) {
                MC.options.keyJump.setDown(true);
                jumpHeld = true;
                lastJumpMs = System.currentTimeMillis();
                int lo = Math.min(minHold.get(), maxHold.get());
                int hi = Math.max(minHold.get(), maxHold.get());
                ticksLeft = lo + random.nextInt(hi - lo + 1);
                phase = Phase.HOLD;
            } else {
                phase = Phase.IDLE;
            }
            return;
        }

        ticksLeft--;
        if (ticksLeft <= 0) {
            releaseJump();
            phase = Phase.IDLE;
        }
    }

    private void releaseJump() {
        if (jumpHeld) {
            MC.options.keyJump.setDown(false);
            jumpHeld = false;
        }
    }

    private void abort() {
        releaseJump();
        phase = Phase.IDLE;
        prevHurtTime = MC.player != null ? MC.player.hurtTime : 0;
    }
}
