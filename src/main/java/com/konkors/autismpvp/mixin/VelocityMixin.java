package com.konkors.autismpvp.mixin;

import com.konkors.autismpvp.modules.VelocityModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Scales knockback on the local player only. The chance is rolled once per knockback at HEAD and
// reused by both the horizontal (ModifyArg on the Vec3.scale strength) and vertical (RETURN) steps,
// so the two axes always agree.
@Mixin(LivingEntity.class)
public abstract class VelocityMixin {

    private static final String KNOCKBACK =
        "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V";

    @Unique
    private boolean autismMinimal$kbScaled;

    @Inject(method = KNOCKBACK, at = @At("HEAD"))
    private void autismMinimal$kbBegin(double strength, double x, double z,
                                       DamageSource source, float amount, boolean flag, CallbackInfo ci) {
        autismMinimal$kbScaled = false;
        LivingEntity self = (LivingEntity) (Object) this;
        if (self != Minecraft.getInstance().player) return;
        if (!VelocityModule.active() || !VelocityModule.rollPasses()) return;
        autismMinimal$kbScaled = true;
        VelocityModule.notifyKnockback();
    }

    @ModifyArg(method = KNOCKBACK,
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;scale(D)Lnet/minecraft/world/phys/Vec3;"),
        index = 0)
    private double autismMinimal$kbHorizontal(double strength) {
        if (!autismMinimal$kbScaled) return strength;
        return strength * (VelocityModule.horizontalPct() / 100.0);
    }

    @Inject(method = KNOCKBACK, at = @At("RETURN"))
    private void autismMinimal$kbVertical(CallbackInfo ci) {
        if (!autismMinimal$kbScaled) return;
        double vertical = VelocityModule.verticalPct() / 100.0;
        if (vertical >= 1.0) return;
        LivingEntity self = (LivingEntity) (Object) this;
        Vec3 motion = self.getDeltaMovement();
        self.setDeltaMovement(motion.x, motion.y * vertical, motion.z);
    }
}
