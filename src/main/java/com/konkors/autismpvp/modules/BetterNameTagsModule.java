package com.konkors.autismpvp.modules;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.ColorSetting;
import autismclient.api.module.DoubleSetting;
import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.modules.AutismAntiBot;
import autismclient.modules.Module;
import autismclient.modules.ModuleRegistry;
import autismclient.modules.PackHideState;
import autismclient.modules.TeamsModule;
import autismclient.util.AutismUiScale;
import com.konkors.autismpvp.Tier;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

// Meteor-style nametags (port of Meteor Client's render.Nametags) drawn on top of the client's own
// 2D overlay pass. Meteor renders player name + colour, health (coloured by threshold), ping,
// distance and game mode, plus item names/counts, inside a translucent box above the entity's head.
//
// This is a pure addon renderer: it does NOT touch the client's built-in "Panic Mode" and does NOT
// need the host's "nametags" module. Enabling this module turns the host one off so you never get
// both renderers drawing at once, and two addon mixins feed it:
//   - HudNametagsMixin        renders our tags in the HUD overlay pass,
//   - EntityNameTagSuppressMixin clears vanilla nameTag so the stock black tag never double-draws.
public final class BetterNameTagsModule extends Module {

    public static final String ID = "autismpvp:better-nametags";

    private final BoolSetting players = add(new BoolSetting("players", "Players", true)
        .group("General")
        .description("Show player nametags."));
    private final BoolSetting mobs = add(new BoolSetting("mobs", "Mobs", true)
        .group("General")
        .description("Show nametags for other living entities."));
    private final BoolSetting items = add(new BoolSetting("items", "Items", false)
        .group("General")
        .description("Show dropped item names."));
    private final BoolSetting showCount = add(new BoolSetting("item-count", "Item Count", true)
        .group("General")
        .visibleWhen(() -> items.get())
        .description("Show the stack count on item nametags."));
    private final BoolSetting ignoreSelf = add(new BoolSetting("ignore-self", "Ignore Self", true)
        .group("General")
        .description("Don't draw a nametag over yourself in third person."));
    private final BoolSetting displayArmor = add(new BoolSetting("armor", "Armor", true)
        .group("General")
        .description("Show equipped armor pieces and their durability percentage."));
    private final BoolSetting displayHeldItems = add(new BoolSetting("held-items", "Held Items", true)
        .group("General")
        .description("Show what the entity is holding in main hand and offhand."));
    private final DoubleSetting scaleSetting = add(new DoubleSetting("scale", "Scale", 1.0, 0.2, 3.0, 0.1)
        .group("General")
        .description("Size of the nametag text."));
    private final DoubleSetting maxDistance = add(new DoubleSetting("max-distance", "Max Distance", 64.0, 8.0, 256.0, 1.0)
        .group("General")
        .description("Only tag entities within this distance."));

    private final BoolSetting displayHealth = add(new BoolSetting("health", "Health", true)
        .group("Players")
        .description("Show health, coloured green/amber/red by how full it is."));
    private final BoolSetting displayPing = add(new BoolSetting("ping", "Ping", true)
        .group("Players")
        .description("Show the player's ping."));
    private final BoolSetting displayDistance = add(new BoolSetting("distance", "Distance", true)
        .group("Players")
        .description("Show the distance to the player."));
    private final BoolSetting displayGameMode = add(new BoolSetting("gamemode", "Game Mode", false)
        .group("Players")
        .description("Show the player's game mode (S/C/A/Sp, or BOT for fake players)."));

    private final ColorSetting nameColor = add(new ColorSetting("name-color", "Name Color", 0xFFFFFFFF)
        .group("Render")
        .description("Color of entity names."));
    private final ColorSetting pingColor = add(new ColorSetting("ping-color", "Ping Color", 0xFF14AAAA)
        .group("Render")
        .description("Color of the ping text."));
    private final ColorSetting gamemodeColor = add(new ColorSetting("gamemode-color", "Game Mode Color", 0xFFE8B923)
        .group("Render")
        .description("Color of the game mode tag."));
    private final ColorSetting distanceColor = add(new ColorSetting("distance-color", "Distance Color", 0xFF969696)
        .group("Render")
        .description("Color of the distance text."));
    private final ColorSetting backgroundColor = add(new ColorSetting("background-color", "Background Color", 0x4B000000)
        .group("Render")
        .description("Color behind the nametag text."));
    private final ColorSetting armorColor = add(new ColorSetting("armor-color", "Armor Color", 0xFFAAAAAA)
        .group("Render")
        .description("Color of the armor text."));

