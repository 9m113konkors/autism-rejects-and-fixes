package com.example.minimal.modules;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.DoubleSetting;
import autismclient.api.module.EnumSetting;
import autismclient.api.module.IntSetting;
import autismclient.modules.Module;
import autismclient.modules.ModuleRegistry;
import com.example.minimal.Tier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Port of the open-source Trouser-Streak (Meteor addon, etianl/Kimtaeho) SpearKill for this addon.
// The server's "spear" is a custom item (any item whose registry/id/name contains "spear"): holding
// right-click charges it, and the harder you hit a target the more velocity damage the spear deals.
// Two modes:
//  - Lunge (default): aimbot + instant velocity shot toward the target once the spear is charged,
//    with optional From-Above lunges (fly above the target, then dive down into them).
//  - Blink: buffers your ServerboundMovePlayerPacket while charging so you walk normally on the
//    server's screen, then flushes the buffered distance in one tick for a massive velocity spike.
// This module only works with the spear item - it never touches tridents.
public final class SpearKillModule extends Module {

    public static final String ID = "autism-minimal-addon-template:spear-kill";

    public enum Mode {
        LUNGE,
        BLINK
    }

    public enum LungeDirection {
        DIRECTION_BASED,
        FROM_ABOVE,
        AUTO_FROM_ABOVE_FIRST
    }

    private final EnumSetting<Mode> mode = add(new EnumSetting<>("mode", "Mode", Mode.LUNGE, Mode.values())
        .group("General")
        .description("Lunge = velocity boost toward the target once charged. Blink = buffer move packets while charging, then flush them for a huge velocity spike."));
    private final BoolSetting autoUse = add(new BoolSetting("auto-use", "Auto Use", false)
        .group("General")
        .description("Automatically start charging the spear (right-click) for you. Off = you hold right-click with the spear."));
    private final IntSetting maxRangeSetting = add(new IntSetting("range", "Max Targeting Range", 256, 16, 512, 8)
        .group("Targeting")
        .description("How far away entities can still be targeted."));
    private final BoolSetting players = add(new BoolSetting("players", "Players", true)
        .group("Targeting")
        .description("Target players."));
    private final BoolSetting mobs = add(new BoolSetting("mobs", "Mobs", true)
        .group("Targeting")
        .description("Target non-player living mobs."));
    private final IntSetting fov = add(new IntSetting("fov", "Targeting FOV", 90, 10, 180, 5)
        .group("Targeting")
        .description("Aim cone used to pick a target. Lower = only targets you are roughly facing."));

