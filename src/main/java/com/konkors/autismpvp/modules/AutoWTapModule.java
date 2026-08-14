package com.konkors.autismpvp.modules;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.EnumSetting;
import autismclient.api.module.IntSetting;
import autismclient.modules.Module;
import autismclient.modules.ModuleRegistry;
import com.konkors.autismpvp.Tier;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;

import java.util.Random;

public final class AutoWTapModule extends Module {

    public static final String ID = "autismpvp:auto-wtap";
    public static volatile long lastTapMs;

    public enum Mode {
        SPRINT_TAP("Sprint-Tap"),
        SNEAK_TAP("Sneak-Tap");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private enum Phase { IDLE, DELAY, HOLD }

    private final EnumSetting<Mode> mode = add(new EnumSetting<>("mode", "Mode", Mode.SPRINT_TAP, Mode.values())
        .group("General")
        .description("Sprint-Tap releases W after each hit to keep sprint; Sneak-Tap briefly sneaks instead, which also resets reach combos and looks like a block-hitter."));
    private final IntSetting delayTicks = add(new IntSetting("delay", "Delay (ticks)", 0, 0, 4, 1)
        .group("Timing")
        .description("Ticks to wait after the hit lands before tapping. 0 = tap immediately after every hit."));
    private final IntSetting minHold = add(new IntSetting("min-hold", "Min hold (ticks)", 1, 1, 3, 1)
        .group("Timing")
        .description("Minimum length of the release, in ticks."));
    private final IntSetting maxHold = add(new IntSetting("max-hold", "Max hold (ticks)", 2, 1, 5, 1)
        .group("Timing")
        .description("Maximum length of the release. A random value between min and max is chosen per tap."));
    private final IntSetting chance = add(new IntSetting("chance", "Chance (%)", 100, 0, 100, 5)
        .group("Timing")
        .description("Chance per hit that a tap happens. Lower feels like a player who occasionally forgets to w-tap."));
    private final IntSetting accuracy = add(new IntSetting("accuracy", "Accuracy (%)", 85, 0, 100, 5)
        .group("Timing")
        .description("How often the tap is perfectly on time. The remaining percentage taps a tick or two late, which reads as a human reaction instead of a scripted tap on every hit."));
    private final BoolSetting requireSprint = add(new BoolSetting("require-sprint", "Require sprinting", true)
        .group("Behavior")
        .description("Only tap while sprinting, so the reset actually registers."));
    private final BoolSetting requireCooldown = add(new BoolSetting("require-cooldown", "Require full cooldown (1.9+ servers)", false)
        .group("Behavior")
        .description("Only tap when the attack cooldown is ready. On 1.9+ servers the cooldown decides when hits land, so this keeps the tap aligned with real hits."));

    private final Random random = new Random();

    private Phase phase = Phase.IDLE;
    private int ticksLeft;
    private Mode tapMode = Mode.SPRINT_TAP;
    private boolean prevUp;
    private boolean prevShift;

    public AutoWTapModule() {
        super(ID, "Auto WTap", "Briefly taps W (or sneak) after a landed hit to push your opponent back.");
    }

    @Override
    public String info() {
        return chance.get() + "% " + tier().label();
    }

    public static Tier tier() {
        Module module = ModuleRegistry.get(ID);
        return module instanceof AutoWTapModule m ? Tier.forChance(m.chance.get()) : Tier.CLOSET;
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
            abort();
            return;
        }

        if (phase != Phase.IDLE) {
            advance();
        }
    }

    @Override
    public boolean onPacketSend(Packet<?> packet) {
        if (phase != Phase.IDLE || MC.player == null) {
            return false;
        }
        if (!(packet instanceof ServerboundAttackPacket)) {
            return false;
        }
        if (mode.get() == Mode.SPRINT_TAP) {
            if (!MC.options.keyUp.isDown()) {
                return false;
            }
            if (requireSprint.get() && !MC.player.isSprinting()) {
                return false;
            }
        }
        if (requireCooldown.get() && MC.player.getAttackStrengthScale(1.0f) < 0.95f) {
            return false;
        }
        if (chance.get() < 100 && random.nextInt(100) >= chance.get()) {
            return false;
        }

        startTap(mode.get());
        return false;
    }

    private void startTap(Mode target) {
        tapMode = target;
        prevUp = MC.options.keyUp.isDown();
        prevShift = MC.options.keyShift.isDown();
        lastTapMs = System.currentTimeMillis();
        phase = Phase.DELAY;
        ticksLeft = Math.max(0, delayTicks.get());
        if (accuracy.get() < 100 && random.nextInt(100) >= accuracy.get()) {
            ticksLeft += 1 + random.nextInt(3);
        }
    }

    private void advance() {
        if (phase == Phase.DELAY) {
            if (ticksLeft > 0) {
                ticksLeft--;
                return;
            }
            applyTap();
            int lo = Math.min(minHold.get(), maxHold.get());
            int hi = Math.max(minHold.get(), maxHold.get());
            ticksLeft = lo + random.nextInt(hi - lo + 1);
            phase = Phase.HOLD;
            return;
        }

        ticksLeft--;
        if (ticksLeft <= 0) {
            restore();
            phase = Phase.IDLE;
        }
    }

    private void applyTap() {
        if (tapMode == Mode.SPRINT_TAP) {
            MC.options.keyUp.setDown(false);
        } else {
            MC.options.keyShift.setDown(true);
        }
    }

    private void restore() {
        if (tapMode == Mode.SPRINT_TAP) {
            MC.options.keyUp.setDown(prevUp);
        } else {
            MC.options.keyShift.setDown(prevShift);
        }
    }

    private void abort() {
        if (phase != Phase.IDLE) {
            restore();
            phase = Phase.IDLE;
        }
    }
}
