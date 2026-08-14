package com.konkors.autismpvp.modules.presets;

// "Bedwars" MCTIERS gamemode preset. Bedwars is 1.8-1.9 style PvP: swords, no crystals/anchor, so
// the preset focuses on reach, velocity, autoclicker and w-tap, and leaves crystal/anchor modules off.
public final class BedwarsPresetModule extends GamemodePresetModule {

    public static final String ID = "autismpvp:preset-bedwars";

    public BedwarsPresetModule() {
        super(ID, "Bedwars", "Bedwars", "Bedwars",
            "Apply the Bedwars preset: reach, velocity, autoclicker and w-tap tuned for 1.8-1.9 sword PvP.");
    }
}