    private final EnumSetting<LungeDirection> lungeDirection = add(new EnumSetting<>("lunge-direction", "Lunge Direction", LungeDirection.DIRECTION_BASED, LungeDirection.values())
        .group("Lunge")
        .visibleWhen(() -> mode.get() == Mode.LUNGE)
        .description("Direction Based = lunge straight at the target. From Above = fly to a spot above the target, then dive down into them. Auto From Above First = prefer From Above, fall back to a straight lunge when the above path is blocked."));
    private final DoubleSetting aboveHeight = add(new DoubleSetting("above-height", "Above Height", 10.0, 5.0, 50.0, 1.0)
        .group("Lunge")
        .visibleWhen(() -> mode.get() == Mode.LUNGE && lungeDirection.get() != LungeDirection.DIRECTION_BASED)
        .description("Blocks above the target center to lunge to before diving down."));
    private final DoubleSetting aboveTrigger = add(new DoubleSetting("above-trigger", "Above Height Trigger Distance", 3.0, 1.0, 10.0, 0.5)
        .group("Lunge")
        .visibleWhen(() -> mode.get() == Mode.LUNGE && lungeDirection.get() != LungeDirection.DIRECTION_BASED)
        .description("If within this distance of the above position, switch from climbing to diving onto the target."));
    private final BoolSetting validateAbove = add(new BoolSetting("validate-above", "Validate Above Path", true)
        .group("Lunge")
        .visibleWhen(() -> mode.get() == Mode.LUNGE && lungeDirection.get() == LungeDirection.AUTO_FROM_ABOVE_FIRST)
        .description("Checks the area around the above position and the dive path for hazards before committing to a From Above lunge."));
    private final DoubleSetting lungeStrength = add(new DoubleSetting("spear-velocity", "Spear Velocity", 5.0, 1.0, 10.0, 0.5)
        .group("Lunge")
        .visibleWhen(() -> mode.get() == Mode.LUNGE)
        .description("The velocity applied to the player in the direction of the target. Higher = faster impact and more spear damage."));
    private final BoolSetting stopOnTarget = add(new BoolSetting("stop-on-target", "Stop on Target", true)
        .group("Lunge")
        .visibleWhen(() -> mode.get() == Mode.LUNGE)
        .description("Kills your momentum the moment you reach the target so you don't overshoot."));
    private final DoubleSetting stopDistance = add(new DoubleSetting("stop-distance", "Stop Distance", 2.0, 0.0, 10.0, 0.5)
        .group("Lunge")
        .visibleWhen(() -> mode.get() == Mode.LUNGE && stopOnTarget.get())
        .description("Distance between your hitbox and the target's hitbox to consider yourself 'there'."));
    private final IntSetting delayModifier = add(new IntSetting("lunge-delay-modifier", "Lunge Delay Modifier (%)", 100, 0, 100, 5)
        .group("Lunge")
        .visibleWhen(() -> mode.get() == Mode.LUNGE)
        .description("Percent of the spear's charge time to wait before lunging. 100 = full charge (max damage), lower = lunge sooner."));

    private final BoolSetting blinkLunge = add(new BoolSetting("blink-lunge", "Blink + Lunge", false)
        .group("Blink")
        .visibleWhen(() -> mode.get() == Mode.BLINK)
        .description("Combine Blink with a velocity lunge: launch toward the target after a charge delay, then flush on impact."));
    private final DoubleSetting blinkLungeStrength = add(new DoubleSetting("blink-lunge-strength", "Lunge Strength", 1.0, 0.1, 2.0, 0.1)
        .group("Blink")
        .visibleWhen(() -> mode.get() == Mode.BLINK && blinkLunge.get())
        .description("Velocity applied toward the target in Blink + Lunge mode."));
    private final IntSetting blinkLungeTicks = add(new IntSetting("blink-lunge-delay", "Lunge Delay (ticks)", 15, 1, 30, 1)
        .group("Blink")
        .visibleWhen(() -> mode.get() == Mode.BLINK && blinkLunge.get())
        .description("Ticks to charge before the lunge launches. Also pauses re-lunging after a flush."));
    private final DoubleSetting flushRange = add(new DoubleSetting("flush-range", "Flush Range", 3.0, 1.0, 10.0, 0.5)
        .group("Blink")
        .visibleWhen(() -> mode.get() == Mode.BLINK)
        .description("Distance to the target at which the buffered movement is flushed for the velocity spike."));
    private final DoubleSetting forceFlush = add(new DoubleSetting("force-flush", "Force Flush Distance", 9.5, 1.0, 20.0, 0.5)
        .group("Blink")
        .visibleWhen(() -> mode.get() == Mode.BLINK && !blinkLunge.get())
        .description("Distance traveled since blink started that forces a flush, so the trick always fires."));
    private final BoolSetting blinkAimbot = add(new BoolSetting("aimbot", "Aimbot", true)
        .group("Blink")
        .visibleWhen(() -> mode.get() == Mode.BLINK)
        .description("Lock onto the target while charging."));
    private final DoubleSetting distanceBoost = add(new DoubleSetting("distance-boost", "Distance Boost", 0.0, 0.0, 10.0, 0.5)
        .group("Blink")
        .visibleWhen(() -> mode.get() == Mode.BLINK)
        .description("Extra blocks added to the flushed start position (behind you, when no wall blocks it) to extend the travel distance."));

