package com.konkors.autismpvp.modules;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.DoubleSetting;
import autismclient.api.module.IntSetting;
import autismclient.modules.Module;
import autismclient.modules.ModuleRegistry;
import autismclient.util.AutismInventoryHelper;
import autismclient.util.AutismRotationUtil;
import com.konkors.autismpvp.Tier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// Litematica printer (meteor-litematica-printer style): reads the currently loaded Litematica
// schematic world and places every block that is missing around you. The client does not bundle
// Litematica, so the two entry points (SchematicWorldHandler.getSchematicWorld() and the schematic
// world's getBlockState(BlockPos)) are resolved with cached reflection — they both use the vanilla
// BlockPos/BlockState classes, so everything else is plain vanilla. Only works while Litematica is
// installed alongside the client and a schematic world is loaded.
public final class LitematicaPrinterModule extends Module {

    public static final String ID = "autismpvp:litematica-printer";

    private static Method schematicWorldMethod;
    private static Method getBlockStateMethod;
    private static Object lastSchematicWorld;

    private final IntSetting range = add(new IntSetting("range", "Range (blocks)", 4, 1, 6, 1)
        .group("Printer")
        .description("How far around you to scan for missing blocks. Placement is still capped by your real block-interaction reach."));
    private final IntSetting placeDelay = add(new IntSetting("place-delay", "Place delay (ticks)", 2, 0, 20, 1)
        .group("Printer")
        .description("Ticks between placement passes. Lower = faster printing."));
    private final IntSetting bpt = add(new IntSetting("blocks-per-tick", "Blocks per pass", 1, 1, 10, 1)
        .group("Printer")
        .description("How many blocks to place per pass."));
    private final BoolSetting rotate = add(new BoolSetting("rotate", "Rotate to block", true)
        .group("Printer")
        .description("Aim at each placement before clicking, so rotation matches the placement. Off = you look at the blocks yourself."));
    private final BoolSetting smoothLook = add(new BoolSetting("smooth-look", "Smooth look", true)
        .group("Printer")
        .description("Glide the aim toward each block and only click once it is aligned, instead of snapping instantly. Keeps the placement rotation matching the server's view (anticheat-safe)."));
    private final DoubleSetting smoothSpeed = add(new DoubleSetting("smooth-speed", "Smooth speed (deg/tick)", 2.0D, 0.5D, 8.0D, 0.25D)
        .group("Printer")
        .description("How fast the look turns toward the next block. Higher = quicker but more obviously automated."));
    private final DoubleSetting lookTolerance = add(new DoubleSetting("look-tolerance", "Look tolerance (deg)", 2.0D, 0.5D, 10.0D, 0.25D)
        .group("Printer")
        .description("How close the aim must be to the block before the placement click fires. Lower = more exact rotation, slower."));
    private final BoolSetting swing = add(new BoolSetting("swing", "Swing hand", true)
        .group("Printer")
        .description("Swing when placing. Off = packet-only swing."));
    private final BoolSetting airPlace = add(new BoolSetting("air-place", "Air place", true)
        .group("Printer")
        .description("Fall back to placing against an air/replaceable block when no solid neighbor is in reach."));
    private final BoolSetting searchInventory = add(new BoolSetting("search-inventory", "Search inventory", true)
        .group("Items")
        .description("Swap needed items from your main inventory into an empty hotbar slot (real container clicks) instead of only using the hotbar."));
    private final BoolSetting grassAsDirt = add(new BoolSetting("dirt-as-grass", "Dirt as grass", true)
        .group("Items")
        .description("Use a dirt item when the schematic wants a grass block and you have no grass."));

    private int timer;
    private AutismRotationUtil.Rotation pendingLook;

    public LitematicaPrinterModule() {
        super(ID, "Litematica Printer", "Places the missing blocks of the loaded Litematica schematic around you. Needs Litematica installed.");
    }

    @Override
    public String info() {
        return "r" + range.get() + " " + tier().label();
    }

