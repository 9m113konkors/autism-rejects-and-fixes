package com.example.minimal.modules;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.EnumSetting;
import autismclient.api.module.IntSetting;
import autismclient.modules.KillAuraModule;
import autismclient.modules.Module;
import autismclient.modules.ModuleRegistry;
import autismclient.util.AutismKeyMappingBridge;
import com.example.minimal.Tier;
import net.minecraft.client.KeyMapping;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Random;

// Strafe assist: while the module is on and you are jumping (airborne), it holds the strafe key
// (plus W) so you get the classic jump-strafe around the enemy. Direction can be a fixed side,
// a random side per jump, or "Around target" which reads the enemy's bearing and strafes toward it
// so you orbit them mid-fight. Keys are restored to their real physical state the moment strafing
// stops, so it never locks your controls.
public final class AutoStrafeModule extends Module {

    public static final String ID = "autism-minimal-addon-template:auto-strafe";

    public enum Direction {
        AROUND_TARGET("Around target"),
        LEFT("Left"),
        RIGHT("Right"),
        RANDOM("Random");

        private final String label;

        Direction(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final EnumSetting<Direction> direction = add(new EnumSetting<>("direction", "Direction", Direction.AROUND_TARGET, Direction.values())
        .group("Strafe")
        .description("Left/Right = hold that strafe key during jumps. Random = switch side each jump. Around target = strafe toward the enemy's side so you orbit them."));
    private final BoolSetting forward = add(new BoolSetting("forward", "Hold W", true)
        .group("Strafe")
        .description("Also hold forward while strafing for the diagonal jump-strafe. Off = strafe only, so your W stays yours."));
    private final BoolSetting onlyJumping = add(new BoolSetting("only-jumping", "Only while jumping", true)
        .group("Strafe")
        .description("Strafe only while airborne. Off = strafe constantly while the module is on (only useful with Hold W off)."));
    private final IntSetting range = add(new IntSetting("range", "Target range (blocks)", 6, 3, 16, 1)
        .group("Targeting")
        .description("Scan range for the Around target mode."));
    private final BoolSetting players = add(new BoolSetting("players", "Players", true)
        .group("Targeting")
        .description("Count players as targets."));
    private final BoolSetting mobs = add(new BoolSetting("mobs", "Mobs", false)
        .group("Targeting")
        .description("Count non-player living mobs as targets."));
    private final IntSetting transitionSmooth = add(new IntSetting("transition", "Direction switch (ticks)", 2, 0, 5, 1)
        .group("Strafe")
        .description("Ticks to smoothly transition when the target crosses to your other side. 0 = instant flip."));

    private final Random random = new Random();

    private boolean wasAirborne;
    private boolean randomSide;
    private boolean holdingLeft;
    private boolean holdingRight;
    private boolean holdingForward;
    private boolean currentLeft;
    private int transitionTicksLeft;

    public AutoStrafeModule() {
        super(ID, "Auto Strafe", "Strafes around the enemy during your jumps. Pairs with KillAura + Auto Critout.");
    }

    @Override
    public String info() {
        Direction dir = direction.get();
        String dirLabel = dir == Direction.AROUND_TARGET
            ? (currentLeft ? "Left" : "Right")
            : dir.label;
        return dirLabel + " " + tier().label();
    }

    public static Tier tier() {
        Module module = ModuleRegistry.get(ID);
        if (!(module instanceof AutoStrafeModule m)) return Tier.CLOSET;
        return switch (m.direction.get()) {
            case AROUND_TARGET -> Tier.LEGIT;
            case RANDOM -> Tier.RISKY;
            case LEFT, RIGHT -> Tier.BLATANT;
        };
    }

    public static boolean active() {
        Module module = ModuleRegistry.get(ID);
        return module != null && module.isEnabled();
    }

    @Override
    public void onEnable() {
        releaseAll();
        wasAirborne = false;
        currentLeft = false;
        transitionTicksLeft = 0;
    }

    @Override
    public void onDisable() {
        releaseAll();
    }

    @Override
    public void onGameLeft() {
        releaseAll();
    }

    @Override
    public void tick() {
        if (MC.player == null || MC.gameMode == null || MC.getConnection() == null) {
            releaseAll();
            return;
        }
        if (MC.gui == null || MC.gui.screen() != null || !MC.player.isAlive()) {
            releaseAll();
            return;
        }

        boolean airborne = !MC.player.onGround() && !MC.player.getAbilities().flying;
        if (airborne && !wasAirborne) {
            randomSide = random.nextBoolean();
        }
        wasAirborne = airborne;

        Direction dir = direction.get();
        LivingEntity target = dir == Direction.AROUND_TARGET ? findTarget() : null;

        boolean shouldStrafe = !(onlyJumping.get() && !airborne)
            && (dir != Direction.AROUND_TARGET || target != null);
        if (!shouldStrafe) {
            releaseAll();
            return;
        }

        boolean left;
        if (dir == Direction.AROUND_TARGET) {
            boolean targetLeft = targetOnLeft(target);
            if (targetLeft != currentLeft && transitionTicksLeft <= 0) {
                transitionTicksLeft = transitionSmooth.get();
            }
            if (transitionTicksLeft > 0) {
                transitionTicksLeft--;
            }
            left = currentLeft;
        } else {
            left = switch (dir) {
                case LEFT -> true;
                case RIGHT -> false;
                case RANDOM -> randomSide;
                default -> false;
            };
        }
        currentLeft = left;

        updateKey(MC.options.keyLeft, left);
        updateKey(MC.options.keyRight, !left);
        updateKey(MC.options.keyUp, forward.get());
    }

    private boolean targetOnLeft(LivingEntity target) {
        Vec3 to = target.position().subtract(MC.player.position());
        double targetYaw = Math.toDegrees(Math.atan2(-to.x, to.z));
        double rel = Mth.wrapDegrees(targetYaw - MC.player.getYRot());
        return rel > 0;
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
                && (entity instanceof Player ? players.get() : mobs.get()));
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

    private void updateKey(KeyMapping key, boolean down) {
        boolean held = keyHeld(key);
        if (down && !held) {
            AutismKeyMappingBridge.of(key).autism$simulatePress(true);
            markHeld(key, true);
        } else if (!down && held) {
            AutismKeyMappingBridge.of(key).autism$simulatePress(keyActuallyDown(key));
            markHeld(key, false);
        }
    }

    private boolean keyHeld(KeyMapping key) {
        return key == MC.options.keyLeft ? holdingLeft
            : key == MC.options.keyRight ? holdingRight
            : holdingForward;
    }

    private void markHeld(KeyMapping key, boolean held) {
        if (key == MC.options.keyLeft) holdingLeft = held;
        else if (key == MC.options.keyRight) holdingRight = held;
        else holdingForward = held;
    }

    private boolean keyActuallyDown(KeyMapping key) {
        try {
            return AutismKeyMappingBridge.of(key).autism$isActuallyDown();
        } catch (Throwable ignored) {
            return key.isDown();
        }
    }

    // Releases every key we simulated, restoring each to its real physical state so the player's
    // controls are never left locked.
    private void releaseAll() {
        if (MC.options == null) {
            holdingLeft = false;
            holdingRight = false;
            holdingForward = false;
            return;
        }
        if (holdingLeft) {
            AutismKeyMappingBridge.of(MC.options.keyLeft).autism$simulatePress(keyActuallyDown(MC.options.keyLeft));
            holdingLeft = false;
        }
        if (holdingRight) {
            AutismKeyMappingBridge.of(MC.options.keyRight).autism$simulatePress(keyActuallyDown(MC.options.keyRight));
            holdingRight = false;
        }
        if (holdingForward) {
            AutismKeyMappingBridge.of(MC.options.keyUp).autism$simulatePress(keyActuallyDown(MC.options.keyUp));
            holdingForward = false;
        }
    }
}
