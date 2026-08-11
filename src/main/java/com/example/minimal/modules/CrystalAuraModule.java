package com.example.minimal.modules;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.DoubleSetting;
import autismclient.api.module.IntSetting;
import autismclient.modules.KillAuraModule;
import autismclient.modules.Module;
import autismclient.modules.ModuleRegistry;
import autismclient.util.AutismInventoryHelper;
import autismclient.util.AutismRotationUtil;
import com.example.minimal.Tier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Random;

// Auto Crystal Aura: finds the nearest player/mob, picks a solid block beside them that can hold a
// crystal, places one there, waits a short detonate delay, then attacks the crystal to blow it up,
// on a steady cycle with humanized jitter. Hostile like the crystal macro, but fully automatic.
public final class CrystalAuraModule extends Module {

    public static final String ID = "autism-minimal-addon-template:crystal-aura";

    private final IntSetting range = add(new IntSetting("range", "Target range (blocks)", 6, 3, 12, 1)
        .group("Targeting")
        .description("Max distance to search for a target."));
    private final BoolSetting players = add(new BoolSetting("players", "Players", true)
        .group("Targeting")
        .description("Target players."));
    private final BoolSetting mobs = add(new BoolSetting("mobs", "Mobs", false)
        .group("Targeting")
        .description("Target non-player living mobs."));
    private final DoubleSetting minSelfDist = add(new DoubleSetting("self-distance", "Min self distance", 2.0D, 0.5D, 6.0D, 0.25D)
        .group("Placement")
        .description("How far from you a crystal spot must be, so you do not blow yourself up with every hit."));
    private final IntSetting detonateDelay = add(new IntSetting("detonate-delay", "Detonate delay (ticks)", 2, 0, 10, 1)
        .group("Timing")
        .description("Ticks between placing a crystal and attacking it."));
    private final IntSetting placeDelay = add(new IntSetting("place-delay", "Cycle delay (ticks)", 3, 0, 30, 1)
        .group("Timing")
        .description("Ticks between detonations. Lower = faster spam, more obvious."));
    private final IntSetting jitter = add(new IntSetting("jitter", "Jitter (ticks)", 1, 0, 6, 1)
        .group("Timing")
        .description("Random extra ticks added to each cycle so the spam is never perfectly periodic."));
    private final BoolSetting smoothAim = add(new BoolSetting("smooth-aim", "Smooth aim", true)
        .group("Aim")
        .description("Glide toward each crystal instead of snapping instantly."));
    private final BoolSetting autoSelect = add(new BoolSetting("auto-select", "Auto-select crystal", true)
        .group("Item")
        .description("Find an end crystal in the hotbar and select it before placing."));
    private final BoolSetting autoSword = add(new BoolSetting("auto-sword", "Switch back to sword", true)
        .group("Item")
        .description("Select a sword in the hotbar after every detonation so you are ready to melee."));

    private final Random random = new Random();
    private int cooldown;
    private BlockPos currentSpot;

    public CrystalAuraModule() {
        super(ID, "Crystal Aura", "Auto-places and detonates end crystals beside the nearest enemy. Hostile module, like the crystal macro.");
    }

    @Override
    public String info() {
        return "r" + range.get() + " " + tier().label();
    }

    public static Tier tier() {
        return Tier.CLOSET; // Ghost module - misleading tier
    }

    public static boolean active() {
        Module module = ModuleRegistry.get(ID);
        return module != null && module.isEnabled();
    }

    @Override
    public void onDisable() {
        cooldown = 0;
        currentSpot = null;
    }

    @Override
    public void onGameLeft() {
        onDisable();
    }

