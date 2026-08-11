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
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

// CrystalWarp: the self-launch escape. Bind a key and hold it — it places an end crystal at your
// own feet (on the block you are standing on), optionally jumps, then detonates it so the explosion
// rockets you upward/away to escape pressure. Loops while held. Hostile, like the crystal macro.
public final class CrystalWarpModule extends Module {

    public static final String ID = "autism-minimal-addon-template:crystal-warp";

    private final IntSetting detonateDelay = add(new IntSetting("detonate-delay", "Detonate delay (ticks)", 1, 0, 10, 1)
        .group("Timing")
        .description("Ticks between placing the crystal at your feet and attacking it."));
    private final IntSetting cycleDelay = add(new IntSetting("cycle-delay", "Cycle delay (ticks)", 6, 0, 40, 1)
        .group("Timing")
        .description("Ticks between detonations while the bind is held. Lower = more launches."));
    private final BoolSetting jump = add(new BoolSetting("jump", "Jump before detonate", true)
        .group("Launch")
        .description("Jump right before detonating so the explosion launches you higher."));
    private final BoolSetting autoSelect = add(new BoolSetting("auto-select", "Auto-select crystal", true)
        .group("Item")
        .description("Find an end crystal in your hotbar and select it before placing."));
    private final BoolSetting autoSword = add(new BoolSetting("auto-sword", "Switch back to sword", true)
        .group("Item")
        .description("Select a sword in the hotbar after every detonation."));

    private int cooldown;

    public CrystalWarpModule() {
        super(ID, "CrystalWarp", "Hold your bind to place a crystal at your feet and blow it up to launch yourself away from pressure.");
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
        cooldown = 0;
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

        BlockPos surface = MC.player.blockPosition().below();
        BlockPos spot = surface.above();

        EndCrystal crystal = findCrystal(spot);
        if (crystal == null) {
            if (autoSelect.get() && !selectItem(Items.END_CRYSTAL)) {
                return;
            }
            aimAt(Vec3.atCenterOf(spot));
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(surface), Direction.UP, surface, false);
            InteractionResult result = MC.gameMode.useItemOn(MC.player, InteractionHand.MAIN_HAND, hit);
            if (result.consumesAction()) {
                MC.player.swing(InteractionHand.MAIN_HAND);
            }
            cooldown = Math.max(0, detonateDelay.get());
            return;
        }

        if (jump.get() && MC.player.onGround()) {
            MC.player.jumpFromGround();
        }
        aimAt(crystal.position());
        MC.gameMode.attack(MC.player, crystal);
        if (autoSword.get()) {
            selectSword();
        }
        cooldown = Math.max(0, cycleDelay.get());
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
}
