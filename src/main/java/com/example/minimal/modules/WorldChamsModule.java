package com.example.minimal.modules;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.ColorSetting;
import autismclient.api.module.DoubleSetting;
import autismclient.api.module.IntSetting;
import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.modules.AutismAntiBot;
import autismclient.modules.Module;
import autismclient.modules.ModuleRegistry;
import autismclient.modules.PackHideState;
import autismclient.modules.TeamsModule;
import autismclient.util.AutismUiScale;
import com.example.minimal.Tier;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.Locale;

// Lightweight through-walls ESP drawn in the 2D overlay pass (same pass BetterNameTags uses).
// Since the overlay draws on top of everything, the ESP is always visible regardless of walls —
// no need to fight the depth buffer or inject into the entity renderer. It projects each entity's
// bounding box to screen space and draws: tracer lines from screen centre, 2D box outlines, and
// a name/health/distance tag inside the box.
public final class WorldChamsModule extends Module {

    public static final String ID = "autism-minimal-addon-template:world-chams";

    private final BoolSetting players = add(new BoolSetting("players", "Players", true)
        .group("Targets")
        .description("Show ESP for players."));
    private final BoolSetting mobs = add(new BoolSetting("mobs", "Mobs", true)
        .group("Targets")
        .description("Show ESP for hostile/other living mobs."));
    private final BoolSetting animals = add(new BoolSetting("animals", "Animals", false)
        .group("Targets")
        .description("Show ESP for passive animals."));
    private final BoolSetting tracers = add(new BoolSetting("tracers", "Tracers", true)
        .group("Display")
        .description("Draw lines from the crosshair to each entity."));
    private final BoolSetting boxes = add(new BoolSetting("boxes", "Boxes", true)
        .group("Display")
        .description("Draw 2D box outlines around entities."));
    private final BoolSetting info = add(new BoolSetting("info", "Info", true)
        .group("Display")
        .description("Draw name, health and distance inside the box."));
    private final ColorSetting boxColor = add(new ColorSetting("box-color", "Box Color", 0x66FF0000)
        .group("Display"));
    private final ColorSetting friendColor = add(new ColorSetting("friend-color", "Friend Color", 0x6600FF00)
        .group("Display")
        .description("Color for teammates and friends."));
    private final ColorSetting tracerColor = add(new ColorSetting("tracer-color", "Tracer Color", 0x88FF5555)
        .group("Display"));
    private final DoubleSetting maxDist = add(new DoubleSetting("max-distance", "Max Distance", 48.0, 8.0, 256.0, 1.0)
        .group("Display"));

    private int renderedEntities;
    private static final int RED = 0xFFFF1919;
    private static final int AMBER = 0xFFFF6919;
    private static final int GREEN = 0xFF19FC19;

    public WorldChamsModule() {
        super(ID, "World Chams", "Through-walls ESP: tracer lines, box outlines and info tags around entities.");
    }

    @Override
    public String info() {
        return Tier.CLOSET.label();
    }

    public static Tier tier() {
        return Tier.CLOSET;
    }

    public static boolean active() {
        Module module = ModuleRegistry.get(ID);
        return module != null && module.isEnabled();
    }

    private static WorldChamsModule self() {
        Module module = ModuleRegistry.get(ID);
        return module instanceof WorldChamsModule m ? m : null;
    }

    public static void render(GuiGraphicsExtractor ctx) {
        WorldChamsModule m = self();
        if (m == null || !m.isEnabled()) return;
        if (ctx == null || PackHideState.isActive()) return;
        if (MC == null || MC.level == null || MC.player == null) return;
        if (MC.gui == null || MC.gui.hud.isHidden()) return;
        Camera camera = MC.gameRenderer.mainCamera();
        if (camera == null) return;
        AutismUiScale.pushOverlayScale(ctx);
        try {
            m.renderInner(ctx, camera);
        } catch (Throwable ignored) {
        } finally {
            AutismUiScale.popOverlayScale(ctx);
        }
    }

    private void renderInner(GuiGraphicsExtractor ctx, Camera camera) {
        Vec3 camPos = camera.position();
        Matrix4f matrix = camera.getViewRotationProjectionMatrix(new Matrix4f());
        int screenW = AutismUiScale.getVirtualScreenWidth();
        int screenH = AutismUiScale.getVirtualScreenHeight();
        float tickDelta = MC.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        renderedEntities = 0;

        for (Entity entity : MC.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (entity == MC.player) continue;
            if (living.isSpectator()) continue;
            if (AutismAntiBot.isBot(entity)) continue;
            if (!matchesType(living)) continue;
            double distSq = entity.distanceToSqr(camPos);
            double maxD = maxDist.get();
            if (distSq > maxD * maxD) continue;
            // Limit to 64 entities to prevent FPS drops in crowded areas
            if (++renderedEntities > 64) break;

            float[] screen = projectHead(entity, tickDelta, camPos, matrix, screenW, screenH);
            float[] feet = projectFeet(entity, tickDelta, camPos, matrix, screenW, screenH);
            if (screen == null && feet == null) continue;

            boolean isFriend = living instanceof Player p && TeamsModule.isFriendOrTeam(p);
            int color = isFriend ? friendColor.get() : boxColor.get();

            if (tracers.get() && screen != null) {
                drawTracer(ctx, screenW / 2, screenH / 2, screen[0], screen[1], tracerColor.get());
            }

            if (boxes.get() && screen != null && feet != null) {
                drawBox(ctx, screen[0], screen[1], feet[0], feet[1], color);
            }

            if (info.get() && screen != null && feet != null) {
                drawInfo(ctx, living, screen[0], feet[1], isFriend);
            }
        }
    }

