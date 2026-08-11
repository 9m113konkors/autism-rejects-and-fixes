package com.example.minimal.modules;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.ChoiceSetting;
import autismclient.api.module.IntSetting;
import autismclient.modules.Module;
import autismclient.modules.ModuleRegistry;
import com.example.minimal.Tier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Random;

// Auto gap macro: holds a golden apple, right-clicks to eat it, then switches back to your
// previous item. Triggers on low health or while the key is held (hold-to-activate). Uses real
// key-presses via AutismKeyMappingBridge so it reads as a genuine right-click.
public final class GapMacroModule extends Module {

    public static final String ID = "autism-minimal-addon-template:gap-macro";

    private enum Phase { IDLE, EAT_WAIT, SWITCH_BACK }

    private final ChoiceSetting trigger = add(new ChoiceSetting("trigger", "Trigger", "Low health",
        "Low health", "Hold key")
        .group("General")
        .description("When to activate the gap macro."));
    private final IntSetting healthThreshold = add(new IntSetting("health", "Health threshold", 8, 1, 20, 1)
        .group("General")
        .description("Eat a gap when your health drops to or below this value (half-hearts on 1.9+).")
        .visibleWhen(() -> "Low health".equals(trigger.get())));
    private final BoolSetting pauseInCombat = add(new BoolSetting("pause-in-combat", "Pause in combat", true)
        .group("Behavior")
        .description("Don't eat gaps while an enemy is within range."));
    private final IntSetting combatRange = add(new IntSetting("combat-range", "Enemy range", 6, 2, 16, 1)
        .group("Behavior")
        .description("Don't eat gaps while an enemy is within this distance.")
        .visibleWhen(() -> pauseInCombat.get()));
    private final IntSetting eatTicks = add(new IntSetting("eat-ticks", "Eat duration (ticks)", 32, 10, 40, 1)
        .group("Timing")
        .description("Ticks to hold the right-click before releasing (golden apple takes 64 ticks = 3.2s)."));

    private final Random random = new Random();
    private Phase phase = Phase.IDLE;
    private int phaseTicks;
    private int originalSlot = -1;
    private int gapSlot = -1;

    public GapMacroModule() {
        super(ID, "Gap Macro",
            "Automatically eats a golden apple when health is low or while the key is held. Switches back to your previous item after.");
    }

    @Override
    public boolean holdToActivate() {
        return "Hold key".equals(trigger.get());
    }

    @Override
    public String info() {
        return tier().label();
    }

    public static Tier tier() {
        Module module = ModuleRegistry.get(ID);
        if (!(module instanceof GapMacroModule m)) return Tier.CLOSET;
        return "Low health".equals(m.trigger.get()) ? Tier.LEGIT : Tier.RISKY;
    }

    public static boolean active() {
        Module module = ModuleRegistry.get(ID);
        return module != null && module.isEnabled();
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        cleanup();
        reset();
    }

    @Override
    public void onGameLeft() {
        cleanup();
        reset();
    }

    @Override
    public void tick() {
        if (MC.player == null || MC.gameMode == null || MC.getConnection() == null) {
            cleanup();
            reset();
            return;
        }
        if (MC.player.isSpectator()) {
            cleanup();
            reset();
            return;
        }
        if (MC.gui != null && MC.gui.screen() != null) {
            return;
        }

        // Check trigger conditions
        if (phase == Phase.IDLE && holdToActivate()) {
            if (!MC.options.keyUse.isDown()) return;
        } else if (phase == Phase.IDLE && "Low health".equals(trigger.get())) {
            float health = MC.player.getHealth() + MC.player.getAbsorptionAmount();
            if (health > healthThreshold.get() * 0.5f) return;
            if (pauseInCombat.get() && enemyWithin(combatRange.get())) return;
            // Don't eat a gap if the offhand has a totem — let the totem handle the save.
            if (isTotem(MC.player.getOffhandItem())) return;
        }

        switch (phase) {
            case IDLE -> {
                int slot = findGap();
                if (slot < 0) {
                    if (originalSlot >= 0) cleanup();
                    return;
                }
                if (originalSlot < 0) {
                    originalSlot = MC.player.getInventory().getSelectedSlot();
                    gapSlot = slot;
                }
                // Select the gap slot
                if (MC.player.getInventory().getSelectedSlot() != slot) {
                    MC.player.getInventory().setSelectedSlot(slot);
                    MC.gameMode.tick();
                }
                // Start using (right-click)
                MC.gameMode.useItem(MC.player, InteractionHand.MAIN_HAND);
                phase = Phase.EAT_WAIT;
                phaseTicks = 0;
            }
            case EAT_WAIT -> {
                phaseTicks++;
                if (phaseTicks >= eatTicks.get()) {
                    // Release right-click
                    MC.gameMode.releaseUsingItem(MC.player);
                    phase = Phase.SWITCH_BACK;
                    phaseTicks = 0;
                }
            }
            case SWITCH_BACK -> {
                if (originalSlot >= 0 && MC.player.getInventory().getSelectedSlot() != originalSlot) {
                    MC.player.getInventory().setSelectedSlot(originalSlot);
                    MC.gameMode.tick();
                }
                cleanup();
                reset();
            }
        }
    }

    private int findGap() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = MC.player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(Items.GOLDEN_APPLE)) {
                return i;
            }
        }
        return -1;
    }

    private boolean enemyWithin(double dist) {
        if (MC.level == null || MC.player == null) return false;
        double distSq = dist * dist;
        for (Player other : MC.level.players()) {
            if (other == MC.player || !other.isAlive() || other.isSpectator()) continue;
            if (MC.player.distanceToSqr(other) <= distSq) return true;
        }
        return false;
    }

    private void cleanup() {
        if (MC.gameMode != null) {
            MC.gameMode.releaseUsingItem(MC.player);
        }
    }

    private static boolean isTotem(ItemStack stack) {
        return !stack.isEmpty() && stack.has(DataComponents.DEATH_PROTECTION);
    }

    private void reset() {
        phase = Phase.IDLE;
        phaseTicks = 0;
        originalSlot = -1;
        gapSlot = -1;
    }
}
