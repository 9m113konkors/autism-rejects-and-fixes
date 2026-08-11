package com.konkors.autismpvp.modules;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.ChoiceSetting;
import autismclient.api.module.IntSetting;
import autismclient.modules.Module;
import autismclient.modules.ModuleRegistry;
import autismclient.util.AutismInventoryClickHelper;
import autismclient.util.AutismInventoryHelper;
import com.konkors.autismpvp.Tier;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// Legit-looking auto totem: opens your real inventory and swaps a totem into the offhand with real
// container clicks (ServerboundContainerClickPacket), so anticheat sees normal inventory interaction
// instead of bare item-change packets. Timing is randomized so it feels human.
//
// By default it reacts on totem pop instead of predicting: when the offhand totem is consumed it
// refills the offhand. Optional double-handing then holds a second totem, but only ever in an empty
// hotbar slot or one that already holds a totem - never in the slot you are using - and switches
// back to your previous slot afterwards so your other items stay usable.
public final class LegitAutoTotemModule extends Module {

    public static final String ID = "autismpvp:legit-auto-totem";
    public static volatile long lastSwapMs;

    private static final int OFFHAND_INDEX = 40;

    private final ChoiceSetting trigger = add(new ChoiceSetting("trigger", "Trigger", "On totem pop",
        "On totem pop", "On low health").group("Behavior"));
    private final IntSetting chance = add(new IntSetting("chance", "Chance (%)", 100, 0, 100, 5)
        .group("Timing"));
    private final IntSetting minDelay = add(new IntSetting("min-delay", "Min delay (ms)", 60, 0, 500, 5)
        .group("Timing"));
    private final IntSetting maxDelay = add(new IntSetting("max-delay", "Max delay (ms)", 200, 0, 500, 5)
        .group("Timing"));
    private final BoolSetting doubleHand = add(new BoolSetting("double-hand", "Double hand", true)
        .group("Behavior").description("Hold a second totem in an empty or totem hotbar slot after a pop."));
    private final BoolSetting switchBack = add(new BoolSetting("switch-back", "Switch back", true)
        .group("Behavior").description("Return to your previous hotbar slot after double-handing.")
        .visibleWhen(() -> doubleHand.get()));
    private final IntSetting switchBackDelay = add(new IntSetting("switch-back-delay", "Switch back delay (ms)", 1500, 200, 5000, 100)
        .group("Behavior").visibleWhen(() -> doubleHand.get() && switchBack.get()));
    private final BoolSetting healthGate = add(new BoolSetting("health-gate", "Only when low health", true)
        .group("Behavior").visibleWhen(() -> !popMode()));
    private final IntSetting healthThreshold = add(new IntSetting("health-threshold", "Health threshold", 14, 0, 20, 1)
        .group("Behavior").visibleWhen(() -> !popMode() && healthGate.get()));

    private final Random random = new Random();

    private enum Phase { IDLE, WAIT_OPEN, RUNNING, CLOSING, SWITCH_BACK }

    private record Transfer(int source, int target) {
    }

    private Phase phase = Phase.IDLE;
    private final List<Transfer> plan = new ArrayList<>();
    private int step;
    private int subStep;
    private long nextAtMs;
    private long closeAtMs;
    private long restoreAtMs;
    private long cooldownUntilMs;
    private boolean openedByUs;
    private boolean offhandHadTotem;
    private int originalSelectedSlot;
    private int mainHandSlot = -1;

    public LegitAutoTotemModule() {
        super(ID, "Legit AutoTotem", "Refills your offhand totem with real inventory clicks after it pops, and optionally double-hands into an empty or totem hotbar slot so your other items stay usable.");
    }

    @Override
    public String info() {
        return chance.get() + "% " + tier().label();
    }

    public static Tier tier() {
        Module module = ModuleRegistry.get(ID);
        return module instanceof LegitAutoTotemModule m ? Tier.forChance(m.chance.get()) : Tier.CLOSET;
    }

    public static boolean active() {
        Module module = ModuleRegistry.get(ID);
        return module != null && module.isEnabled();
    }

