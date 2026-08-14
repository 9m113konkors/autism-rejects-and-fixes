package com.konkors.autismpvp.modules;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.ChoiceSetting;
import autismclient.api.module.ColorSetting;
import autismclient.api.module.DoubleSetting;
import autismclient.api.module.IntSetting;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.modules.Module;
import autismclient.modules.ModuleRegistry;
import autismclient.util.AutismConfig;
import autismclient.util.AutismTheme;
import com.konkors.autismpvp.Tier;

// Re-skins the ENTIRE client's click GUI / module menu to look like Vape v4's click GUI.
//
// Vape v4's GUI is a set of semi-transparent dark-navy panels with a thin light outline and a
// single cyan accent used for toggles, sliders and highlights. This module does not draw its own
// overlay: the host client already resolves every GUI color from AutismConfig.themeColors through
// AutismTheme.recolor(), so we write a Vape-style palette into the live config, push it through
// AutismTheme.reload() + UiContexts.refreshTheme(), and restore the previous palette on disable.
// That themes the module screen, overlays and menus uniformly with zero mixins.
public final class BetterClickGuiModule extends Module {

    public static final String ID = "autismpvp:better-clickgui";

    private final ChoiceSetting style = add(new ChoiceSetting("style", "Style", "Vape Dark",
        "Vape Dark", "Vape Light", "Ocean", "Retro").group("General")
        .description("Palette for the whole client GUI. Vape Dark is the classic cyan-on-navy look; Vape Light is a bright theme with the same layout feel."));
    private final ColorSetting accent = add(new ColorSetting("accent", "Accent color", 0xFF1CE6D5)
        .group("General")
        .description("Accent used for toggles, sliders, highlights and focused fields."));
    private final IntSetting opacity = add(new IntSetting("opacity", "Background opacity (%)", 75, 10, 100, 5)
        .group("General")
        .description("How solid the panel background is. Lower values give the signature translucent Vape look."));
    private final BoolSetting outline = add(new BoolSetting("outline", "Light outline", true)
        .group("General")
        .description("Draw the thin bright outline around panels that Vape's GUI is known for."));

    private final BoolSetting thinFont = add(new BoolSetting("thin-font", "Thin font", true)
        .group("Typography")
        .description("Render the whole click GUI with a thinner, cleaner sans-serif (Segoe UI Light) instead of the default blocky vanilla pixel font. This is what gives it that Vape/Prestige 'light' look."));
    private final DoubleSetting fontSize = add(new DoubleSetting("font-size", "Thin font size", 9.0, 7.0, 14.0, 0.5)
        .group("Typography")
        .visibleWhen(() -> thinFont.get())
        .description("Pixel size of the thin font. 9 matches the vanilla line height; a touch larger reads smoother on high-DPI screens."));

    private int[] saved = new int[0];

    public BetterClickGuiModule() {
        super(ID, "Better Click GUI",
            "Makes the whole client click GUI look and feel like Vape v4's click GUI: dark translucent panels, thin outline and a single cyan accent. Restores your theme the moment the module is turned off.");
    }

    @Override
    public String info() {
        return style.get() + " " + tier().label();
    }

    public static Tier tier() {
        return Tier.CLOSET;
    }

    public static boolean active() {
        Module module = ModuleRegistry.get(ID);
        return module != null && module.isEnabled();
    }

    // True when the addon's thin font should replace the GUI font (module on + thin font enabled).
    public static boolean thinFontActive() {
        Module module = ModuleRegistry.get(ID);
        return module instanceof BetterClickGuiModule m && module.isEnabled() && m.thinFont.get();
    }

    // Current thin-font pixel size (used to build/cache the Font).
    public static float fontSize() {
        Module module = ModuleRegistry.get(ID);
        if (module instanceof BetterClickGuiModule m) {
            return (float) m.fontSize.get();
        }
        return 9.0f;
    }

    @Override
    public void onEnable() {
        if (MC.level == null) {
            // Not in a world yet; apply anyway so the menu changes still show (theme is global).
        }
        capture();
        apply();
    }

    @Override
    public void onDisable() {
        restore();
    }

    @Override
    public void onGameLeft() {
        restore();
    }

