package com.konkors.autismpvp.modules;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.DoubleSetting;
import autismclient.api.module.EnumSetting;
import autismclient.api.module.IntSetting;
import autismclient.modules.Module;
import autismclient.modules.ModuleRegistry;
<<<<<<< HEAD:src/main/java/com/example/minimal/modules/VelocityModule.java
import autismclient.util.AutismKeyMappingBridge;
import com.example.minimal.Tier;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
=======
import com.konkors.autismpvp.Tier;
>>>>>>> b46b0d5ba813af2c2b0d4860bde92eae69a4568e:src/main/java/com/konkors/autismpvp/modules/VelocityModule.java

import java.util.Random;

// Scales the knockback the local player takes. On modern servers the knockback is applied from the
// ClientboundSetEntityMotionPacket (see VelocityMotionMixin) after the server computed it, so an
// instant 0% looks like the client fighting the server's own velocity to anticheats. Three modes:
//  - Reduce (default, Vape-style): keep most of the knockback (default 85%/90%) and optionally
//    delay the reduction a few ticks so the server-expected motion plays out first. Big reductions
//    are what Grim flags ("if you are moving, you will flag antikb").
//  - Smooth (Vulcan-safest reduce): instead of applying a flat percentage in one shot, gradually
//    decays the knockback over several ticks. The total displacement matches what the server
//    predicted more closely, so packet-analysis checks see a natural friction curve instead of
//    an abrupt velocity cut.
//  - Jump Reset (Grim-safest): keep 100% knockback and jump the instant the knockback packet lands.
//    Jumping is a vanilla mechanic that cancels knockback by taking the player out of the server's
//    movement sim, so Grim reads a legit jump instead of reduced velocity.
public final class VelocityModule extends Module {

    public static final String ID = "autismpvp:velocity";
    public static volatile long lastKbMs;

    public enum Mode {
        REDUCE("Reduce"),
        SMOOTH("Smooth"),
        JUMP_RESET("Jump Reset"),
        PACKET("Packet");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final Random ROLL = new Random();
    private static final Random JITTER = new Random();
    private static final Random SMOOTH_RND = new Random();
    private static Vec3 pendingScaled;
    private static int pendingTicksLeft;
    private static double pendingJitter = 1.0;
    private static int pendingJumpTicks;
    private static boolean jumpHeld;

    // Smooth decay state: ticks remaining, original motion, current fraction applied
    private static int smoothTicksLeft;
    private static Vec3 smoothOriginal;

    private final EnumSetting<Mode> mode = add(new EnumSetting<>("mode", "Mode", Mode.REDUCE, Mode.values())
        .group("General")
        .description("Reduce = instant flat percent (Vape-style). Smooth = gradual decay over several ticks (Vulcan-safest reduce). Jump Reset = full KB then jump (Grim-safest). Packet = cancel the knockback packet entirely (blatant, for practice servers)."));
    private final IntSetting horizontal = add(new IntSetting("horizontal", "Horizontal (%)", 85, 0, 100, 5)
        .unit("%").group("General")
        .description("Percent of the knockback you keep. Vape v4 legit configs run 85-95; big reductions are what Grim flags."));
    private final IntSetting vertical = add(new IntSetting("vertical", "Vertical (%)", 90, 0, 100, 5)
        .unit("%").group("General")
        .description("Percent of vertical knockback you keep. Grim checks vertical offsets tightly, so stay high (85+)."));
    private final IntSetting chance = add(new IntSetting("chance", "Chance (%)", 100, 0, 100, 5)
        .group("Timing")
        .description("Chance per hit that the velocity effect applies. 100 = every hit."));
    private final IntSetting delay = add(new IntSetting("delay", "Delay reduction (ticks)", 0, 0, 4, 1)
        .group("Timing")
        .description("Keep the full server knockback for this many ticks before the reduction kicks in, so each hit still moves you the way the server predicted first. 0 = reduce instantly. Reduce/Smooth mode only."));
    private final IntSetting jitter = add(new IntSetting("jitter", "Jitter (%)", 10, 0, 40, 5)
        .group("Timing")
        .description("Random variance added to the reduced knockback each hit so the reduction is never the same flat percent twice. Keep low (0-10) on Grim. Reduce/Smooth mode only."));
    private final IntSetting smoothTicks = add(new IntSetting("smooth-ticks", "Decay ticks", 4, 2, 10, 1)
        .group("Smooth")
        .description("How many ticks the knockback decays over in Smooth mode. Longer = more gradual but the player drifts further from where the server thinks they are."));
    private final DoubleSetting smoothRandomness = add(new DoubleSetting("smooth-randomness", "Decay randomness", 0.15, 0.0, 0.5, 0.05)
        .group("Smooth")
        .description("How much per-tick decay varies randomly. Higher looks more human but gives less consistent reduction."));

