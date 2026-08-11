package com.example.minimal.mixin;

import com.example.minimal.modules.KnockbackDelayModule;
import com.example.minimal.modules.VelocityModule;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// One call per client tick: applies the deferred anti-velocity reduction after the delay counted
// from the knockback motion packet (see VelocityModule.schedule / VelocityMotionMixin), and drives
// the KnockbackDelay grounded tracker + held-knockback release.
@Mixin(ClientPacketListener.class)
public abstract class VelocityDelayMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void autismMinimal$tickVelocity(CallbackInfo ci) {
        VelocityModule.onTick();
        KnockbackDelayModule.onTick();
    }
}