    private boolean matchesType(LivingEntity entity) {
        if (entity instanceof Player) return players.get();
        if (entity instanceof Animal) return animals.get();
        return mobs.get();
    }

    private static float[] projectHead(Entity entity, float tickDelta, Vec3 camPos,
                                       Matrix4f matrix, int sw, int sh) {
        return project(entity, tickDelta, camPos, entity.getDimensions(entity.getPose()).height() + 0.45,
            matrix, sw, sh);
    }

    private static float[] projectFeet(Entity entity, float tickDelta, Vec3 camPos,
                                       Matrix4f matrix, int sw, int sh) {
        return project(entity, tickDelta, camPos, 0.0, matrix, sw, sh);
    }

    private static float[] project(Entity entity, float tickDelta, Vec3 cam,
                                   double yOffset, Matrix4f matrix, int sw, int sh) {
        double x = Mth.lerp(tickDelta, entity.xOld, entity.getX());
        double y = Mth.lerp(tickDelta, entity.yOld, entity.getY()) + yOffset;
        double z = Mth.lerp(tickDelta, entity.zOld, entity.getZ());
        Vector4f v = new Vector4f((float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z), 1.0f);
        matrix.transform(v);
        if (v.w <= 0.001f) return null;
        float ndcX = v.x / v.w;
        float ndcY = v.y / v.w;
        if (Float.isNaN(ndcX) || Float.isNaN(ndcY) || Float.isInfinite(ndcX) || Float.isInfinite(ndcY)) return null;
        return new float[]{(ndcX * 0.5f + 0.5f) * sw, (0.5f - ndcY * 0.5f) * sh};
    }

    private static void drawTracer(GuiGraphicsExtractor ctx, float fromX, float fromY,
                                   float toX, float toY, int color) {
        int x0 = (int) Math.round(fromX);
        int y0 = (int) Math.round(fromY);
        int x1 = (int) Math.round(toX);
        int y1 = (int) Math.round(toY);
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        if (dx == 0 && dy == 0) return;
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        while (true) {
            UiRenderer.rect(ctx, UiBounds.of(x0, y0, 1, 1), color);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x0 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    private static void drawBox(GuiGraphicsExtractor ctx, float headX, float headY,
                                float feetX, float feetY, int color) {
        float left = Math.min(headX, feetX);
        float right = Math.max(headX, feetX);
        float top = headY;
        float bottom = feetY;
        float w = right - left + 4;
        float h = bottom - top + 2;
        float thickness = 1.5f;
        int boxColor = color & 0x00FFFFFF | 0x88000000; // keep RGB, set alpha
        // Background fill
        UiRenderer.rect(ctx, UiBounds.of((int) left, (int) top, (int) w, (int) h), boxColor);
        // Outline
        int outline = color | 0xFF000000;
        UiRenderer.rect(ctx, UiBounds.of((int) left, (int) top, (int) Math.max(1, thickness), (int) h), outline);
        UiRenderer.rect(ctx, UiBounds.of((int) right - (int) thickness, (int) top, (int) Math.max(1, thickness), (int) h), outline);
        UiRenderer.rect(ctx, UiBounds.of((int) left, (int) top, (int) w, (int) Math.max(1, thickness)), outline);
        UiRenderer.rect(ctx, UiBounds.of((int) left, (int) bottom - (int) thickness, (int) w, (int) Math.max(1, thickness)), outline);
    }

    private void drawInfo(GuiGraphicsExtractor ctx, LivingEntity living, float x, float bottom, boolean isFriend) {
        float y = bottom + 2;
        // Name
        String name = living.getName().getString();
        if (name.length() > 16) name = name.substring(0, 16);
        int nameCol = isFriend ? 0xFF00FF00 : 0xFFFFFFFF;
        ctx.text(MC.font, Component.literal(name).getVisualOrderText(), (int) x - MC.font.width(name) / 2, (int) y, nameCol, true);
        y += LINE_H;

        // Health
        float hp = living.getHealth() + living.getAbsorptionAmount();
        float maxHp = living.getMaxHealth();
        int col = hp < maxHp * 0.33f ? RED : hp < maxHp * 0.66f ? AMBER : GREEN;
        String hpStr = String.format(Locale.ROOT, "%.1f", hp);
        ctx.text(MC.font, Component.literal(hpStr).getVisualOrderText(), (int) x - MC.font.width(hpStr) / 2, (int) y, col, true);
        y += LINE_H;

        // Distance
        double dist = Math.sqrt(living.distanceToSqr(MC.player));
        String distStr = (int) Math.round(dist) + "m";
        ctx.text(MC.font, Component.literal(distStr).getVisualOrderText(), (int) x - MC.font.width(distStr) / 2, (int) y, 0xFFAAAAAA, true);
    }

    private static final int LINE_H = 10;
}