    private final List<ServerboundMovePlayerPacket> packets = new ArrayList<>();
    private boolean isBlinking;
    private boolean isFlushing;
    private Vec3 startPos;
    private boolean wasCharging;
    private boolean currentlyCharging;
    private double lastTargetDistance = Double.MAX_VALUE;
    private boolean wasApproaching;
    private Entity killtarget;
    private int blinkChargeTicks;
    private int flushCooldown;
    private boolean firstPhase;
    private Vec3 aboveTargetPos;
    private final Map<Vec3, Boolean> positionCache = new HashMap<>();

    public SpearKillModule() {
        super(ID, "SpearKill",
            "Spear one-shot (Trouser-Streak port): hold right-click with the server's spear to charge, aimbot locks on, then Lunge (velocity shot) or Blink (packet flush) for massive spear damage. Spear only, never tridents.");
    }

    @Override
    public String info() {
        return mode.get() == Mode.LUNGE ? "Lunge " + tier().label() : "Blink " + tier().label();
    }

    public static boolean active() {
        Module module = ModuleRegistry.get(ID);
        return module != null && module.isEnabled();
    }

    public static int range() {
        Module module = ModuleRegistry.get(ID);
        return module instanceof SpearKillModule m ? m.maxRangeSetting.get() : 256;
    }

    public static String modeLabel() {
        Module module = ModuleRegistry.get(ID);
        return module instanceof SpearKillModule m
            ? (m.mode.get() == Mode.BLINK ? "Blink" : "Lunge")
            : "Lunge";
    }

    public static Tier tier() {
        Module module = ModuleRegistry.get(ID);
        return module instanceof SpearKillModule m
            ? (m.mode.get() == Mode.BLINK ? Tier.RISKY : Tier.BLATANT)
            : Tier.BLATANT;
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        flushPackets();
        reset();
    }

    @Override
    public void onGameLeft() {
        reset();
    }

    @Override
    public void tick() {
        if (MC.player == null || MC.level == null || MC.gameMode == null || MC.getConnection() == null) {
            reset();
            return;
        }
        if (MC.gui == null || MC.gui.screen() != null || !MC.player.isAlive()) {
            reset();
            return;
        }

        if (autoUse.get() && !MC.player.isUsingItem() && isHoldingSpear(MC.player.getMainHandItem())) {
            MC.gameMode.useItem(MC.player, InteractionHand.MAIN_HAND);
        }

        boolean charging = isUsingSpear();
        currentlyCharging = charging;

        if (mode.get() == Mode.LUNGE) {
            if (charging) {
                lunge();
            } else {
                killtarget = null;
                firstPhase = false;
                aboveTargetPos = null;
            }
            return;
        }

        // ---- Blink mode ----
        if (charging) {
            blinkChargeTicks++;
            if (killtarget == null || !killtarget.isAlive() || !canSeeTarget(killtarget)) {
                acquireTarget();
            }
        } else {
            blinkChargeTicks = 0;
            killtarget = null;
            lastTargetDistance = Double.MAX_VALUE;
            wasApproaching = false;
        }

        if (flushCooldown > 0) {
            flushCooldown--;
        }

        if (charging && !wasCharging) {
            startBlink();
        }

        if (!charging && wasCharging) {
            if (isBlinking) {
                if (killtarget != null) {
                    rotateToTarget(killtarget);
                }
                flushPackets();
                isBlinking = false;
                startPos = null;
            }
        }

        wasCharging = charging;

        if (isBlinking && killtarget != null && charging) {
            double currentDistance = MC.player.distanceTo(killtarget);
            boolean isApproaching = currentDistance < lastTargetDistance;
            boolean shouldFlush = false;

            if (currentDistance <= flushRange.get()) {
                shouldFlush = true;
            } else if (wasApproaching && !isApproaching && currentDistance < 8.0) {
                shouldFlush = true;
            } else if (!blinkLunge.get() && startPos != null
                && new Vec3(MC.player.getX(), MC.player.getY(), MC.player.getZ()).distanceTo(startPos) >= forceFlush.get()) {
                flushPackets();
                startBlink();
            }

            if (shouldFlush) {
                rotateToTarget(killtarget);
                flushPackets();
                if (blinkLunge.get()) {
                    flushCooldown = blinkLungeTicks.get();
                }
                isBlinking = true;
                startPos = new Vec3(MC.player.getX(), MC.player.getY(), MC.player.getZ());
                synchronized (packets) {
                    packets.clear();
                }
                lastTargetDistance = MC.player.distanceTo(killtarget);
                wasApproaching = false;
            } else {
                lastTargetDistance = currentDistance;
                wasApproaching = isApproaching;
            }
        }

        if (charging && killtarget != null && blinkAimbot.get()) {
            rotateToTarget(killtarget);
        }

        if (charging && blinkLunge.get() && killtarget != null && flushCooldown == 0
            && blinkChargeTicks >= blinkLungeTicks.get()) {
            rotateToTarget(killtarget);
            Vec3 viewDir = Vec3.directionFromRotation(MC.player.getXRot(), MC.player.getYRot());
            MC.player.setSprinting(true);
            MC.player.setDeltaMovement(viewDir.scale(blinkLungeStrength.get()));
        }
    }