    @Override
    public void onEnable() {
        reset(true);
        cooldownUntilMs = 0;
        offhandHadTotem = MC.player != null && isTotem(MC.player.getOffhandItem());
    }

    @Override
    public void onDisable() {
        reset(true);
    }

    @Override
    public void onGameLeft() {
        reset(true);
    }

    private boolean popMode() {
        return "On totem pop".equals(choice("trigger"));
    }

    @Override
    public void tick() {
        if (MC.player == null || MC.getConnection() == null || MC.gameMode == null) {
            reset(false);
            return;
        }
        if (phase != Phase.IDLE) {
            advance();
            return;
        }
        if (MC.gui.screen() != null || !MC.player.isAlive()
            || MC.player.isCreative() || MC.player.isSpectator()) {
            return;
        }

        long now = System.currentTimeMillis();

        if (popMode()) {
            boolean offhandTotem = isTotem(MC.player.getOffhandItem());
            boolean popped = offhandHadTotem && !offhandTotem;
            offhandHadTotem = offhandTotem;
            if (!popped) {
                return;
            }
        } else if (healthGate.get() && MC.player.getHealth() + MC.player.getAbsorptionAmount() >= healthThreshold.get()) {
            return;
        }

        if (now < cooldownUntilMs) {
            return;
        }
        if (!buildPlan()) {
            return;
        }
        if (chance.get() < 100 && random.nextInt(100) >= chance.get()) {
            return;
        }

        MC.gui.setScreen(new InventoryScreen(MC.player));
        openedByUs = true;
        phase = Phase.WAIT_OPEN;
        nextAtMs = now + delay();
    }

    private boolean buildPlan() {
        plan.clear();
        step = 0;
        subStep = 0;

        long now = System.currentTimeMillis();
        originalSelectedSlot = MC.player.getInventory().getSelectedSlot();
        mainHandSlot = doubleHand.get() ? findDoubleHandSlot() : -1;

        boolean offhandOk = isTotem(MC.player.getOffhandItem());
        if (offhandOk && mainHandSlot < 0) {
            cooldownUntilMs = now + 500L + random.nextInt(700);
            return false;
        }

        int offhandSource = -1;
        if (!offhandOk) {
            offhandSource = findTotemSkipping(mainHandSlot, originalSelectedSlot);
            if (offhandSource < 0) {
                cooldownUntilMs = now + 500L + random.nextInt(700);
                return false;
            }
            plan.add(new Transfer(offhandSource, OFFHAND_INDEX));
        }

        if (mainHandSlot >= 0 && !isTotem(MC.player.getInventory().getItem(mainHandSlot))) {
            int src = findTotemSkipping(offhandSource, mainHandSlot);
            if (src >= 0) {
                plan.add(new Transfer(src, mainHandSlot));
            }
        }

        if (plan.isEmpty()) {
            cooldownUntilMs = now + 500L + random.nextInt(700);
            return false;
        }
        return true;
    }

    // The main-hand totem only ever goes into an empty hotbar slot or one that already holds a
    // totem, never into the slot the player is currently using.
    private int findDoubleHandSlot() {
        int selected = MC.player.getInventory().getSelectedSlot();
        for (int slot = 0; slot < 9; slot++) {
            if (slot == selected) {
                continue;
            }
            ItemStack stack = MC.player.getInventory().getItem(slot);
            if (stack.isEmpty() || isTotem(stack)) {
                return slot;
            }
        }
        return -1;
    }

