package com.konkors.autismpvp.mixin;

import com.konkors.autismpvp.modules.WorldChamsModule;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Renders the WorldChamsModule's ESP (tracers, boxes, info) in the same overlay pass the
// client's own overlay UI uses, so the ESP is always visible through walls.
@Mixin(Hud.class)
public abstract class HudChamsMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void autismMinimal$renderWorldChams(GuiGraphicsExtractor context, DeltaTracker deltaTracker, CallbackInfo ci) {
        WorldChamsModule.render(context);
    }
}