    public VelocityModule() {
        super(ID, "Velocity", "Scales the knockback you take. Jump Reset is Grim-safest; Smooth is Vulcan-safest; keep 85-95% in Reduce to look legit.");
    }

    @Override
    public String info() {
        Mode m = mode.get();
        return switch (m) {
            case JUMP_RESET -> "JumpReset " + tier().label();
            case SMOOTH -> "Smooth " + tier().label();
            case PACKET -> "Packet " + tier().label();
            default -> "H" + horizontal.get() + " V" + vertical.get() + " " + tier().label();
        };
    }

    public static boolean active() {
        Module module = ModuleRegistry.get(ID);
        return module != null && module.isEnabled();
    }

    public static Mode currentMode() {
        Module module = ModuleRegistry.get(ID);
        return module instanceof VelocityModule m ? m.mode.get() : Mode.REDUCE;
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
        if (!(module instanceof VelocityModule m)) return Tier.CLOSET;
        Mode m2 = m.mode.get();
        if (m2 == Mode.JUMP_RESET) return Tier.CLOSET;
        if (m2 == Mode.PACKET) return Tier.IMPOSSIBLE;
        if (m2 == Mode.SMOOTH) {
            int avg = (m.horizontal.get() + m.vertical.get()) / 2;
            return avg >= 90 ? Tier.CLOSET : avg >= 75 ? Tier.LEGIT : avg >= 55 ? Tier.RISKY : Tier.BLATANT;
        }
        return Tier.forVelocity(m.horizontal.get(), m.vertical.get());
    }

    // One roll per knockback so horizontal and vertical stay in sync.
    public static boolean rollPasses() {
        Module module = ModuleRegistry.get(ID);
        int chance = module instanceof VelocityModule m ? m.chance.get() : 100;
        return chance >= 100 || ROLL.nextInt(100) < chance;
    }

    public static boolean jumpResets() {
        Module module = ModuleRegistry.get(ID);
        return module instanceof VelocityModule m && m.mode.get() == Mode.JUMP_RESET;
    }

    public static boolean smoothMode() {
        Module module = ModuleRegistry.get(ID);
        return module instanceof VelocityModule m && m.mode.get() == Mode.SMOOTH;
    }

    public static boolean packetMode() {
        Module module = ModuleRegistry.get(ID);
        return module instanceof VelocityModule m && m.mode.get() == Mode.PACKET;
    }

    public static void notifyKnockback() {
        lastKbMs = System.currentTimeMillis();
    }

    public static int delayTicks() {
        Module module = ModuleRegistry.get(ID);
        return module instanceof VelocityModule m ? m.delay.get() : 0;
    }

    public static int jitterPct() {
        Module module = ModuleRegistry.get(ID);
        return module instanceof VelocityModule m ? m.jitter.get() : 0;
    }

    public static int smoothDuration() {
        Module module = ModuleRegistry.get(ID);
        return module instanceof VelocityModule m ? m.smoothTicks.get() : 4;
    }

    public static double smoothRandomness() {
        Module module = ModuleRegistry.get(ID);
        return module instanceof VelocityModule m ? m.smoothRandomness.get() : 0.15;
    }

    // Applies the configured reduction to a knockback motion vector. Used when KnockbackDelay
    // releases a held knockback so the two modules combine: the knockback is both delayed AND
    // reduced (Vape recommends running them together). Jump Reset mode returns the motion untouched
    // (the caller schedules the jump instead). Smooth mode returns untouched too (it has its own
    // gradual decay path).
    public static Vec3 reduceMotion(Vec3 motion) {
        if (!active() || jumpResets() || smoothMode() || !rollPasses()) return motion;
        int h = horizontalPct();
        int v = verticalPct();
        if (h == 100 && v == 100) return motion;
        return new Vec3(motion.x * h / 100.0, motion.y * v / 100.0, motion.z * h / 100.0);
    }