    private void advance() {
        long now = System.currentTimeMillis();
        Screen screen = MC.gui.screen();
        boolean ours = screen instanceof InventoryScreen;
        switch (phase) {
            case WAIT_OPEN -> {
                if (!ours || MC.player.containerMenu != MC.player.inventoryMenu) {
                    reset(false);
                    return;
                }
                phase = Phase.RUNNING;
                nextAtMs = now + delay();
            }
            case RUNNING -> {
                if (!ours || MC.player.containerMenu != MC.player.inventoryMenu) {
                    reset(true);
                    return;
                }
                if (now < nextAtMs) {
                    return;
                }
                if (!doNextStep()) {
                    reset(true);
                    return;
                }
                if (step < plan.size()) {
                    nextAtMs = now + delay();
                } else {
                    phase = Phase.CLOSING;
                    closeAtMs = now + delay();
                }
            }
            case CLOSING -> {
                if (now >= closeAtMs) {
                    if (ours) {
                        screen.onClose();
                    }
                    if (mainHandSlot >= 0) {
                        AutismInventoryHelper.selectHotbarSlot(MC, mainHandSlot);
                        phase = Phase.SWITCH_BACK;
                        restoreAtMs = System.currentTimeMillis() + switchBackDelay.get();
                    } else {
                        cooldownUntilMs = System.currentTimeMillis() + 400L + random.nextInt(600);
                        reset(false);
                    }
                }
            }
            case SWITCH_BACK -> {
                if (now >= restoreAtMs) {
                    AutismInventoryHelper.restoreHotbarSlot(MC, originalSelectedSlot);
                    cooldownUntilMs = System.currentTimeMillis() + 400L + random.nextInt(600);
                    reset(false);
                }
            }
            default -> reset(false);
        }
    }

    private boolean doNextStep() {
        if (step >= plan.size()) {
            return true;
        }
        Transfer t = plan.get(step);
        AbstractContainerMenu menu = MC.player.containerMenu;
        if (menu == null) {
            return false;
        }

        if (subStep == 0) {
            int sourceHandler = AutismInventoryHelper.toHandlerSlot(MC, t.source());
            if (sourceHandler < 0) {
                return false;
            }
            if (!isTotem(MC.player.getInventory().getItem(t.source()))) {
                return false;
            }
            if (!menu.getCarried().isEmpty()) {
                return false;
            }
            if (!click(sourceHandler, 0, ContainerInput.PICKUP)) {
                return false;
            }
            if (!isTotem(menu.getCarried())) {
                return false;
            }
            subStep = 1;
            return true;
        }
        if (subStep == 1) {
            int targetHandler = AutismInventoryHelper.toHandlerSlot(MC, t.target());
            if (targetHandler < 0) {
                return false;
            }
            if (!isTotem(menu.getCarried())) {
                return false;
            }
            if (!click(targetHandler, 0, ContainerInput.PICKUP)) {
                return false;
            }
            if (menu.getCarried().isEmpty()) {
                completeTransfer();
            } else {
                subStep = 2;
            }
            return true;
        }

        int sourceHandler = AutismInventoryHelper.toHandlerSlot(MC, t.source());
        if (sourceHandler < 0) {
            return false;
        }
        if (!click(sourceHandler, 0, ContainerInput.PICKUP)) {
            return false;
        }
        completeTransfer();
        return true;
    }

    private void completeTransfer() {
        lastSwapMs = System.currentTimeMillis();
        step++;
        subStep = 0;
    }

    private boolean click(int handlerSlot, int button, ContainerInput input) {
        if (handlerSlot < 0) {
            return false;
        }
        return AutismInventoryClickHelper.click(MC, handlerSlot, button, input);
    }

    private long delay() {
        int lo = Math.min(minDelay.get(), maxDelay.get());
        int hi = Math.max(minDelay.get(), maxDelay.get());
        return lo + (hi > lo ? random.nextInt(hi - lo + 1) : 0);
    }

    private int findTotemSkipping(int... skipSlots) {
        outer:
        for (int i = 0; i < 36; i++) {
            for (int skip : skipSlots) {
                if (i == skip) {
                    continue outer;
                }
            }
            if (isTotem(MC.player.getInventory().getItem(i))) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isTotem(ItemStack stack) {
        return !stack.isEmpty() && stack.has(DataComponents.DEATH_PROTECTION);
    }

    private void reset(boolean closeIfOurs) {
        if (closeIfOurs && openedByUs) {
            Screen screen = MC.gui.screen();
            if (screen instanceof InventoryScreen) {
                screen.onClose();
            }
        }
        openedByUs = false;
        phase = Phase.IDLE;
        plan.clear();
        step = 0;
        subStep = 0;
        nextAtMs = -1;
        closeAtMs = -1;
        restoreAtMs = -1;
        mainHandSlot = -1;
        originalSelectedSlot = MC.player != null ? MC.player.getInventory().getSelectedSlot() : -1;
    }
}
