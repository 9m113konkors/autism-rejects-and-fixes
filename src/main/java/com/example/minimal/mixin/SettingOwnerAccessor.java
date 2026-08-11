package com.example.minimal.mixin;

import autismclient.api.module.Setting;
import autismclient.api.module.SettingOwner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Exposes the owning module of a Setting so the addon can tell which settings belong to it
// (modules are attached as the setting owner via Setting.attach when Module.add is called).
@Mixin(Setting.class)
public interface SettingOwnerAccessor {
    @Accessor("owner")
    SettingOwner autismMinimal$owner();
}
