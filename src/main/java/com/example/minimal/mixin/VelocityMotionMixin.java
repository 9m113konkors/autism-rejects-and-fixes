package com.example.minimal.mixin;

import com.example.minimal.modules.KnockbackDelayModule;
import com.example.minimal.modules.VelocityModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// In modern Minecraft multiplayer the local player's knockback is server-authoritative: the server
// computes the knockback and sends it back as a ClientboundSetEntityMotionPacket, which the client
// applies with Entity.lerpMotion. LivingEntity.knockback is only called on the server, so it never
// fires on the client. This mixin scales the motion packet whenever it targets the local player.
// Three paths:
//  - Jump Reset: apply the full knockback untouched and jump on the next tick (vanilla mechanic).
//  - Reduce: apply the scaled motion instantly or schedule it for later.
//  - Smooth: apply full motion now and start a gradual decay toward the target percent.
//  - Packet: don't apply the knockback at all (bypass).
@Mixin(ClientPacketListener.class)
public abstract class VelocityMotionMixin {

    @Redirect(method = "handleSetEntityMotion",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;lerpMotion(Lnet/minecraft/world/phys/Vec3;)V"))
    private void autismMinimal$scaleMotion(Entity entity, Vec3 movement) {
        if (entity == Minecraft.getInstance().player) {
            // KnockbackDelay: hold the knockback packet and apply it later
            if (KnockbackDelayModule.holdIfNeeded(movement)) {
                return;
            }
            if (VelocityModule.active() && VelocityModule.rollPasses()) {
                VelocityModule.notifyKnockback();
                if (VelocityModule.jumpResets()) {
                    VelocityModule.scheduleJump();
                } else if (VelocityModule.packetMode()) {
                    return;
                } else if (VelocityModule.smoothMode()) {
                    int h = VelocityModule.horizontalPct();
                    int v = VelocityModule.verticalPct();
                    if (h != 100 || v != 100) {
                        VelocityModule.scheduleSmooth(movement);
                    }
                } else {
                    int h = VelocityModule.horizontalPct();
                    int v = VelocityModule.verticalPct();
                    if (h != 100 || v != 100) {
                        Vec3 scaled = new Vec3(movement.x * h / 100.0, movement.y * v / 100.0, movement.z * h / 100.0);
                        if (VelocityModule.delayTicks() > 0) {
                            VelocityModule.schedule(scaled);
                        } else {
                            movement = scaled;
                        }
                    }
                }
            }
        }
        entity.lerpMotion(movement);
    }
}
