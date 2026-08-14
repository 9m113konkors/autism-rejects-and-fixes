package com.konkors.autismpvp.mixin;

import com.konkors.autismpvp.modules.BetterNameTagsModule;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Clears the vanilla entity nameTag while the BetterNameTags module owns it, so the stock black tag
// never appears underneath our rendered one. Same approach the client itself uses for its own tags.
@Mixin(EntityRenderer.class)
public abstract class EntityNameTagSuppressMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void autismMinimal$suppressVanillaNameTag(Entity entity, EntityRenderState state, float partialTick, CallbackInfo ci) {
        if (state != null && state.nameTag != null && BetterNameTagsModule.tags(entity)) {
            state.nameTag = null;
        }
    }
}