    private static final int RED = 0xFFFF1919;
    private static final int AMBER = 0xFFFF6919;
    private static final int GREEN = 0xFF19FC19;
    private static final int GOLD = 0xFFE8B923;
    private static final int LINE_H = 10;

    public BetterNameTagsModule() {
        super(ID, "BetterNameTags",
            "Meteor-style nametags: player names, health, ping, distance and game mode, plus dropped item names, above living entities. Replaces the stock black tag.");
    }

    @Override
    public String info() {
        return tier().label();
    }

    public static Tier tier() {
        return Tier.LEGIT;
    }

    public static boolean active() {
        Module module = ModuleRegistry.get(ID);
        return module != null && module.isEnabled();
    }

    private static BetterNameTagsModule self() {
        Module module = ModuleRegistry.get(ID);
        return module instanceof BetterNameTagsModule m ? m : null;
    }

    @Override
    public void onEnable() {
        // Turn the client's own nametags module off so both renderers never draw at the same time.
        Module hostTags = ModuleRegistry.get("nametags");
        if (hostTags != null && hostTags.isEnabled()) {
            hostTags.setEnabled(false);
            hostTagsForcedOff = true;
        }
    }

    @Override
    public void onDisable() {
        if (hostTagsForcedOff) {
            hostTagsForcedOff = false;
            Module hostTags = ModuleRegistry.get("nametags");
            if (hostTags != null && !hostTags.isEnabled()) hostTags.setEnabled(true);
        }
    }

    private static boolean hostTagsForcedOff;

    // True when this module owns an entity's tag: enables the vanilla suppression mixin AND draws it.
    public static boolean tags(Entity entity) {
        BetterNameTagsModule m = self();
        if (m == null || !m.isEnabled()) return false;
        if (PackHideState.isActive()) return false;
        if (MC == null || MC.player == null || MC.level == null || entity == null) return false;
        if (entity == MC.player) return !m.ignoreSelf.get();
        if (entity.isSpectator()) return false;
        if (AutismAntiBot.isBot(entity)) return false;
        if (!m.matchesTargetType(entity)) return false;
        double maxDist = m.maxDistance.get();
        if (maxDist > 0) {
            Camera cam = MC.gameRenderer.mainCamera();
            if (cam == null) return false;
            double distSq = entity.distanceToSqr(cam.position());
            if (distSq > maxDist * maxDist) return false;
        }
        return true;
    }

    private boolean matchesTargetType(Entity entity) {
        if (entity instanceof ItemEntity) return items.get();
        if (entity instanceof Player) return players.get();
        return entity instanceof LivingEntity && mobs.get();
    }