    @Override
    public void tick() {
        if (MC.player == null || MC.level == null || MC.gameMode == null || MC.getConnection() == null) {
            onDisable();
            return;
        }
        if (MC.player.isSpectator()) {
            onDisable();
            return;
        }
        if (MC.gui != null && MC.gui.screen() != null) {
            return;
        }

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        // Anti-surround: break any crystals that would block our placement spot
        if (currentSpot != null) {
            EndCrystal blocking = findCrystal(currentSpot);
            if (blocking != null) {
                // Clear crystals from our own placement spot so we can replace them
                aimAt(blocking.position());
                MC.gameMode.attack(MC.player, blocking);
                if (autoSword.get()) {
                    selectSword();
                }
                cooldown = Math.max(0, placeDelay.get()) + jitter();
                return;
            }
        }

        LivingEntity target = findTarget();
        if (target == null) {
            currentSpot = null;
            return;
        }

        BlockPos spot = currentSpot != null && validSpot(currentSpot) ? currentSpot : findSpot(target);
        currentSpot = spot;
        if (spot == null) {
            return;
        }

        EndCrystal crystal = findCrystal(spot);
        if (crystal == null) {
            placeAt(spot);
            cooldown = Math.max(0, detonateDelay.get());
        } else {
            detonate(crystal);
            cooldown = Math.max(0, placeDelay.get()) + jitter();
        }
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

    private BlockPos findSpot(LivingEntity target) {
        BlockPos feet = target.blockPosition();
        BlockPos best = null;
        double bestSelfDist = -1.0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                BlockPos crystalPos = new BlockPos(feet.getX() + dx, feet.getY(), feet.getZ() + dz);
                if (!validSpot(crystalPos)) {
                    continue;
                }
                double selfDist = MC.player.distanceToSqr(Vec3.atCenterOf(crystalPos));
                if (selfDist < minSelfDist.get() * minSelfDist.get()) {
                    continue;
                }
                if (selfDist > bestSelfDist) {
                    bestSelfDist = selfDist;
                    best = crystalPos;
                }
            }
        }
        return best;
    }

    private boolean validSpot(BlockPos crystalPos) {
        BlockPos surface = crystalPos.below();
        BlockState surfaceState = MC.level.getBlockState(surface);
        if (surfaceState.isAir() || surfaceState.canBeReplaced()) {
            return false;
        }
        BlockState spotState = MC.level.getBlockState(crystalPos);
        if (!spotState.isAir() && !spotState.canBeReplaced()) {
            return false;
        }
        return MC.player.distanceToSqr(Vec3.atCenterOf(crystalPos))
            <= MC.player.blockInteractionRange() * MC.player.blockInteractionRange();
    }

    private void placeAt(BlockPos spot) {
        BlockPos surface = spot.below();
        if (autoSelect.get() && !selectItem(Items.END_CRYSTAL)) {
            return;
        }
        aimAt(Vec3.atCenterOf(spot));
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(surface), Direction.UP, surface, false);
        InteractionResult result = MC.gameMode.useItemOn(MC.player, InteractionHand.MAIN_HAND, hit);
        if (result.consumesAction()) {
            MC.player.swing(InteractionHand.MAIN_HAND);
        }
    }

    private void detonate(EndCrystal crystal) {
        aimAt(crystal.position());
        MC.gameMode.attack(MC.player, crystal);
        if (autoSword.get()) {
            selectSword();
        }
    }

    private EndCrystal findCrystal(BlockPos pos) {
        AABB box = new AABB(pos).inflate(0.75);
        Vec3 center = Vec3.atCenterOf(pos);
        for (EndCrystal crystal : MC.level.getEntitiesOfClass(EndCrystal.class, box,
            entity -> entity.distanceToSqr(center) < 4.0)) {
            return crystal;
        }
        return null;
    }

    private void aimAt(Vec3 point) {
        AutismRotationUtil.Rotation current = AutismRotationUtil.playerRotation(MC.player);
        AutismRotationUtil.Rotation target = AutismRotationUtil.lookingAt(point, MC.player.getEyePosition());
        if (smoothAim.get()) {
            target = AutismRotationUtil.towardsLinear(current, target, 0.45f, 0.45f);
        }
        AutismRotationUtil.apply(MC.player, target, true);
    }

    private boolean selectItem(net.minecraft.world.item.Item item) {
        // First try hotbar
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = MC.player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.getItem() == item) {
                AutismInventoryHelper.selectHotbarSlot(MC, slot);
                return true;
            }
        }
        // Then try inventory (swap into empty hotbar slot)
        int invSlot = -1;
        for (int slot = 9; slot < 36; slot++) {
            ItemStack stack = MC.player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.getItem() == item) {
                invSlot = slot;
                break;
            }
        }
        int emptyHotbar = -1;
        for (int slot = 0; slot < 9; slot++) {
            if (MC.player.getInventory().getItem(slot).isEmpty()) {
                emptyHotbar = slot;
                break;
            }
        }
        if (invSlot < 0 || emptyHotbar < 0) return false;
        if (!AutismInventoryHelper.swapInventorySlots(MC, invSlot, emptyHotbar)) return false;
        AutismInventoryHelper.selectHotbarSlot(MC, emptyHotbar);
        return true;
    }

    private void selectSword() {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = MC.player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.is(ItemTags.SWORDS)) {
                AutismInventoryHelper.selectHotbarSlot(MC, slot);
                return;
            }
        }
    }

    private int jitter() {
        return jitter.get() <= 0 ? 0 : random.nextInt(jitter.get() + 1);
    }
}
