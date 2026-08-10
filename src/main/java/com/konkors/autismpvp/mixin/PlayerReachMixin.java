package com.konkors.autismpvp.mixin;

import com.konkors.autismpvp.modules.ReachModule;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Both the crosshair pick (LocalPlayer.raycastHitResult) and the attack gate
// (isWithinEntityInteractionRange) read entityInteractionRange()/blockInteractionRange(),
// so overriding their return value extends reach the same way Meteor/Wurst do.
@Mixin(Player.class)
public abstract class PlayerReachMixin {
    @Inject(method = "entityInteractionRange", at = @At("RETURN"), cancellable = true)
    private void autismMinimal$entityRange(CallbackInfoReturnable<Double> cir) {
        if (ReachModule.active()) {
            double extended = ReachModule.reach();
            if (extended > cir.getReturnValue()) {
                cir.setReturnValue(extended);
            }
        }
    }

    @Inject(method = "blockInteractionRange", at = @At("RETURN"), cancellable = true)
    private void autismMinimal$blockRange(CallbackInfoReturnable<Double> cir) {
        if (ReachModule.active()) {
            double extended = ReachModule.reach();
            if (extended > cir.getReturnValue()) {
                cir.setReturnValue(extended);
            }
        }
    }
}