    public static Tier tier() {
        Module module = ModuleRegistry.get(ID);
        if (module instanceof LitematicaPrinterModule printer) {
            boolean smooth = printer.smoothLook.get();
            if (printer.placeDelay.get() <= 1 && printer.bpt.get() >= 4) {
                return smooth ? Tier.RISKY : Tier.BLATANT;
            }
            return smooth ? Tier.LEGIT : Tier.RISKY;
        }
        return Tier.LEGIT;
    }

    public static boolean active() {
        Module module = ModuleRegistry.get(ID);
        return module != null && module.isEnabled();
    }

    public static boolean hasSchematic() {
        return getSchematicWorld() != null;
    }

    @Override
    public void onDisable() {
        timer = 0;
        pendingLook = null;
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

        Object schematicWorld = getSchematicWorld();
        if (schematicWorld == null) {
            timer = 0;
            pendingLook = null;
            return;
        }

        // Ease the aim toward the pending block every tick (even between passes) so the look
        // glides into position instead of snapping right before the click.
        if (rotate.get() && smoothLook.get() && pendingLook != null) {
            easeTowards(pendingLook);
        }

        if (timer < placeDelay.get()) {
            timer++;
            return;
        }
        timer = 0;

        int scan = Math.max(1, range.get());
        BlockPos center = MC.player.blockPosition();
        double scanSq = scan * scan;
        List<BlockPos> candidates = new ArrayList<>(Math.min(256, (scan * 2 + 1) * (scan * 2 + 1)));
        outer:
        for (int x = -scan; x <= scan; x++) {
            for (int y = -scan; y <= scan; y++) {
                double xySq = x * x + y * y;
                if (xySq > scanSq) continue;
                for (int z = -scan; z <= scan; z++) {
                    if (xySq + z * z > scanSq) continue;
                    BlockPos pos = new BlockPos(center.getX() + x, center.getY() + y, center.getZ() + z);
                    if (candidate(schematicWorld, pos)) {
                        candidates.add(pos.immutable());
                    }
                    if (candidates.size() >= 256) break outer; // safety cap
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(this::distanceTo));
        if (candidates.isEmpty()) {
            pendingLook = null;
            return;
        }

        double reach = Math.max(1.0, Math.min(MC.player.blockInteractionRange(), scan));
        double reachSq = reach * reach;
        int placed = 0;
        for (BlockPos pos : candidates) {
            if (placed >= Math.max(1, bpt.get())) {
                break;
            }
            BlockState required = schematicState(schematicWorld, pos);
            if (required == null) {
                continue;
            }
            Direction placeDir = placeSide(pos, required);
            if (placeDir == null) {
                if (!airPlace.get()) {
                    continue;
                }
                placeDir = Direction.UP;
            }
            Vec3 hitPos = Vec3.atCenterOf(pos)
                .add(placeDir.getStepX() * 0.5, placeDir.getStepY() * 0.5, placeDir.getStepZ() * 0.5);
            if (MC.player.distanceToSqr(hitPos) > reachSq) {
                continue;
            }
            Item item = required.getBlock().asItem();
            if (grassAsDirt.get() && item == Items.GRASS_BLOCK && !hasItem(Items.GRASS_BLOCK)) {
                item = Items.DIRT;
            }
            if (!selectItem(item)) {
                continue;
            }
            if (rotate.get()) {
                AutismRotationUtil.Rotation target = AutismRotationUtil.lookingAt(hitPos, MC.player.getEyePosition());
                if (smoothLook.get()) {
                    // Smooth look: remember the target, glide toward it on following ticks, and only
                    // click once the look is close enough that the placement rotation matches what
                    // the server/anticheat expects — never a snap-and-place.
                    pendingLook = target;
                    if (AutismRotationUtil.angleTo(AutismRotationUtil.playerRotation(MC.player), target) > lookTolerance.get()) {
                        break;
                    }
                    pendingLook = null;
                } else {
                    AutismRotationUtil.apply(MC.player, target, true);
                }
            }
            BlockPos neighbour = pos.relative(placeDir.getOpposite());
            BlockHitResult hit = new BlockHitResult(hitPos, placeDir, neighbour, false);
            InteractionResult result = MC.gameMode.useItemOn(MC.player, InteractionHand.MAIN_HAND, hit);
            if (result.consumesAction() && swing.get()) {
                MC.player.swing(InteractionHand.MAIN_HAND);
            }
            placed++;
        }
    }

    private boolean candidate(Object schematicWorld, BlockPos pos) {
        BlockState required = schematicState(schematicWorld, pos);
        if (required == null || required.isAir() || !required.getFluidState().isEmpty()) {
            return false;
        }
        BlockState current = MC.level.getBlockState(pos);
        if (current.getBlock() == required.getBlock()) {
            return false;
        }
        if (!current.canBeReplaced() || !current.getFluidState().isEmpty()) {
            return false;
        }
        if (MC.player.getBoundingBox().intersects(pos.getX(), pos.getY(), pos.getZ(),
            pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0)) {
            return false;
        }
        return required.canSurvive(MC.level, pos);
    }

    private double distanceTo(BlockPos pos) {
        return MC.player.distanceToSqr(Vec3.atCenterOf(pos));
    }

    private void easeTowards(AutismRotationUtil.Rotation target) {
        AutismRotationUtil.Rotation current = AutismRotationUtil.playerRotation(MC.player);
        float speed = (float) Math.max(0.5, smoothSpeed.get());
        AutismRotationUtil.apply(MC.player,
            AutismRotationUtil.towardsLinear(current, target, speed, speed), true);
    }

    // Returns the face of a reachable neighbor to click so the block lands at pos, or null.
    private Direction placeSide(BlockPos pos, BlockState required) {
        double reachSq = Math.max(1.0, Math.min(MC.player.blockInteractionRange(), range.get()));
        reachSq = reachSq * reachSq;
        for (Direction side : Direction.values()) {
            BlockPos neighbor = pos.relative(side);
            BlockState state = MC.level.getBlockState(neighbor);
            if (state.isAir() || state.canBeReplaced()) {
                continue;
            }
            if (!state.getFluidState().isEmpty()) {
                continue;
            }
            Vec3 faceCenter = Vec3.atCenterOf(pos)
                .add(side.getStepX() * 0.5, side.getStepY() * 0.5, side.getStepZ() * 0.5);
            if (MC.player.distanceToSqr(faceCenter) > reachSq) {
                continue;
            }
            return side.getOpposite();
        }
        return null;
    }

    private boolean hasItem(Item item) {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = MC.player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.getItem() == item) {
                return true;
            }
        }
        return false;
    }