    @Override
    public boolean onPacketSend(Packet<?> packet) {
        if (mode.get() != Mode.BLINK || !isBlinking || isFlushing) {
            return false;
        }
        if (!(packet instanceof ServerboundMovePlayerPacket p)) {
            return false;
        }
        synchronized (packets) {
            if (packets.isEmpty() || !isSamePacket(p, packets.get(packets.size() - 1))) {
                packets.add(p);
            }
        }
        return true;
    }

    @Override
    public boolean onPacketReceive(Packet<?> packet) {
        if (mode.get() == Mode.BLINK && isBlinking && packet instanceof ClientboundPlayerPositionPacket) {
            synchronized (packets) {
                packets.clear();
            }
            startPos = new Vec3(MC.player.getX(), MC.player.getY(), MC.player.getZ());
            lastTargetDistance = killtarget != null ? MC.player.distanceTo(killtarget) : Double.MAX_VALUE;
            wasApproaching = false;
        }
        return false;
    }

    private void lunge() {
        if (killtarget == null || !killtarget.isAlive()) {
            acquireTarget();
        }
        if (killtarget == null || !(killtarget instanceof LivingEntity)) {
            return;
        }
        if (!isValidTarget(killtarget)) {
            return;
        }

        int readyTicks = MC.player.getUsedItemHand() == InteractionHand.MAIN_HAND
            ? getReadyTicks(MC.player.getMainHandItem().getItem())
            : getReadyTicks(MC.player.getOffhandItem().getItem());

        rotateToTarget(killtarget);

        if (MC.player.getTicksUsingItem() <= readyTicks) {
            return;
        }

        AABB playerBox = MC.player.getBoundingBox().inflate(stopDistance.get());
        if (playerBox.intersects(killtarget.getBoundingBox())) {
            if (stopOnTarget.get()) {
                killtarget = null;
                MC.player.setDeltaMovement(0, 0, 0);
                MC.player.setSprinting(false);
            }
            firstPhase = false;
            aboveTargetPos = null;
            return;
        }

        double lungeSpeed = lungeStrength.get();
        Vec3 playerPos = MC.player.position();
        Vec3 viewDir;
        Vec3 targetCenter = killtarget.getBoundingBox().getCenter();

        switch (lungeDirection.get()) {
            case DIRECTION_BASED -> {
                viewDir = targetCenter.subtract(playerPos).normalize();
                MC.player.setSprinting(true);
                MC.player.setDeltaMovement(viewDir.scale(lungeSpeed));
            }
            case FROM_ABOVE -> {
                if (!firstPhase || aboveTargetPos == null) {
                    aboveTargetPos = new Vec3(targetCenter.x, targetCenter.y + aboveHeight.get(), targetCenter.z);
                    firstPhase = true;
                }
                if (playerPos.distanceTo(aboveTargetPos) < aboveTrigger.get()) {
                    viewDir = targetCenter.subtract(playerPos).normalize();
                    firstPhase = false;
                } else {
                    viewDir = aboveTargetPos.subtract(playerPos).normalize();
                }
                MC.player.setSprinting(true);
                MC.player.setDeltaMovement(viewDir.scale(lungeSpeed));
            }
            case AUTO_FROM_ABOVE_FIRST -> {
                if (!firstPhase || aboveTargetPos == null) {
                    aboveTargetPos = new Vec3(targetCenter.x, targetCenter.y + aboveHeight.get(), targetCenter.z);
                    firstPhase = true;
                }
                boolean pathValid = isFromAbovePathValid(aboveTargetPos, killtarget);
                if (!pathValid || playerPos.distanceTo(aboveTargetPos) < aboveTrigger.get()) {
                    viewDir = targetCenter.subtract(playerPos).normalize();
                    firstPhase = false;
                } else {
                    viewDir = aboveTargetPos.subtract(playerPos).normalize();
                }
                MC.player.setSprinting(true);
                MC.player.setDeltaMovement(viewDir.scale(lungeSpeed));
            }
        }
    }

