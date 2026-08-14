package com.konkors.autismpvp.modules;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.IntSetting;
import autismclient.modules.Module;
import autismclient.modules.ModuleRegistry;
import autismclient.util.AutismInventoryHelper;
import autismclient.util.AutismRotationUtil;
import com.konkors.autismpvp.Tier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// Hostile crystal macro (the addon's second explicit non-legit module): bind a key and HOLD it —
// while held, it places an end crystal on the block under your crosshair, waits a short detonate
// delay, then attacks the crystal to blow it up, on a steady cycle. Also auto-selects the crystal
// from your hotbar (and optionally switches back to a sword after every detonation).
public final class CrystalMacroModule extends Module {

    public static final String ID = "autismpvp:crystal-macro";

    private enum Phase {
        PLACE,
        WAIT_BREAK,
        DETONATE,
        WAIT_CYCLE
    }

    private final IntSetting breakDelay = add(new IntSetting("detonate-delay", "Detonate delay (ticks)", 6, 0, 20, 1)
        .group("Timing")
        .description("Ticks between placing the crystal and attacking it. Needs to be a couple of ticks so the server registers the placement."));
    private final IntSetting cycleDelay = add(new IntSetting("cycle-delay", "Cycle delay (ticks)", 4, 0, 40, 1)
        .group("Timing")
        .description("Ticks between detonations. Lower = faster spam."));
    private final BoolSetting autoSelect = add(new BoolSetting("auto-select", "Auto-select crystal", true)
        .group("Item")
        .description("Find an end crystal in your hotbar and select it before placing. Off = place whatever your selected slot holds."));
    private final BoolSetting autoSword = add(new BoolSetting("auto-sword", "Switch back to sword", false)
        .group("Item")
        .description("Select a sword in your hotbar after every detonation so you are ready to melee."));
    private final IntSetting range = add(new IntSetting("range", "Range (blocks)", 4, 3, 6, 1)
        .group("Placement")
        .description("Placement distance limit from your position."));

    private Phase phase = Phase.PLACE;
    private int phaseTicks;
    private BlockPos lastTarget;

    public CrystalMacroModule() {
        super(ID, "Crystal Macro", "Hold your bind to place an end crystal on the block under your crosshair and blow it up, on a loop.");
    }

    @Override
    public boolean holdToActivate() {
        return true;
    }

    @Override
    public String info() {
        return "Hold " + tier().label();
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
        if (MC.player.isSpectator()) {
            reset();
            return;
        }
        if (MC.gui != null && MC.gui.screen() != null) {
            return;
        }

        Resolved target = resolveTarget();
        if (target == null) {
            reset();
            return;
        }
        if (!target.pos.equals(lastTarget)) {
            lastTarget = target.pos.immutable();
            phase = Phase.PLACE;
            phaseTicks = 0;
        }

        switch (phase) {
            case PLACE -> {
                if (target.crystal != null) {
                    phase = Phase.WAIT_BREAK;
                    phaseTicks = Math.max(0, breakDelay.get());
                    break;
                }
                if (MC.player.distanceToSqr(Vec3.atCenterOf(target.pos)) > range.get() * (double) range.get()) {
                    break;
                }
                if (placeCrystal(target)) {
                    phase = Phase.WAIT_BREAK;
                    phaseTicks = Math.max(0, breakDelay.get());
                }
            }
            case WAIT_BREAK -> {
                if (--phaseTicks <= 0) {
                    phase = Phase.DETONATE;
                }
            }
            case DETONATE -> {
                EndCrystal crystal = findCrystal(target.pos);
                if (crystal == null) {
                    phase = Phase.PLACE;
                    break;
                }
                aimAt(crystal.position());
                MC.gameMode.attack(MC.player, crystal);
                if (autoSword.get()) {
                    selectSword();
                }
                phase = Phase.WAIT_CYCLE;
                phaseTicks = Math.max(0, cycleDelay.get());
            }
            case WAIT_CYCLE -> {
                if (--phaseTicks <= 0) {
                    phase = Phase.PLACE;
                }
            }
        }
    }

    private record Resolved(BlockPos pos, EndCrystal crystal, BlockHitResult blockHit) {}

    private Resolved resolveTarget() {
        HitResult hit = MC.hitResult;
        if (hit == null) {
            return null;
        }
        if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof EndCrystal crystal) {
            return new Resolved(crystal.blockPosition().immutable(), crystal, null);
        }
        if (hit instanceof BlockHitResult blockHit) {
            BlockPos target = blockHit.getBlockPos().relative(blockHit.getDirection());
            return new Resolved(target.immutable(), findCrystal(target), blockHit);
        }
        return null;
    }

    private boolean placeCrystal(Resolved target) {
        BlockHitResult blockHit = target.blockHit;
        if (blockHit == null) {
            return false;
        }
        if (autoSelect.get() && !selectItem(Items.END_CRYSTAL)) {
            return false;
        }
        aimAt(Vec3.atCenterOf(target.pos));
        InteractionResult result = MC.gameMode.useItemOn(MC.player, InteractionHand.MAIN_HAND, blockHit);
        if (result.consumesAction()) {
            MC.player.swing(InteractionHand.MAIN_HAND);
        }
        return result.consumesAction();
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
        AutismRotationUtil.apply(MC.player, AutismRotationUtil.lookingAt(point, MC.player.getEyePosition()), true);
    }

    private boolean selectItem(net.minecraft.world.item.Item item) {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = MC.player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.getItem() == item) {
                AutismInventoryHelper.selectHotbarSlot(MC, slot);
                return true;
            }
        }
        // Inventory fallback
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

    private void reset() {
        phase = Phase.PLACE;
        phaseTicks = 0;
        lastTarget = null;
    }
}
