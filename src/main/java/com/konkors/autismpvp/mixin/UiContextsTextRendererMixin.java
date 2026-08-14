package com.konkors.autismpvp.mixin;

import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.UiTextRenderer;
import com.konkors.autismpvp.ThinFont;
import com.konkors.autismpvp.modules.BetterClickGuiModule;
import net.minecraft.client.gui.Font;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Swaps the font the host's vanilla-looking UI uses for its text renderers. When the addon's thin
// font is active we hand back a UiTextRenderer built on our runtime-loaded thin TTF instead of the
// blocky vanilla font. If the thin font ever fails to load we simply don't override, so the menu
// keeps working with the default font.
@Mixin(UiContexts.class)
public abstract class UiContextsTextRendererMixin {

    @Inject(method = "textRenderer", at = @At("HEAD"), cancellable = true)
    private static void autismPvp$thinFont(Font font, CallbackInfoReturnable<UiTextRenderer> cir) {
        if (BetterClickGuiModule.thinFontActive()) {
            UiTextRenderer thin = ThinFont.renderer(BetterClickGuiModule.fontSize());
            if (thin != null) {
                cir.setReturnValue(thin);
            }
        }
    }
}