    // Called from HudNametagsMixin (Hud.extractRenderState TAIL), in the same overlay-scaled pass the
    // client's own overlay UI uses so screen-space coordinates line up.
    public static void render(GuiGraphicsExtractor ctx) {
        BetterNameTagsModule m = self();
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
            // Never break the HUD overlay pass.
        } finally {
            AutismUiScale.popOverlayScale(ctx);
        }
    }

    private void renderInner(GuiGraphicsExtractor ctx, Camera camera) {
        float tickDelta = MC.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camPos = camera.position();
        Projection projection = new Projection(
            camPos,
            camera.getViewRotationProjectionMatrix(new Matrix4f()),
            AutismUiScale.getVirtualScreenWidth(),
            AutismUiScale.getVirtualScreenHeight()
        );
        for (Entity entity : MC.level.entitiesForRendering()) {
            if (!tags(entity)) continue;
            float[] screen = projectHead(entity, tickDelta, projection);
            if (screen == null) continue;
            List<Seg> segments = buildSegments(entity, camPos);
            if (segments.isEmpty()) continue;
            drawLabel(ctx, screen[0], screen[1], segments, scaleSetting.get().floatValue());
        }
    }

    private List<Seg> buildSegments(Entity entity, Vec3 camPos) {
        List<Seg> segments = new ArrayList<>();
        if (entity instanceof ItemEntity item) {
            if (!items.get()) return segments;
            ItemStack stack = item.getItem();
            if (stack.isEmpty()) return segments;
            segments.add(seg(stack.getHoverName().getString(), nameColor.get()));
            if (showCount.get()) segments.add(seg(" x" + stack.getCount(), GOLD));
            return segments;
        }
        if (!(entity instanceof LivingEntity living)) return segments;
        if (living instanceof Player player) {
            if (displayGameMode.get()) segments.add(seg("[" + gameMode(player) + "] ", gamemodeColor.get()));
            segments.add(seg(entityName(living), TeamsModule.isFriendOrTeam(player)
                ? TeamsModule.friendsColor() : nameColor.get()));
            if (displayHealth.get()) {
                segments.add(seg(" " + healthValue(living) + "HP", healthColor(living)));
            }
            if (displayPing.get()) segments.add(seg(" [" + ping(player) + "ms]", pingColor.get()));
            if (displayDistance.get()) {
                double dist = camPos == null ? 0 : Math.sqrt(entity.distanceToSqr(camPos));
                segments.add(seg(" " + (int) Math.round(dist) + "m", distanceColor.get()));
            }
            if (displayArmor.get()) addArmorSegments(segments, living);
            if (displayHeldItems.get()) addHeldItemSegments(segments, living);
        } else {
            segments.add(seg(entityName(living), nameColor.get()));
            if (displayHealth.get()) {
                segments.add(seg(" " + healthValue(living) + "HP", healthColor(living)));
            }
            if (displayArmor.get()) addArmorSegments(segments, living);
            if (displayHeldItems.get()) addHeldItemSegments(segments, living);
        }
        return segments;
    }

    private static String entityName(Entity entity) {
        Component display = entity.getDisplayName();
        return display != null ? display.getString() : entity.getName().getString();
    }

    private static String gameMode(Player player) {
        PlayerInfo info = mcPlayerInfo(player);
        GameType gm = info == null ? null : info.getGameMode();
        if (gm == null) return "BOT";
        return switch (gm) {
            case SPECTATOR -> "Sp";
            case SURVIVAL -> "S";
            case CREATIVE -> "C";
            case ADVENTURE -> "A";
        };
    }

    private static int ping(Player player) {
        PlayerInfo info = mcPlayerInfo(player);
        if (info == null) return 0;
        int latency = info.getLatency();
        return Math.max(0, latency);
    }

    private static PlayerInfo mcPlayerInfo(Player player) {
        if (player == null || MC.getConnection() == null) return null;
        return MC.getConnection().getPlayerInfo(player.getUUID());
    }

    private static int healthValue(LivingEntity entity) {
        return Math.round(entity.getHealth() + entity.getAbsorptionAmount());
    }

    private static int healthColor(LivingEntity entity) {
        double health = entity.getHealth() + entity.getAbsorptionAmount();
        double max = entity.getMaxHealth() + entity.getAbsorptionAmount();
        double pct = max <= 0 ? 1.0 : health / max;
        if (pct <= 0.333) return RED;
        if (pct <= 0.666) return AMBER;
        return GREEN;
    }

    private static final EquipmentSlot[] ARMOR_SLOTS = {
        EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD
    };

    private static void addArmorSegments(List<Seg> segments, LivingEntity living) {
        BetterNameTagsModule m = self();
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = living.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            int maxDmg = stack.getMaxDamage();
            if (maxDmg > 0) {
                int dmg = stack.getDamageValue();
                int pct = (int) Math.round((1.0 - (double) dmg / maxDmg) * 100);
                // Color code: green > 75%, yellow > 25%, red otherwise
                int color = pct > 75 ? GREEN : pct > 25 ? AMBER : RED;
                segments.add(Seg.item(stack, color, String.valueOf(pct)));
            } else {
                segments.add(Seg.item(stack, 0xFFAAAAAA, null));
            }
        }
    }

    private static void addHeldItemSegments(List<Seg> segments, LivingEntity living) {
        BetterNameTagsModule m = self();
        int color = m != null ? m.armorColor.get() : 0xFFAAAAAA;
        ItemStack main = living.getMainHandItem();
        ItemStack off = living.getOffhandItem();
        if (!main.isEmpty()) {
            String overlay = main.getCount() > 1 ? String.valueOf(main.getCount()) : null;
            segments.add(seg("Main: ", color));
            segments.add(Seg.item(main, color, overlay));
        }
        if (!off.isEmpty()) {
            String overlay = off.getCount() > 1 ? String.valueOf(off.getCount()) : null;
            segments.add(seg(" Off: ", color));
            segments.add(Seg.item(off, color, overlay));
        }
    }

    private static Seg seg(String text, int color) {
        return Seg.text(text, color);
    }

    private static void drawLabel(GuiGraphicsExtractor ctx, float screenX, float screenY, List<Seg> segments, float scale) {
        if (segments.isEmpty()) return;
        ctx.pose().pushMatrix();
        ctx.pose().scale(scale, scale);
        int ox = Math.round(screenX / scale);
        int oy = Math.round(screenY / scale);
        
        // Calculate total width including item icons
        int width = 0;
        for (Seg s : segments) {
            if (s.item != null) {
                width += 16; // item icon width
            } else {
                width += MC.font.width(s.text());
            }
        }
        
        int top = oy - LINE_H - 2;
        BetterNameTagsModule m = self();
        int bg = m != null ? m.backgroundColor.get() : 0x4B000000;
        UiRenderer.rect(ctx, UiBounds.of(ox - width / 2 - 2, top, width + 4, LINE_H + 2), bg);
        int tx = ox - width / 2;
        for (Seg s : segments) {
            if (s.item != null) {
                renderItem(ctx, s.item, tx, top + 1, s.durabilityOverlay);
                tx += 16;
            } else {
                ctx.text(MC.font, s.text(), tx, top + 1, s.color(), true);
                tx += MC.font.width(s.text());
            }
        }
        ctx.pose().popMatrix();
    }
    
    private static void renderItem(GuiGraphicsExtractor ctx, ItemStack stack, int x, int y, String overlay) {
        ctx.item(stack, x, y);
        ctx.itemDecorations(MC.font, stack, x, y, overlay);
    }

    private static float[] projectHead(Entity entity, float tickDelta, Projection p) {
        double x = Mth.lerp(tickDelta, entity.xOld, entity.getX());
        double y = Mth.lerp(tickDelta, entity.yOld, entity.getY()) + entity.getDimensions(entity.getPose()).height() + 0.45;
        double z = Mth.lerp(tickDelta, entity.zOld, entity.getZ());
        return project(x, y, z, p);
    }

    private static float[] project(double worldX, double worldY, double worldZ, Projection p) {
        Vec3 cam = p.cameraPosition();
        Vector4f v = new Vector4f((float) (worldX - cam.x), (float) (worldY - cam.y), (float) (worldZ - cam.z), 1.0f);
        p.matrix().transform(v);
        if (v.w <= 0.001f) return null;
        float ndcX = v.x / v.w;
        float ndcY = v.y / v.w;
        if (Float.isNaN(ndcX) || Float.isNaN(ndcY) || Float.isInfinite(ndcX) || Float.isInfinite(ndcY)) return null;
        float sx = (ndcX * 0.5f + 0.5f) * p.screenWidth();
        float sy = (0.5f - ndcY * 0.5f) * p.screenHeight();
        return new float[]{sx, sy};
    }

    private record Seg(ItemStack item, FormattedCharSequence text, int color, String durabilityOverlay) {
        static Seg text(String text, int color) {
            return new Seg(null, Component.literal(text).getVisualOrderText(), color, null);
        }
        static Seg item(ItemStack stack, int color, String durabilityOverlay) {
            return new Seg(stack, FormattedCharSequence.EMPTY, color, durabilityOverlay);
        }
    }

    private record Projection(Vec3 cameraPosition, Matrix4f matrix, int screenWidth, int screenHeight) {
    }
}
