package com.example.minimal.modules;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.IntSetting;
import autismclient.modules.AutismAntiBot;
import autismclient.modules.Module;
import autismclient.modules.ModuleRegistry;
import com.example.minimal.Tier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Random;

// Aims the bow at the nearest valid target while charging. Uses basic projectile prediction:
// the arrow's speed is power * 3.0 and it drops under gravity, so the aimpoint is offset
// ahead of the target by the estimated flight time. Auto-releases the arrow at full pull.
public final class BowAimbotModule extends Module {

    public static final String ID = "autism-minimal-addon-template:bow-aimbot";

    private static final double ARROW_SPEED_MULT = 3.0;
    private static final double ARROW_GRAVITY = 0.05;
    private static final int FULL_PULL_TICKS = 20;

    private final IntSetting range = add(new IntSetting("range", "Range (blocks)", 20, 4, 64, 1)
        .group("Targeting")
        .description("Maximum distance to scan for targets."));
    private final BoolSetting players = add(new BoolSetting("players", "Players", true)
        .group("Targeting")
        .description("Target players."));
    private final BoolSetting mobs = add(new BoolSetting("mobs", "Mobs", false)
        .group("Targeting")
        .description("Target living mobs."));
    private final BoolSetting animals = add(new BoolSetting("animals", "Animals", false)
        .group("Targeting")
        .description("Target passive animals."));
    private final BoolSetting visCheck = add(new BoolSetting("visible", "Line of sight", true)
        .group("Targeting")
        .description("Only target entities you can see."));
    private final BoolSetting autoRelease = add(new BoolSetting("auto-release", "Auto release", true)
        .group("Behavior")
        .description("Release the arrow automatically at full pull."));
    private final IntSetting releaseDelay = add(new IntSetting("release-delay", "Release delay (ticks)", 0, 0, 5, 1)
        .group("Behavior")
        .description("Extra ticks to wait after full pull before releasing. Adds human-like variance."));
    private final IntSetting fov = add(new IntSetting("fov", "FOV limit", 180, 30, 180, 5)
        .group("Targeting")
        .description("Only aim at targets within this FOV cone from your crosshair."));

    private final Random random = new Random();
    private int releaseTicks;

    public BowAimbotModule() {
        super(ID, "Bow Aimbot",
            "Aims your bow at the nearest target while charging and auto-releases at full pull.");
    }

    @Override
    public String info() {
        return range.get() + "m " + tier().label();
    }

    public static Tier tier() {
        Module module = ModuleRegistry.get(ID);
        if (!(module instanceof BowAimbotModule m)) return Tier.CLOSET;
        if (!m.autoRelease.get()) return Tier.LEGIT;
        if (m.releaseDelay.get() >= 2) return Tier.LEGIT;
        return Tier.RISKY;
    }

    public static boolean active() {
        Module module = ModuleRegistry.get(ID);
        return module != null && module.isEnabled();
    }

    @Override
    public void onEnable() {
        releaseTicks = 0;
    }

    @Override
    public void onDisable() {
        releaseTicks = 0;
    }

    @Override
    public void onGameLeft() {
        releaseTicks = 0;
    }

    @Override
    public void tick() {
        if (MC.player == null || MC.gameMode == null || MC.getConnection() == null) return;
        if (MC.player.isSpectator() || MC.gui == null || MC.gui.screen() != null || !MC.player.isAlive()) return;
        if (!(MC.player.getUseItem().getItem() instanceof BowItem)) {
            releaseTicks = 0;
            return;
        }

        LivingEntity target = findTarget();
        if (target == null) return;

        int usingTicks = MC.player.getTicksUsingItem();
        aimAt(target, usingTicks);

        if (autoRelease.get() && usingTicks >= FULL_PULL_TICKS) {
            if (releaseTicks <= 0) {
                releaseTicks = Math.max(0, releaseDelay.get()) + random.nextInt(2);
            }
            if (releaseTicks > 0) {
                releaseTicks--;
                if (releaseTicks <= 0) {
                    MC.gameMode.releaseUsingItem(MC.player);
                }
            }
        }
    }

    private void aimAt(LivingEntity target, int usingTicks) {
        Vec3 eyePos = MC.player.getEyePosition();
        float power = BowItem.getPowerForTime(usingTicks);
        double arrowSpeed = power * ARROW_SPEED_MULT;

        Vec3 targetPos = target.position().add(0, target.getBbHeight() * 0.5, 0);
        Vec3 targetVel = target.getDeltaMovement();

        double bestYaw = MC.player.getYRot();
        double bestPitch = MC.player.getXRot();
        double bestScore = Double.MAX_VALUE;

        for (int predictTicks = 1; predictTicks <= 40; predictTicks++) {
            Vec3 predicted = targetPos.add(targetVel.scale(predictTicks));
            Vec3 toTarget = predicted.subtract(eyePos);
            double dist = toTarget.length();
            if (dist < 0.5 || dist > range.get()) continue;

            double flightTime = dist / arrowSpeed;
            double gravityDrop = 0.5 * ARROW_GRAVITY * flightTime * flightTime;
            predicted = predicted.subtract(0, gravityDrop, 0);

            Vec3 dir = predicted.subtract(eyePos);
            double horizontalDist = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
            double yaw = Math.toDegrees(Math.atan2(-dir.x, dir.z));
            double pitch = Math.toDegrees(Math.atan2(-dir.y, horizontalDist));
            pitch = Math.max(-90, Math.min(90, pitch));

            double yawDiff = Math.abs(net.minecraft.util.Mth.wrapDegrees((float)(yaw - MC.player.getYRot())));
            if (yawDiff > fov.get()) continue;

            double score = yawDiff + Math.abs(pitch - MC.player.getXRot()) * 0.5;
            if (score < bestScore) {
                bestScore = score;
                bestYaw = yaw;
                bestPitch = pitch;
            }
        }

        if (bestScore < Double.MAX_VALUE) {
            MC.player.setYRot((float) bestYaw);
            MC.player.setXRot((float) bestPitch);
        }
    }

    private LivingEntity findTarget() {
        double reach = range.get();
        double reachSq = reach * reach;
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;

        List<LivingEntity> candidates = MC.level.getEntitiesOfClass(LivingEntity.class,
            MC.player.getBoundingBox().inflate(reach),
             entity -> entity != MC.player
                && entity.isAlive()
                && !entity.isSpectator()
                && AutismAntiBot.isBot(entity) == false
                && MC.player.distanceToSqr(entity) <= reachSq
                && (entity instanceof Player ? players.get()
                    : entity instanceof Animal ? animals.get()
                    : mobs.get()));

        for (LivingEntity entity : candidates) {
            if (visCheck.get() && !MC.player.hasLineOfSight(entity)) continue;
            double d = MC.player.distanceToSqr(entity);
            if (d < bestDist) {
                bestDist = d;
                best = entity;
            }
        }
        return best;
    }
}
