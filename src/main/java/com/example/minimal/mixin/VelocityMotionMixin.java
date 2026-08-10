package com.konkors.autismpvp.mixin;

import com.konkors.autismpvp.modules.VelocityModule;
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
@Mixin(ClientPacketListener.class)
public abstract class VelocityMotionMixin {

    @Redirect(method = "handleSetEntityMotion",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;lerpMotion(Lnet/minecraft/world/phys/Vec3;)V"))
    private void autismMinimal$scaleMotion(Entity entity, Vec3 movement) {
        if (entity == Minecraft.getInstance().player && VelocityModule.active() && VelocityModule.rollPasses()) {
            VelocityModule.notifyKnockback();
            int h = VelocityModule.horizontalPct();
            int v = VelocityModule.verticalPct();
            if (h != 100 || v != 100) {
                movement = new Vec3(movement.x * h / 100.0, movement.y * v / 100.0, movement.z * h / 100.0);
            }
        }
        entity.lerpMotion(movement);
    }
}