    @Override
    protected void onOptionValueChanged(String settingId) {
        if (isEnabled() && settingId != null
            && (settingId.equals("style") || settingId.equals("accent")
            || settingId.equals("opacity") || settingId.equals("outline"))) {
            apply();
        }
    }

    private void capture() {
        AutismConfig config = AutismConfig.getGlobal();
        if (config == null || config.themeColors == null) return;
        saved = new int[]{
            config.themeColors.master, config.themeColors.accent, config.themeColors.outline,
            config.themeColors.text, config.themeColors.toggle, config.themeColors.backdrop,
            config.themeColors.success, config.themeColors.danger, config.themeColors.button,
            config.themeColors.header, config.themeColors.hover,
            config.themeColors.advanced ? 1 : 0
        };
    }

    private void restore() {
        AutismConfig config = AutismConfig.getGlobal();
        if (config == null || config.themeColors == null) return;
        if (saved.length == 12) {
            config.themeColors.master = saved[0];
            config.themeColors.accent = saved[1];
            config.themeColors.outline = saved[2];
            config.themeColors.text = saved[3];
            config.themeColors.toggle = saved[4];
            config.themeColors.backdrop = saved[5];
            config.themeColors.success = saved[6];
            config.themeColors.danger = saved[7];
            config.themeColors.button = saved[8];
            config.themeColors.header = saved[9];
            config.themeColors.hover = saved[10];
            config.themeColors.advanced = saved[11] == 1;
        }
        saved = new int[0];
        pushToGui();
    }

    private void apply() {
        AutismConfig config = AutismConfig.getGlobal();
        if (config == null || config.themeColors == null) return;

        int accent = this.accent.get();
        String styleName = style.get();
        int a = (int) Math.round(255.0 * Math.max(0.1, opacity.get() / 100.0));

        int backdrop = argb(a, 10, 14, 20);
        int panel = argb(255, 16, 20, 28);
        int header = argb(255, 9, 12, 18);
        int text = argb(255, 245, 248, 255);
        int outlineCol = outline.get() ? argb(255, 235, 245, 255) : argb(110, 235, 245, 255);
        int hover = argb(60, 120, 200, 255);

        switch (styleName) {
            case "Vape Light" -> {
                backdrop = argb(a, 236, 238, 242);
                panel = argb(255, 250, 251, 253);
                header = argb(255, 226, 230, 235);
                text = argb(255, 30, 34, 42);
                outlineCol = outline.get() ? argb(255, 84, 110, 122) : argb(120, 84, 110, 122);
                hover = argb(48, 0, 137, 123);
            }
            case "Ocean" -> {
                accent = argb(255, 0, 170, 220);
                backdrop = argb(a, 4, 18, 32);
                panel = argb(255, 8, 28, 46);
                header = argb(255, 5, 16, 28);
                outlineCol = outline.get() ? argb(255, 0, 170, 220) : argb(90, 0, 170, 220);
                hover = argb(60, 0, 170, 220);
            }
            case "Retro" -> {
                accent = argb(255, 255, 84, 230);
                backdrop = argb(a, 24, 10, 26);
                panel = argb(255, 34, 16, 38);
                header = argb(255, 22, 10, 26);
                outlineCol = outline.get() ? argb(255, 255, 84, 230) : argb(100, 255, 84, 230);
                hover = argb(60, 255, 84, 230);
            }
            default -> {
            }
        }

        AutismConfig.ThemeColors c = config.themeColors;
        c.advanced = true;
        c.master = accent;
        c.accent = accent;
        c.toggle = accent;
        c.outline = outlineCol;
        c.text = text;
        c.button = panel;
        c.header = header;
        c.backdrop = backdrop;
        c.hover = hover;
        c.success = argb(255, 46, 224, 109);
        c.danger = argb(255, 255, 77, 77);
        pushToGui();
    }

    private static void pushToGui() {
        try {
            AutismTheme.reload();
        } catch (Throwable ignored) {
        }
        try {
            UiContexts.refreshTheme();
        } catch (Throwable ignored) {
        }
    }

    private static int argb(int alpha, int r, int g, int b) {
        return (alpha << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }
}