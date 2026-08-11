package com.example.minimal.mixin;

import com.example.minimal.modules.BetterNameTagsModule;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Renders the BetterNameTags module's tags in the same overlay pass the client's own overlay UI
// uses, so the tags are scaled consistently with the HUD. Pure observer; no-op when the module is off.
@Mixin(Hud.class)
public abstract class HudNametagsMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void autismMinimal$renderBetterNameTags(GuiGraphicsExtractor context, DeltaTracker deltaTracker, CallbackInfo ci) {
        BetterNameTagsModule.render(context);
    }
}