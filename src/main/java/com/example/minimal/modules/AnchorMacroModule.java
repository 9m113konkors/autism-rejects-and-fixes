package com.example.minimal.modules;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.IntSetting;
import autismclient.modules.Module;
import autismclient.modules.ModuleRegistry;
import autismclient.util.AutismInventoryHelper;
import autismclient.util.AutismRotationUtil;
import com.example.minimal.Tier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// Hostile anchor macro: hold your bind to place a respawn anchor on the block under your crosshair,
// charge it with glowstone, then break it so it explodes, on a loop. Like the crystal macro this is
// deliberately not "legit-looking" — it is machine-speed hostile block work.
public final class AnchorMacroModule extends Module {

    public static final String ID = "autism-minimal-addon-template:anchor-macro";

    private enum Phase {
        PLACE,
        CHARGE,
        WAIT,
        DETONATE,
        WAIT_CYCLE
    }

    private final IntSetting detonateDelay = add(new IntSetting("detonate-delay", "Detonate delay (ticks)", 4, 0, 20, 1)
        .group("Timing")
        .description("Ticks between placing/charging the anchor and breaking it, so the server registers the state change first."));
    private final IntSetting cycleDelay = add(new IntSetting("cycle-delay", "Cycle delay (ticks)", 4, 0, 40, 1)
        .group("Timing")
        .description("Ticks between detonations. Lower = faster spam."));
    private final BoolSetting autoSelect = add(new BoolSetting("auto-select", "Auto-select anchor", true)
        .group("Item")
        .description("Find a respawn anchor in your hotbar and select it before placing. Off = place whatever your selected slot holds."));
    private final BoolSetting autoCharge = add(new BoolSetting("auto-charge", "Charge with glowstone", true)
        .group("Item")
        .description("Auto-select glowstone and right-click the anchor to charge it before detonating. Off = only detonate already-charged anchors."));
    private final BoolSetting autoSword = add(new BoolSetting("auto-sword", "Switch back to sword", false)
        .group("Item")
        .description("Select a sword in your hotbar after every detonation so you are ready to melee."));
    private final IntSetting range = add(new IntSetting("range", "Range (blocks)", 4, 3, 6, 1)
        .group("Placement")
        .description("Placement distance limit from your position."));

    private Phase phase = Phase.PLACE;
    private int phaseTicks;
    private BlockPos lastTarget;

    public AnchorMacroModule() {
        super(ID, "Anchor Macro", "Hold your bind to place a respawn anchor on the block under your crosshair, charge it with glowstone, then break it to detonate, on a loop.");
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

        BlockPos pos = resolveTarget();
        if (pos == null) {
            reset();
            return;
        }
        if (!pos.equals(lastTarget)) {
            lastTarget = pos.immutable();
            phase = Phase.PLACE;
            phaseTicks = 0;
        }

        BlockState state = MC.level.getBlockState(pos);
        boolean anchored = state.getBlock() instanceof RespawnAnchorBlock;
        int charge = anchored ? state.getValue(RespawnAnchorBlock.CHARGE) : 0;

        switch (phase) {
            case PLACE -> {
                if (anchored) {
                    phase = charge >= 1 ? Phase.DETONATE : Phase.CHARGE;
                    break;
                }
                if (!state.isAir() || MC.player.distanceToSqr(Vec3.atCenterOf(pos)) > range.get() * (double) range.get()) {
                    break;
                }
                if (autoSelect.get() && !selectItem(Items.RESPAWN_ANCHOR)) {
                    break;
                }
                aimAt(Vec3.atCenterOf(pos));
                BlockHitResult blockHit = (BlockHitResult) MC.hitResult;
                if (blockHit == null) {
                    break;
                }
                place(pos, blockHit);
                phase = Phase.WAIT;
                phaseTicks = Math.max(0, detonateDelay.get());
            }
            case CHARGE -> {
                if (!anchored || charge >= 1) {
                    phase = Phase.PLACE;
                    break;
                }
                if (!autoCharge.get()) {
                    phase = Phase.PLACE;
                    break;
                }
                if (!selectItem(Items.GLOWSTONE)) {
                    break;
                }
                aimAt(Vec3.atCenterOf(pos));
                useOn(pos, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
                phase = Phase.WAIT;
                phaseTicks = Math.max(0, detonateDelay.get());
            }
            case WAIT -> {
                if (--phaseTicks <= 0) {
                    BlockState fresh = MC.level.getBlockState(pos);
                    boolean isAnchor = fresh.getBlock() instanceof RespawnAnchorBlock;
                    phase = isAnchor && fresh.getValue(RespawnAnchorBlock.CHARGE) >= 1 ? Phase.DETONATE : Phase.PLACE;
                }
            }
            case DETONATE -> {
                if (!anchored || charge < 1) {
                    phase = Phase.PLACE;
                    break;
                }
                aimAt(Vec3.atCenterOf(pos));
                MC.gameMode.destroyBlock(pos);
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

    private BlockPos resolveTarget() {
        HitResult hit = MC.hitResult;
        if (!(hit instanceof BlockHitResult blockHit)) {
            return null;
        }
        BlockPos hitBlock = blockHit.getBlockPos();
        BlockState hitState = MC.level.getBlockState(hitBlock);
        if (hitState.getBlock() instanceof RespawnAnchorBlock) {
            return hitBlock.immutable();
        }
        return hitBlock.relative(blockHit.getDirection()).immutable();
    }

    private void place(BlockPos pos, BlockHitResult blockHit) {
        InteractionResult result = MC.gameMode.useItemOn(MC.player, InteractionHand.MAIN_HAND, blockHit);
        if (result.consumesAction()) {
            MC.player.swing(InteractionHand.MAIN_HAND);
        }
    }

    private void useOn(BlockPos pos, BlockHitResult anchorHit) {
        InteractionResult result = MC.gameMode.useItemOn(MC.player, InteractionHand.MAIN_HAND, anchorHit);
        if (result.consumesAction()) {
            MC.player.swing(InteractionHand.MAIN_HAND);
        }
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