    private boolean selectItem(Item item) {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = MC.player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.getItem() == item) {
                AutismInventoryHelper.selectHotbarSlot(MC, slot);
                return true;
            }
        }
        if (!searchInventory.get()) {
            return false;
        }
        int inventorySlot = -1;
        for (int slot = 9; slot < 36; slot++) {
            ItemStack stack = MC.player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.getItem() == item) {
                inventorySlot = slot;
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
        if (inventorySlot < 0 || emptyHotbar < 0) {
            return false;
        }
        if (!AutismInventoryHelper.swapInventorySlots(MC, inventorySlot, emptyHotbar)) {
            return false;
        }
        AutismInventoryHelper.selectHotbarSlot(MC, emptyHotbar);
        return true;
    }

    // ---- Litematica reflection ----

    private static Object getSchematicWorld() {
        try {
            if (schematicWorldMethod == null) {
                Class<?> handler = Class.forName("fi.dy.masa.litematica.world.SchematicWorldHandler");
                schematicWorldMethod = handler.getMethod("getSchematicWorld");
            }
            Object world = schematicWorldMethod.invoke(null);
            if (world == null) {
                lastSchematicWorld = null;
                return null;
            }
            if (getBlockStateMethod == null || lastSchematicWorld != world) {
                getBlockStateMethod = findMethod(world.getClass(), "getBlockState", BlockPos.class);
                lastSchematicWorld = world;
            }
            return world;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static BlockState schematicState(Object world, BlockPos pos) {
        try {
            if (getBlockStateMethod == null) {
                return null;
            }
            return (BlockState) getBlockStateMethod.invoke(world, pos);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... params) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                return c.getMethod(name, params);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }
}