    private void acquireTarget() {
        killtarget = target();
        firstPhase = false;
        aboveTargetPos = null;
    }

    private boolean isFromAbovePathValid(Vec3 abovePos, Entity target) {
        if (MC.level == null || abovePos == null) {
            return false;
        }
        Vec3 targetCenter = target.getBoundingBox().getCenter();
        if (invalid(abovePos)) {
            return false;
        }
        if (validateAbove.get()) {
            double checkDistance = aboveTrigger.get();
            int radius = (int) checkDistance;
            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        Vec3 testPos = abovePos.add(x, y, z);
                        if (testPos.distanceTo(abovePos) <= checkDistance && invalid(testPos)) {
                            return false;
                        }
                    }
                }
            }
        }
        int pathSteps = Math.max(10, (int) (abovePos.distanceTo(targetCenter) * 2.5));
        for (int i = 1; i < pathSteps; i++) {
            double t = i / (double) pathSteps;
            if (invalid(abovePos.lerp(targetCenter, t))) {
                return false;
            }
        }
        return true;
    }

    private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

    private boolean invalid(Vec3 pos) {
        if (MC.level == null) {
            return true;
        }
        double clampedY = Mth.clamp(pos.y, MC.level.getMinY(), MC.level.getMaxY() - 1);
        if (clampedY != pos.y) {
            return true;
        }
        BlockPos floored = BlockPos.containing(pos);
        int chunkX = floored.getX() >> 4;
        int chunkZ = floored.getZ() >> 4;
        if (MC.level.getChunkSource().getChunkNow(chunkX, chunkZ) == null) {
            return true;
        }
        if (positionCache.containsKey(pos)) {
            return positionCache.get(pos);
        }
        Entity entity = MC.player;
        Vec3 delta = pos.subtract(entity.position());
        AABB box = entity.getBoundingBox().move(delta);

        mutablePos.set(floored);
        for (int x = -1; x <= 1; x++) {
            mutablePos.setX(floored.getX() + x);
            for (int y = -1; y <= 1; y++) {
                mutablePos.setY(floored.getY() + y);
                for (int z = -1; z <= 1; z++) {
                    mutablePos.setZ(floored.getZ() + z);
                    BlockState state = MC.level.getBlockState(mutablePos);
                    if (state.is(Blocks.LAVA) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                        || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CAMPFIRE)
                        || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.POWDER_SNOW)) {
                        positionCache.put(pos, true);
                        return true;
                    }
                }
            }
        }

        for (Entity e : MC.level.getEntities(entity, box)) {
            if (e.canBeCollidedWith(entity)) {
                positionCache.put(pos, true);
                return true;
            }
        }

        boolean collides = MC.level.getBlockCollisions(entity, box).iterator().hasNext();
        positionCache.put(pos, collides);
        return collides;
    }

    private void rotateToTarget(Entity target) {
        if (MC.player == null || target == null) {
            return;
        }
        Vec3 playerPos = MC.player.getEyePosition();
        AABB box = target.getBoundingBox();

        double targetCenterY = box.getCenter().y;
        double heightDiff = targetCenterY - playerPos.y;

        double targetY;
        double boxHeight = box.maxY - box.minY;

        if (Math.abs(heightDiff) < 1.0) {
            targetY = targetCenterY;
        } else if (heightDiff > 0) {
            double offset = Math.min(heightDiff / 5.0, 0.4);
            targetY = targetCenterY - (boxHeight * offset);
        } else {
            double offset = Math.min(-heightDiff / 5.0, 0.4);
            targetY = targetCenterY + (boxHeight * offset);
        }

        Vec3 targetPos = new Vec3(box.getCenter().x, targetY, box.getCenter().z);
        Vec3 toTarget = targetPos.subtract(playerPos).normalize();
        float yaw = (float) (Math.toDegrees(Math.atan2(toTarget.z, toTarget.x)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.asin(toTarget.y));
        MC.player.setYRot(yaw);
        MC.player.setYHeadRot(yaw);
        MC.player.setXRot(pitch);
    }

    private int getReadyTicks(Item item) {
        String name = item.toString().toLowerCase();
        int value = 14;
        if (name.contains("wooden")) {
            value = 14;
        } else if (name.contains("stone") || name.contains("golden")) {
            value = 13;
        } else if (name.contains("copper")) {
            value = 12;
        } else if (name.contains("iron")) {
            value = 11;
        } else if (name.contains("diamond")) {
            value = 9;
        } else if (name.contains("netherite")) {
            value = 7;
        }
        return Math.round(value * (delayModifier.get() / 100.0f));
    }

    private void startBlink() {
        isBlinking = true;
        startPos = new Vec3(MC.player.getX(), MC.player.getY(), MC.player.getZ());
        synchronized (packets) {
            packets.clear();
        }
        lastTargetDistance = killtarget != null ? MC.player.distanceTo(killtarget) : Double.MAX_VALUE;
        wasApproaching = false;
    }

    private boolean isSamePacket(ServerboundMovePlayerPacket a, ServerboundMovePlayerPacket b) {
        return a.isOnGround() == b.isOnGround()
            && a.getYRot(-1) == b.getYRot(-1)
            && a.getXRot(-1) == b.getXRot(-1)
            && a.getX(-1) == b.getX(-1)
            && a.getY(-1) == b.getY(-1)
            && a.getZ(-1) == b.getZ(-1);
    }

    private void flushPackets() {
        if (MC.player == null || MC.getConnection() == null) {
            return;
        }
        synchronized (packets) {
            if (packets.isEmpty()) {
                return;
            }
            isFlushing = true;
            Vec3 currentPos = new Vec3(MC.player.getX(), MC.player.getY(), MC.player.getZ());
            double distance = startPos != null ? startPos.distanceTo(currentPos) : 0;
            if (distance < flushRange.get()) {
                packets.clear();
                isFlushing = false;
                return;
            }

            Vec3 sendStartPos = startPos;
            double boost = distanceBoost.get();
            if (boost > 0 && startPos != null) {
                Vec3 direction = currentPos.subtract(startPos);
                Vec3 horizontalDir = new Vec3(direction.x, 0, direction.z).normalize();
                if (horizontalDir.length() > 0.01) {
                    Vec3 targetPos = startPos.subtract(horizontalDir.scale(boost));
                    HitResult hit = MC.level.clip(new ClipContext(
                        startPos, targetPos,
                        ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE,
                        MC.player
                    ));
                    if (hit.getType() == HitResult.Type.MISS) {
                        sendStartPos = targetPos;
                    } else {
                        sendStartPos = hit.getLocation().add(horizontalDir.scale(0.5));
                    }
                }
            }

            if (sendStartPos != null) {
                ServerboundMovePlayerPacket startPacket = new ServerboundMovePlayerPacket.PosRot(
                    sendStartPos.x, sendStartPos.y, sendStartPos.z,
                    MC.player.getYRot(), MC.player.getXRot(), false, false
                );
                MC.getConnection().send(startPacket);
            }
            ServerboundMovePlayerPacket endPacket = new ServerboundMovePlayerPacket.PosRot(
                currentPos.x, currentPos.y, currentPos.z,
                MC.player.getYRot(), MC.player.getXRot(),
                MC.player.onGround(), MC.player.horizontalCollision
            );
            MC.getConnection().send(endPacket);
            packets.clear();
            isFlushing = false;
        }
    }

    private Entity target() {
        if (MC.player == null || MC.level == null) {
            return null;
        }
        if (MC.hitResult instanceof EntityHitResult hit && isValidTarget(hit.getEntity())) {
            return hit.getEntity();
        }
        double maxRange = maxRangeSetting.get();
        Vec3 eyePos = MC.player.getEyePosition();
        Vec3 lookVec = MC.player.getViewVector(1.0f);
        HitResult blockHit = MC.level.clip(new ClipContext(eyePos,
            eyePos.add(lookVec.scale(maxRange)), ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE, MC.player));
        double rayLength = blockHit.getType() == HitResult.Type.MISS
            ? maxRange
            : eyePos.distanceTo(blockHit.getLocation());
        List<Entity> candidates = MC.level.getEntities(MC.player,
            MC.player.getBoundingBox().expandTowards(lookVec.scale(rayLength)),
            e -> e instanceof LivingEntity && e.isAlive() && e != MC.player);
        candidates.sort(Comparator.comparingDouble(e -> eyePos.distanceToSqr(e.getBoundingBox().getCenter())));
        double cone = Math.cos(Math.toRadians(fov.get() / 2.0));
        for (Entity e : candidates) {
            double dist = eyePos.distanceTo(e.getBoundingBox().getCenter());
            if (dist > maxRange) {
                break;
            }
            if (!isValidTarget(e)) {
                continue;
            }
            if (!canSeeTarget(e)) {
                continue;
            }
            Vec3 toEntity = e.getBoundingBox().getCenter().subtract(eyePos).normalize();
            if (lookVec.dot(toEntity) > cone) {
                return e;
            }
        }
        return null;
    }

    private boolean canSeeTarget(Entity target) {
        if (MC.player == null || MC.level == null) {
            return false;
        }
        Vec3 eyePos = MC.player.getEyePosition();
        Vec3 targetCenter = target.getBoundingBox().getCenter();
        HitResult result = MC.level.clip(new ClipContext(
            eyePos, targetCenter,
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE, MC.player
        ));
        if (result.getType() == HitResult.Type.MISS) {
            return true;
        }
        return eyePos.distanceTo(result.getLocation()) >= eyePos.distanceTo(targetCenter) - 0.5;
    }

    private boolean isValidTarget(Entity entity) {
        if (entity == null || entity == MC.player || !entity.isAlive() || entity.isSpectator()) {
            return false;
        }
        return entity instanceof Player ? players.get() : entity instanceof LivingEntity && mobs.get();
    }

    private boolean isUsingSpear() {
        if (MC.player == null) {
            return false;
        }
        ItemStack use = MC.player.getUseItem();
        if (use == null || use.isEmpty()) {
            return false;
        }
        return isSpearItem(use.getItem());
    }

    private boolean isHoldingSpear(ItemStack stack) {
        return stack != null && !stack.isEmpty() && isSpearItem(stack.getItem());
    }

    private boolean isSpearItem(Item item) {
        if (item == null) {
            return false;
        }
        String name = item.toString().toLowerCase()
            + " " + item.getDescriptionId().toLowerCase()
            + " " + BuiltInRegistries.ITEM.getKey(item).getPath().toLowerCase();
        return name.contains("spear");
    }

    private void reset() {
        synchronized (packets) {
            packets.clear();
        }
        isBlinking = false;
        isFlushing = false;
        startPos = null;
        wasCharging = false;
        currentlyCharging = false;
        lastTargetDistance = Double.MAX_VALUE;
        wasApproaching = false;
        killtarget = null;
        blinkChargeTicks = 0;
        flushCooldown = 0;
        firstPhase = false;
        aboveTargetPos = null;
        positionCache.clear();
    }
}
