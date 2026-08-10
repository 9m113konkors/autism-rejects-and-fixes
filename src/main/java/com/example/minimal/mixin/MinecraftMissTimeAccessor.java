package com.example.minimal.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Exposes the client's attack swing cooldown (Minecraft.missTime) so the addon can suppress the
// host client KillAura's internal clicker and own the click rate instead.
@Mixin(Minecraft.class)
public interface MinecraftMissTimeAccessor {
    @Accessor("missTime")
    void autismMinimal$setMissTime(int missTime);
}
