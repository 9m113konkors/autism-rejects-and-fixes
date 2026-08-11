package com.example.minimal.mixin;

import autismclient.api.module.Setting;
import autismclient.api.module.SettingOwner;
import autismclient.gui.vanillaui.module.VanillaModuleMenuController;
import autismclient.modules.Module;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// The host's settings window draws a setting's description inline on the setting row. When the
// description is long it is scaled down (drawFitted) to fit the label column - long addon
// descriptions end up as tiny, hard-to-read text. Instead, for addon settings we hide the inline
// line and let the existing hover tooltip show the full description at normal size (the tooltip
// path is already used for rows that don't fit the inline description).
@Mixin(VanillaModuleMenuController.class)
public abstract class SettingDescriptionMixin {

    private static final String ADDON_NS = "autism-minimal-addon-template:";

    // renderSettingRow calls Setting.description() first to decide whether the inline description
    // fits (line: boolean descriptionShownInline = !option.description().isBlank() && ...). We
    // override only that first call (ordinal 0) for addon settings, so the inline line is skipped
    // and hovering the row shows the full-size tooltip instead.
    @Redirect(method = "renderSettingRow",
        at = @At(value = "INVOKE", ordinal = 0,
            target = "Lautismclient/api/module/Setting;description()Ljava/lang/String;"))
    private String autismMinimal$hideInlineDescriptionForAddon(Setting<?, ?> option) {
        SettingOwner owner = ((SettingOwnerAccessor) (Object) option).autismMinimal$owner();
        if (owner instanceof Module module && module.id().startsWith(ADDON_NS)) {
            return "";
        }
        return option.description();
    }
}