    // Called from VelocityMotionMixin when the reduction must wait: the full server knockback is
    // applied immediately, and the reduced (scaled) motion is applied once the delay elapses.
    public static void schedule(Vec3 scaled) {
        pendingScaled = scaled;
        pendingTicksLeft = Math.max(0, delayTicks());
        pendingJitter = 1.0 + JITTER.nextDouble() * (jitterPct() / 100.0);
    }

    // Called from VelocityMotionMixin for Smooth mode: starts the gradual decay from full motion
    // down to the configured percentage over smoothDuration ticks.
    public static void scheduleSmooth(Vec3 original) {
        int delay = delayTicks();
        // Vulcan requires at least 2 ticks of full knockback before any reduction,
        // otherwise the velocity change looks artificial. Enforce a minimum.
        delay = Math.max(delay, 2);
        if (delay > 0) {
            pendingScaled = original;
            pendingTicksLeft = delay;
            pendingJitter = -1.0;
        } else {
            startSmoothDecay(original);
        }
    }

    private static void startSmoothDecay(Vec3 original) {
        smoothOriginal = original;
        smoothTicksLeft = smoothDuration();
    }

    // Called from VelocityMotionMixin when Jump Reset mode is on: press the jump key shortly after
    // the knockback packet lands (the packet's own motion is still applied untouched).
    public static void scheduleJump() {
        pendingJumpTicks = 1;
    }

    // Called once per client tick from VelocityDelayMixin; drives the delayed reduction, the
    // smooth decay, and the jump-reset key press/release.
    public static void onTick() {
        Minecraft client = Minecraft.getInstance();

        if (pendingJumpTicks > 0) {
            pendingJumpTicks--;
            if (pendingJumpTicks == 0) {
                if (jumpHeld) {
                    setJump(client, false);
                    jumpHeld = false;
                } else if (client.player != null && client.player.onGround() && !client.options.keyJump.isDown()) {
                    setJump(client, true);
                    jumpHeld = true;
                    pendingJumpTicks = 1;
                }
            }
        }

        // Delayed reduce/smooth: the pending motion was held while we waited
        if (pendingScaled != null && pendingTicksLeft > 0) {
            pendingTicksLeft--;
            if (pendingTicksLeft <= 0) {
                Vec3 held = pendingScaled;
                pendingScaled = null;
                if (pendingJitter < 0) {
                    // Sentinel: was a smooth-mode hold — start the decay from full motion now
                    startSmoothDecay(held);
                } else if (client.player != null) {
                    client.player.setDeltaMovement(
                        held.x * pendingJitter, held.y * pendingJitter, held.z * pendingJitter);
                }
                return;
            }
            return;
        }

         // Smooth decay: each tick we move the motion closer to the target reduction.
        // Vulcan expects natural exponential friction, not a linear ramp. We start at
        // full motion and each tick multiply toward the target percentage using an
        // exponential curve, so the displacement matches a natural slowdown.
        if (smoothTicksLeft > 0 && smoothOriginal != null) {
            if (client.player == null) {
                smoothTicksLeft = 0;
                smoothOriginal = null;
                return;
            }
            Module module = ModuleRegistry.get(ID);
            int h = module instanceof VelocityModule m ? m.horizontal.get() : 100;
            int v = module instanceof VelocityModule m ? m.vertical.get() : 100;
            double rnd = module instanceof VelocityModule m ? m.smoothRandomness.get() : 0.15;

            smoothTicksLeft--;
            double steps = (double) smoothDuration();
            double progress = 1.0 - ((double) smoothTicksLeft / Math.max(1.0, steps));
            // Add small randomness but keep it bounded for Vulcan safety
            progress = Math.min(1.0, Math.max(0.0, progress + (SMOOTH_RND.nextDouble() * rnd - rnd / 2.0)));

            double hTarget = Math.max(0.01, h / 100.0);
            double vTarget = Math.max(0.01, v / 100.0);
            // Exponential interpolation: the motion scales exponentially toward the target,
            // which matches how Vulcan models natural knockback decay (friction-based).
            double hExp = Math.pow(hTarget, progress);
            double vExp = Math.pow(vTarget, progress);
            client.player.setDeltaMovement(
                smoothOriginal.x * hExp,
                smoothOriginal.y * vExp,
                smoothOriginal.z * hExp);
        }
    }

    private static void setJump(Minecraft client, boolean down) {
        try {
            AutismKeyMappingBridge.of(client.options.keyJump).autism$simulatePress(down);
        } catch (Throwable ignored) {
        }
    }
}
