package com.konkors.autismpvp.modules;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.IntSetting;
import autismclient.modules.Module;
import autismclient.modules.ModuleRegistry;
import autismclient.util.AutismKeyMappingBridge;
import com.konkors.autismpvp.Tier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Random;

// 1.9 sword + shield meta for KillAura/AimAssist users: keeps the shield raised the whole fight and
// only drops it for the exact tick a swing goes out (right-click = block, and you cannot attack while
// blocking). The swing hook is driven by the addon's BetterAutoClicker so the release happens in the
// same thread right before the attack packet is sent; the shield then goes back up for the swing, and
// stays up between swings. Reads as a player who blocks between every hit, like real sword PvP.
public final class AutoShieldModule extends Module {

    public static final String ID = "autismpvp:auto-shield";
    public static volatile long lastBlockMs;

    private enum Phase { RAISED, SWING }

    private final BoolSetting onlyInCombat = add(new BoolSetting("only-in-combat", "Only in combat", true)
        .group("Behavior")
        .description("Only raise the shield while an enemy is within range below. Off = block all the time while enabled."));
    private final IntSetting enemyRange = add(new IntSetting("enemy-range", "Enemy range (blocks)", 6, 2, 16, 1)
        .group("Behavior")
        .visibleWhen(() -> onlyInCombat.get())
        .description("An enemy inside this distance counts as combat."));
    private final IntSetting releaseTicks = add(new IntSetting("release-ticks", "Open for swing (ticks)", 2, 0, 6, 1)
        .group("Timing")
        .description("How many ticks the shield stays DOWN after a swing before it goes back up. 1-2 leaves exactly the attack moment open; higher keeps you unblocked longer, which anticheats read as a normal player."));
    private final IntSetting reblockJitter = add(new IntSetting("reblock-jitter", "Reblock jitter (ticks)", 1, 0, 5, 1)
        .group("Timing")
        .description("Random extra ticks before re-raising after each swing so the block rhythm is never identical (a fixed +=N restrike is a classic macro tell). Lower = tighter, more efficient. Higher = more human-looking."));

    private final Random random = new Random();

    private Phase phase = Phase.RAISED;
    private int leaveDown;
    private boolean prevUse;

    public AutoShieldModule() {
        super(ID, "AutoShield", "Keeps your shield up between hits for KillAura + AimAssist combos: raises it while fighting and drops it for exactly the swing so every hit still lands. 1.9 sword meta.");
    }

    @Override
    public String info() {
        return (onlyInCombat.get() ? "Combat" : "Always") + " " + tier().label();
    }

    public static Tier tier() {
        Module module = ModuleRegistry.get(ID);
        if (!(module instanceof AutoShieldModule m)) return Tier.CLOSET;
        return m.onlyInCombat.get() ? Tier.LEGIT : Tier.RISKY;
    }

    public static boolean active() {
        Module module = ModuleRegistry.get(ID);
        return module != null && module.isEnabled();
    }

    // Called by BetterAutoClicker in the same tick, right before the attack packet goes out.
    public static void willAttack() {
        Module module = ModuleRegistry.get(ID);
        if (module instanceof AutoShieldModule m && module.isEnabled() && m.canShield()) {
            m.openForSwing();
        }
    }

    @Override
    public void onEnable() {
        prevUse = MC.options != null && MC.options.keyUse.isDown();
        phase = Phase.RAISED;
        leaveDown = 0;
    }

    @Override
    public void onDisable() {
        restore();
        phase = Phase.RAISED;
        leaveDown = 0;
    }

    @Override
    public void onGameLeft() {
        onDisable();
    }

    @Override
    public void tick() {
        if (MC.player == null || MC.gameMode == null || MC.getConnection() == null) {
            onDisable();
            return;
        }
        if (MC.player.isSpectator() || !MC.player.isAlive()
            || (MC.gui != null && MC.gui.screen() != null)) {
            onDisable();
            return;
        }
        if (!canShield()) {
            // No shield, or someone else owns right-click (eating a gap, charging a bow/spear):
            // leave the use key completely alone.
            phase = Phase.RAISED;
            leaveDown = 0;
            return;
        }

        if (phase == Phase.SWING) {
            if (leaveDown > 0) {
                leaveDown--;
                return; // keep the shield down for the swing window
            }
            phase = Phase.RAISED;
        }

        if (onlyInCombat.get() && !enemyWithin(enemyRange.get())) {
            release();
            phase = Phase.RAISED;
            leaveDown = 0;
            return;
        }

        raise();
    }

    // Release right-click now (drops the shield) for the swing + a jittered recovery window.
    private void openForSwing() {
        if (phase != Phase.RAISED) {
            return;
        }
        release();
        lastBlockMs = System.currentTimeMillis();
        leaveDown = Math.max(0, releaseTicks.get()) + random.nextInt(reblockJitter.get() + 1);
        phase = Phase.SWING;
    }

    private void raise() {
        if (MC.options == null) {
            return;
        }
        if (!MC.options.keyUse.isDown()) {
            AutismKeyMappingBridge.of(MC.options.keyUse).autism$simulatePress(true);
        }
    }

    // Force right-click down so the swing always lands, even if the user is holding the button.
    private void release() {
        if (MC.options != null) {
            MC.options.keyUse.setDown(false);
        }
    }

    private void restore() {
        if (MC.options != null) {
            MC.options.keyUse.setDown(prevUse);
        }
    }

    // Blocking is only meaningful with a shield + a melee weapon, and only when the player is not
    // already using a non-shield item (gap, bow, spear charge).
    private boolean canShield() {
        if (MC.player == null) {
            return false;
        }
        if (MC.player.isUsingItem()) {
            ItemStack useItem = MC.player.getUseItem();
            if (useItem == null || useItem.isEmpty() || !isShield(useItem)) {
                return false;
            }
        }
        return isShield(MC.player.getItemInHand(InteractionHand.MAIN_HAND))
            || isShield(MC.player.getItemInHand(InteractionHand.OFF_HAND));
    }

    private boolean enemyWithin(double dist) {
        if (MC.level == null || MC.player == null) {
            return false;
        }
        double distSq = dist * dist;
        for (Player other : MC.level.players()) {
            if (other == MC.player || !other.isAlive() || other.isSpectator()) {
                continue;
            }
            if (MC.player.distanceToSqr(other) <= distSq) {
                return true;
            }
        }
        return false;
    }

    private static boolean isShield(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.is(Items.SHIELD)) {
            return true;
        }
        var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key != null && key.getPath().toLowerCase().contains("shield");
    }
}