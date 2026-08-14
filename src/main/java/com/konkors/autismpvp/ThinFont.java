package com.konkors.autismpvp;

import autismclient.gui.vanillaui.UiTextRenderer;
import com.mojang.blaze3d.font.GlyphProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.GlyphStitcher;
import net.minecraft.client.gui.font.glyphs.EffectGlyph;
import net.minecraft.client.gui.font.providers.TrueTypeGlyphProviderDefinition;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Set;

// Loads a thin TTF (shipped in the addon under assets/autismpvp/fonts/thin.ttf) as a normal
// Minecraft Font at runtime, without a resource reload or a resource-pack font definition. It wires
// the existing public pieces together: a GlyphStitcher (needs only the TextureManager) + a FontSet,
// then feeds the TrueType glyph provider into it and builds a vanilla Font on top. The Font's
// Provider closure keeps the FontSet + stitcher alive for as long as the Font is referenced, and the
// whole thing is cached per font size. Fully fault-tolerant: any failure just returns null and the
// caller keeps the vanilla font, so a bad load can never crash or freeze the menu.
public final class ThinFont {

    private static final Identifier FONT_LOCATION = new Identifier("autismpvp", "fonts/thin");
    private static final Identifier STITCH_TEXTURE = new Identifier("autismpvp", "thin-texture");
    private static final float MIN_SIZE = 7.0f;
    private static final float MAX_SIZE = 14.0f;
    private static final float OVER_SAMPLE = 2.0f;

    private static UiTextRenderer renderer;
    private static float buildSize = -1f;

    private ThinFont() {
    }

    public static UiTextRenderer renderer(float size) {
        float s = Math.max(MIN_SIZE, Math.min(MAX_SIZE, size));
        Font f = build(s);
        if (f == null) {
            return null;
        }
        if (renderer == null || Math.abs(buildSize - s) >= 0.05f) {
            renderer = new UiTextRenderer(f);
            buildSize = s;
        }
        return renderer;
    }

    private static Font build(float size) {
        Minecraft mc = Minecraft.getInstance();
        GlyphStitcher stitcher = new GlyphStitcher(mc.getTextureManager(), STITCH_TEXTURE);
        FontSet set = new FontSet(stitcher);
        try {
            TrueTypeGlyphProviderDefinition definition = new TrueTypeGlyphProviderDefinition(
                FONT_LOCATION, size, OVER_SAMPLE, TrueTypeGlyphProviderDefinition.Shift.NONE, "");

            GlyphProvider provider;
            var unpacked = definition.unpack();
            if (unpacked.left().isPresent()) {
                provider = unpacked.left().get().load(mc.getResourceManager());
            } else {
                throw new IllegalStateException("thin font unpacked a reference, not a loadable provider");
            }

            set.reload(
                List.of(new GlyphProvider.Conditional(provider, FontOption.Filter.ALWAYS_PASS)),
                Set.of());

            return new Font(new Font.Provider() {
                @Override
                public GlyphSource glyphs(FontDescription description) {
                    return set.source(true);
                }

                @Override
                public EffectGlyph effect() {
                    return set.whiteGlyph();
                }
            });
        } catch (Throwable t) {
            try {
                set.close();
            } catch (Throwable ignored) {
            }
            try {
                stitcher.close();
            } catch (Throwable ignored) {
            }
            return null;
        }
    }
}
