package com.example.minimal.mixin;

import autismclient.api.module.Setting;
import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContext;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.module.VanillaModuleMenuController;
import autismclient.modules.Module;
import com.example.minimal.api.RangeSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Gives the addon's RangeSetting a two-handle slider bar. The host only ships single-handle
// sliders, so this renders the range row itself (track, accent fill between both handles, two
// knobs, and a "min-max" value readout) and intercepts mouse input for the bar. Everything stays
// inside the addon; no client code is modified.
@Mixin(VanillaModuleMenuController.class)
public abstract class RangeSliderMixin {

    @Shadow
    private void setOptionValueTransient(Module module, Setting<?, ?> option, String value) {
    }

    @Shadow
    private void invalidateSettings(Module module) {
    }

    @Unique
    private UiBounds autismMinimal$rangeBounds;

    @Unique
    private Module autismMinimal$rangeModule;

    @Unique
    private Setting<?, ?> autismMinimal$rangeOption;

    @Unique
    private boolean autismMinimal$rangeDragging;

    @Inject(method = "render", at = @At("HEAD"))
    private void autismMinimal$clearRangeState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                               float delta, int width, int height, CallbackInfo ci) {
        autismMinimal$rangeBounds = null;
        autismMinimal$rangeModule = null;
        autismMinimal$rangeOption = null;
    }

    @Inject(method = "renderNumericOption", at = @At("HEAD"), cancellable = true)
    private void autismMinimal$renderRangeOption(UiContext context, Module module, Setting<?, ?> option,
                                                 UiBounds control, CallbackInfo ci) {
        if (!(option instanceof RangeSetting range)) {
            return;
        }
        autismMinimal$rangeBounds = null;
        int valueW = Math.min(48, Math.max(38, control.width() / 3));
        UiBounds slider = UiBounds.of(control.x(), control.y(),
            Math.max(36, control.width() - valueW - 4), control.height());
        UiBounds value = UiBounds.of(slider.right() + 4, control.y(), valueW, control.height());
        boolean hovered = slider.contains(context.mouseX(), context.mouseY());
        drawRangeSlider(context, slider, range, hovered);
        String display = range.minValue() + "-" + range.maxValue();
        context.text().drawFitted(context.graphics(), display, value.x(), context.text().centeredY(value),
            value.width(), context.theme().colors().text);
        autismMinimal$rangeBounds = slider;
        autismMinimal$rangeModule = module;
        autismMinimal$rangeOption = option;
        ci.cancel();
    }

    @Unique
    private void drawRangeSlider(UiContext context, UiBounds bounds, RangeSetting range, boolean hovered) {
        var colors = context.theme().colors();
        UiRenderer.frame(context.graphics(), bounds, colors.field,
            hovered ? colors.border : colors.borderSoft);
        UiBounds track = bounds.inset(3, Math.max(4, bounds.height() / 2 - 1), 3,
            Math.max(4, bounds.height() / 2 - 1));
        if (track.height() <= 0) {
            track = UiBounds.of(bounds.x() + 3, bounds.y() + bounds.height() / 2,
                Math.max(1, bounds.width() - 6), 1);
        }
        UiRenderer.rect(context.graphics(), track, 0xCC30333C);
        int domain = Math.max(1, range.domainMax() - range.domainMin());
        int minX = track.x() + (int) Math.round(
            track.width() * (range.minValue() - range.domainMin()) / (double) domain);
        int maxX = track.x() + (int) Math.round(
            track.width() * (range.maxValue() - range.domainMin()) / (double) domain);
        if (minX > maxX) {
            int tmp = minX;
            minX = maxX;
            maxX = tmp;
        }
        UiRenderer.rect(context.graphics(),
            UiBounds.of(minX, track.y(), Math.max(1, maxX - minX), track.height()), colors.accent);
        drawKnob(context, bounds, minX, hovered ? colors.text : colors.muted);
        drawKnob(context, bounds, maxX, hovered ? colors.text : colors.muted);
    }

    @Unique
    private void drawKnob(UiContext context, UiBounds bounds, int centerX, int color) {
        int knobX = Math.max(bounds.x() + 2, Math.min(bounds.right() - 6, centerX - 2));
        UiRenderer.rect(context.graphics(), UiBounds.of(knobX, bounds.y() + 2, 5,
            Math.max(1, bounds.height() - 4)), color);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void autismMinimal$onRangeClicked(int mx, int my, int button,
                                             CallbackInfoReturnable<Boolean> cir) {
        if (button == 0 && autismMinimal$rangeBounds != null
            && autismMinimal$rangeBounds.contains(mx, my)
            && autismMinimal$rangeOption instanceof RangeSetting range) {
            autismMinimal$rangeDragging = true;
            applyRange(range, mx);
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void autismMinimal$onRangeDragged(int mx, int my, int button, double dx, double dy,
                                             CallbackInfoReturnable<Boolean> cir) {
        if (autismMinimal$rangeDragging && autismMinimal$rangeOption instanceof RangeSetting range) {
            applyRange(range, mx);
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void autismMinimal$onRangeReleased(int mx, int my, int button,
                                              CallbackInfoReturnable<Boolean> cir) {
        if (autismMinimal$rangeDragging) {
            autismMinimal$rangeDragging = false;
            if (autismMinimal$rangeModule != null) {
                autismMinimal$rangeModule.persistConfiguredState();
            }
            cir.setReturnValue(true);
        }
    }

    @Unique
    private void applyRange(RangeSetting range, int mx) {
        if (autismMinimal$rangeBounds == null || autismMinimal$rangeModule == null) {
            return;
        }
        UiBounds bounds = autismMinimal$rangeBounds;
        int domainMin = range.domainMin();
        int domainMax = range.domainMax();
        double ratio = (mx - bounds.x()) / (double) Math.max(1, bounds.width());
        ratio = Math.max(0.0, Math.min(1.0, ratio));
        int value = domainMin + (int) Math.round(ratio * (domainMax - domainMin));
        int step = (int) Math.round(range.step());
        if (step > 1) {
            value = domainMin + Math.round((value - domainMin) / (float) step) * step;
        }
        int curMin = range.minValue();
        int curMax = range.maxValue();
        double span = Math.max(1, domainMax - domainMin);
        double minRatio = (curMin - domainMin) / span;
        double maxRatio = (curMax - domainMin) / span;
        double clickRatio = ratio;
        int newMin = curMin;
        int newMax = curMax;
        if (Math.abs(clickRatio - minRatio) <= Math.abs(clickRatio - maxRatio)) {
            newMin = Math.min(value, curMax);
        } else {
            newMax = Math.max(value, curMin);
        }
        setOptionValueTransient(autismMinimal$rangeModule, range, RangeSetting.encode(newMin, newMax));
        invalidateSettings(autismMinimal$rangeModule);
    }
}
