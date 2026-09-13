package com.tankscraft.client.garage;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tankscraft.client.render.TankModels;
import com.tankscraft.tank.TankDefinition;
import com.tankscraft.tank.Tanks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.gui.ModListScreen;

import java.util.List;
import java.util.Locale;

/**
 * Le hangar : remplace le menu principal de Minecraft.
 * façon World of Tanks — char 3D au centre, carrousel en bas,
 * fiche technique à gauche, bouton "Au combat !" à droite.
 */
@OnlyIn(Dist.CLIENT)
public class GarageScreen extends Screen {
    private static final ResourceLocation HANGAR =
            ResourceLocation.fromNamespaceAndPath("tankscraft", "textures/gui/hangar.png");
    private static final ResourceLocation LOGO =
            ResourceLocation.fromNamespaceAndPath("tankscraft", "textures/gui/logo.png");

    private static final int CARD_W = 96;
    private static final int CARD_H = 58;
    private static final int CARD_GAP = 6;
    private static final int CAROUSEL_Y_BOTTOM = 80;

    // palette
    private static final int PANEL_BG = 0xD00F1214;
    private static final int PANEL_BG_DARK = 0xE0121416;
    private static final int PANEL_BORDER = 0xFF3A4038;
    private static final int GOLD = 0xFFD9A441;
    private static final int TEXT_MAIN = 0xFFE8E4D8;
    private static final int TEXT_DIM = 0xFF9AA08E;
    private static final int BATTLE_RED = 0xFFB3261E;
    private static final int BATTLE_ORANGE = 0xFFE8590C;
    private static final int READY_GREEN = 0xFF7DC95E;

    private static final List<TankDefinition> TANKS = Tanks.ALL;

    private final ItemStack goldIcon = new ItemStack(Items.GOLD_INGOT);
    private final ItemStack creditIcon = new ItemStack(Items.EMERALD);
    private final ItemStack xpIcon = new ItemStack(Items.EXPERIENCE_BOTTLE);

    private TankDefinition selected;
    private int carouselScroll = 0;

    // caméra
    private float targetYaw = -28.0F;
    private float renderYaw = -28.0F;
    private float targetPitch = 9.0F;
    private float renderPitch = 9.0F;
    private float targetZoom = 1.0F;
    private float renderZoom = 1.0F;

    private boolean dragging = false;
    private double lastMouseX, lastMouseY;

    /** Capture automatique du garage (CI) : -Dtankscraft.autoshot=true */
    private static final boolean AUTOSHOT = Boolean.getBoolean("tankscraft.autoshot");
    private int autoshotCountdown = AUTOSHOT ? 60 : -1;

    public GarageScreen() {
        super(Component.translatable("screen.tankscraft.garage"));
    }

    @Override
    protected void init() {
        super.init();
        this.selected = Tanks.byId(GarageState.get().selectedTank);
        this.clampCarouselScroll();
        this.ensureSelectedVisible();

        int bw = 200;
        int bx = this.width - bw - 16;
        int by = this.height - 188;

        this.addRenderableWidget(new BattleButton(bx, by, bw, 44,
                Component.translatable("gui.tankscraft.to_battle"),
                b -> this.minecraft.setScreen(new SelectWorldScreen(this))));

        this.addRenderableWidget(Button.builder(Component.translatable("gui.tankscraft.multiplayer"),
                        b -> this.minecraft.setScreen(new JoinMultiplayerScreen(this)))
                .bounds(bx, by + 52, bw, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.tankscraft.options"),
                        b -> this.minecraft.setScreen(new OptionsScreen(this, this.minecraft.options)))
                .bounds(bx, by + 76, bw, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.tankscraft.mods"),
                        b -> this.minecraft.setScreen(new ModListScreen(this)))
                .bounds(bx, by + 100, bw, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("menu.quit"),
                        b -> this.minecraft.stop())
                .bounds(bx, by + 124, bw, 20).build());

        // flèches du carrousel
        int carouselY = this.carouselY();
        this.addRenderableWidget(Button.builder(Component.literal("<"), b -> this.scrollCarousel(-1))
                .bounds(8, carouselY + CARD_H / 2 - 10, 20, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal(">"), b -> this.scrollCarousel(1))
                .bounds(this.carouselRight() + 4, carouselY + CARD_H / 2 - 10, 20, 20).build());
    }

    // ------------------------------------------------------------ rendu

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderHangarBackground(g);
        this.renderTankPreview(g, partialTick);

        // L'UI passe devant le char (z supérieur)
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(0.0F, 0.0F, 400.0F);

        this.renderLogo(g);
        this.renderTopBar(g);
        this.renderLeftPanel(g);
        this.renderCarousel(g, mouseX, mouseY);
        this.renderFooter(g);

        super.render(g, mouseX, mouseY, partialTick);

        pose.popPose();

        if (this.autoshotCountdown >= 0 && this.autoshotCountdown-- == 0) {
            this.captureAutoshot();
        }
    }

    /** Screenshot du garage puis fermeture du jeu (utilisé par la CI). */
    private void captureAutoshot() {
        try {
            net.minecraft.client.Screenshot.takeScreenshot(this.minecraft.getMainRenderTarget())
                    .writeFile(new java.io.File(System.getProperty("tankscraft.autoshot.path", "autoshot.png")));
        } catch (Exception ignored) {
        }
        this.minecraft.stop();
    }

    private void renderHangarBackground(GuiGraphics g) {
        // étirement plein écran (l'image est en ~16:9, comme la plupart des écrans)
        g.blit(HANGAR, 0, 0, this.width, this.height, 0.0F, 0.0F, 1376, 768, 1376, 768);
        // léger assombrissement en bas pour asseoir l'UI
        g.fillGradient(0, this.height - 140, this.width, this.height, 0x00000000, 0x99000000);
    }

    private void renderTankPreview(GuiGraphics g, float partialTick) {
        // caméra lissée
        this.renderYaw += Mth.wrapDegrees(this.targetYaw - this.renderYaw) * 0.25F;
        this.renderPitch += (this.targetPitch - this.renderPitch) * 0.25F;
        this.renderZoom += (this.targetZoom - this.renderZoom) * 0.25F;

        int anchorX = (int) (this.width * 0.44F);
        int anchorY = this.height - 92;

        // ombre au sol
        float shadow = 0.55F * this.renderZoom;
        int halfW = (int) (60 * shadow);
        g.fill(anchorX - halfW, anchorY - 2, anchorX + halfW, anchorY + 2, 0x40000000);
        g.fill(anchorX - (int) (halfW * 0.8F), anchorY - 1, anchorX + (int) (halfW * 0.8F), anchorY + 1, 0x40000000);

        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(anchorX, anchorY, 200.0F);

        float pixelsPerBlock = (this.height * 0.5F * this.renderZoom) / 1.75F;
        pose.scale(pixelsPerBlock, pixelsPerBlock, pixelsPerBlock);

        // orbite autour du centre du châssis
        pose.translate(0.0F, 0.8F, 0.0F);
        pose.mulPose(Axis.XP.rotationDegrees(this.renderPitch));
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - this.renderYaw));
        pose.translate(0.0F, -0.8F, 0.0F);

        // compression de la profondeur : invisible en projection ortho,
        // mais borne le char sous le plan de l'UI (z=400) quel que soit le zoom
        pose.scale(1.0F, 1.0F, 0.3F);

        // passage en espace "modèle entité" (cf. LivingEntityRenderer)
        pose.scale(-1.0F, -1.0F, 1.0F);
        pose.translate(0.0F, -1.501F, 0.0F);

        ModelPart model = TankModels.bake(this.selected);
        float time = (net.minecraft.Util.getMillis() % 100000L) / 1000.0F;
        float turretYaw = Mth.sin(time * 0.6F) * 20.0F;
        float gunPitch = 3.0F + Mth.sin(time * 0.4F) * 2.0F;
        TankModels.poseParts(model, turretYaw, gunPitch);

        Lighting.setupForEntityInInventory();
        MultiBufferSource.BufferSource buffers = this.minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(this.selected.texture()));
        model.render(pose, consumer, 0xF000F0, OverlayTexture.NO_OVERLAY);
        buffers.endBatch();
        Lighting.setupFor3DItems();

        pose.popPose();
    }

    private void renderLogo(GuiGraphics g) {
        int logoH = 56;
        int logoW = logoH * 4; // l'image fait ~4:1
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        g.blit(LOGO, this.width / 2 - logoW / 2, 6, logoW, logoH, 0.0F, 0.0F, 1024, 256, 1024, 256);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private void renderTopBar(GuiGraphics g) {
        GarageState state = GarageState.get();
        Minecraft mc = this.minecraft;

        // joueur, en haut à gauche
        g.drawString(this.font, Component.translatable("gui.tankscraft.commander"), 10, 8, TEXT_DIM, true);
        String playerName = mc != null ? mc.getUser().getName() : "Commandant";
        g.drawString(this.font, playerName, 10, 19, TEXT_MAIN, true);

        // devises, en haut à droite
        int x = this.width - 10;
        x = drawCurrency(g, x, format(state.credits), this.creditIcon);
        x = drawCurrency(g, x, format(state.gold), this.goldIcon);
        drawCurrency(g, x, format(state.freeXp), this.xpIcon);
    }

    /** Dessine une devise de droite à gauche ; renvoie le x suivant. */
    private int drawCurrency(GuiGraphics g, int rightX, String value, ItemStack icon) {
        int textW = this.font.width(value);
        int w = textW + 20;
        g.fill(rightX - w - 6, 6, rightX, 26, PANEL_BG);
        g.renderItem(icon, rightX - w - 2, 7);
        g.drawString(this.font, value, rightX - textW - 2, 12, GOLD, true);
        return rightX - w - 12;
    }

    private void renderLeftPanel(GuiGraphics g) {
        int x = 10;
        int y = 56;
        int w = 172;
        int h = 190;
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, PANEL_BORDER);
        g.fill(x, y, x + w, y + h, PANEL_BG);

        // en-tête : nation + nom
        TankDefinition def = this.selected;
        g.fill(x + 6, y + 6, x + 16, y + 16, def.nation().color);
        g.fill(x + 6, y + 6, x + 16, y + 7, def.nation().accent);
        g.drawString(this.font, TankDefinition.roman(def.tier()), x + 21, y + 7, GOLD, true);
        String name = this.font.plainSubstrByWidth(def.name().getString(), w - 30);
        g.drawString(this.font, name, x + 6, y + 21, TEXT_MAIN, true);
        g.drawString(this.font, def.className().getString() + " • " + def.nation().displayName().getString(),
                x + 6, y + 31, TEXT_DIM, true);

        // barres de stats
        int sy = y + 48;
        sy = drawStatBar(g, x + 6, sy, w - 12, "gui.tankscraft.stat.firepower", def.firepower());
        sy = drawStatBar(g, x + 6, sy, w - 12, "gui.tankscraft.stat.mobility", def.mobility());
        sy = drawStatBar(g, x + 6, sy, w - 12, "gui.tankscraft.stat.armor", def.armor());
        sy = drawStatBar(g, x + 6, sy, w - 12, "gui.tankscraft.stat.camo", def.camo());
        sy = drawStatBar(g, x + 6, sy, w - 12, "gui.tankscraft.stat.view", def.viewRange());

        // chiffres clés
        int ny = sy + 6;
        g.fill(x + 6, ny - 4, x + w - 6, ny - 3, PANEL_BORDER);
        drawKeyValue(g, x + 6, ny + 2, w - 12, Component.translatable("gui.tankscraft.stat.hp"), def.hp() + " PDV");
        drawKeyValue(g, x + 6, ny + 13, w - 12, Component.translatable("gui.tankscraft.stat.damage"), def.damage() + "");
        drawKeyValue(g, x + 6, ny + 24, w - 12, Component.translatable("gui.tankscraft.stat.speed"), def.topSpeedKmh() + " km/h");
        drawKeyValue(g, x + 6, ny + 35, w - 12, Component.translatable("gui.tankscraft.stat.reload"),
                String.format(Locale.ROOT, "%.1f s", def.reloadTicks() / 20.0F));
    }

    private int drawStatBar(GuiGraphics g, int x, int y, int w, String labelKey, int value) {
        g.drawString(this.font, Component.translatable(labelKey), x, y, TEXT_DIM, true);
        String num = String.valueOf(value);
        g.drawString(this.font, num, x + w - this.font.width(num), y, statColor(value), true);

        int barY = y + 10;
        int segments = 5;
        int segW = (w - (segments - 1) * 2) / segments;
        int filled = Mth.clamp(value, 0, 100) / 20;
        int partial = Mth.clamp(value % 20, 0, 20);
        int color = statColor(value);
        for (int i = 0; i < segments; i++) {
            int sx = x + i * (segW + 2);
            g.fill(sx, barY, sx + segW, barY + 4, 0xFF26292C);
            if (i < filled) {
                g.fill(sx, barY, sx + segW, barY + 4, color);
            } else if (i == filled && partial > 0) {
                int pw = Math.max(1, segW * partial / 20);
                g.fill(sx, barY, sx + pw, barY + 4, color);
            }
        }
        return barY + 12;
    }

    private static int statColor(int value) {
        if (value < 34) {
            return 0xFFC9503F;
        }
        return value < 67 ? 0xFFD9973F : READY_GREEN;
    }

    private void drawKeyValue(GuiGraphics g, int x, int y, int w, Component key, String value) {
        g.drawString(this.font, key, x, y, TEXT_DIM, true);
        g.drawString(this.font, value, x + w - this.font.width(value), y, TEXT_MAIN, true);
    }

    // ------------------------------------------------------------ carrousel

    private int carouselY() {
        return this.height - CAROUSEL_Y_BOTTOM;
    }

    private int carouselRight() {
        return this.width - 240;
    }

    private void renderCarousel(GuiGraphics g, int mouseX, int mouseY) {
        int y = this.carouselY();
        int left = 34;
        int right = this.carouselRight();

        g.fill(0, y - 6, this.width, this.height, 0x80100F0E);

        for (int i = 0; i < TANKS.size(); i++) {
            TankDefinition def = TANKS.get(i);
            int cx = left + (i - this.carouselScroll) * (CARD_W + CARD_GAP);
            if (cx + CARD_W < left - 4 || cx > right + 4) {
                continue;
            }
            boolean isSelected = def == this.selected;
            boolean hovered = mouseX >= cx && mouseX < cx + CARD_W && mouseY >= y && mouseY < y + CARD_H;

            int border = isSelected ? GOLD : (hovered ? 0xFF6B7268 : PANEL_BORDER);
            g.fill(cx - 1, y - 1, cx + CARD_W + 1, y + CARD_H + 1, border);
            g.fill(cx, y, cx + CARD_W, y + CARD_H, isSelected ? 0xE01C2620 : PANEL_BG_DARK);

            // tier
            g.drawString(this.font, TankDefinition.roman(def.tier()), cx + 5, y + 4, GOLD, true);
            // classe
            int badgeW = 22;
            g.fill(cx + CARD_W - badgeW - 5, y + 4, cx + CARD_W - 5, y + 14, def.tankClass().color | 0xFF000000);
            String cls = def.tankClass().shortLabel();
            g.drawCenteredString(this.font, cls, cx + CARD_W - badgeW / 2 - 5, y + 5, 0xFF101210);

            // bande de nation
            g.fill(cx + 5, y + 17, cx + 9, y + 21, def.nation().color);
            g.fill(cx + 5, y + 17, cx + 6, y + 21, def.nation().accent);

            // nom
            String name = this.font.plainSubstrByWidth(def.name().getString(), CARD_W - 10);
            g.drawString(this.font, name, cx + 5, y + CARD_H - 20, isSelected ? TEXT_MAIN : TEXT_DIM, true);
            // vitesse
            g.drawString(this.font, def.topSpeedKmh() + " km/h", cx + 5, y + CARD_H - 10, TEXT_DIM, true);

            if (isSelected) {
                g.fill(cx, y - 4, cx + CARD_W, y - 2, GOLD);
            }
        }
    }

    private void renderFooter(GuiGraphics g) {
        g.drawString(this.font, "TanksCraft 0.1.0 — Minecraft 1.21.1 (NeoForge)", 8, this.height - 10, 0x80605E58, true);
        String hint = Component.translatable("gui.tankscraft.hint").getString();
        int hintW = this.font.width(hint);
        g.drawString(this.font, hint, this.width - hintW - 8, this.height - 10, 0x80706E64, true);
    }

    // ------------------------------------------------------------ interactions

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // carte du carrousel ?
        int y = this.carouselY();
        int left = 34;
        if (button == 0 && mouseY >= y && mouseY < y + CARD_H && mouseX >= left && mouseX <= this.carouselRight()) {
            int index = (int) ((mouseX - left) / (CARD_W + CARD_GAP));
            int cx = left + index * (CARD_W + CARD_GAP);
            if (mouseX - cx < CARD_W && index >= 0 && index < TANKS.size()) {
                this.select(TANKS.get(index));
                return true;
            }
        }
        // démarrage du drag caméra hors widgets
        if (button == 0 && this.getChildAt(mouseX, mouseY) == null) {
            this.dragging = true;
            this.lastMouseX = mouseX;
            this.lastMouseY = mouseY;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.dragging && button == 0) {
            this.targetYaw += (float) ((mouseX - this.lastMouseX) * 0.4F);
            this.targetPitch = Mth.clamp(this.targetPitch + (float) ((mouseY - this.lastMouseY) * 0.2F), -6.0F, 32.0F);
            this.lastMouseX = mouseX;
            this.lastMouseY = mouseY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int y = this.carouselY();
        if (mouseY >= y - 8) {
            this.scrollCarousel(scrollY > 0 ? -1 : 1);
        } else {
            this.targetZoom = Mth.clamp(this.targetZoom * (scrollY > 0 ? 1.12F : 0.89F), 0.55F, 1.9F);
        }
        return true;
    }

    private void select(TankDefinition def) {
        this.selected = def;
        GarageState.get().selectedTank = def.id();
        GarageState.save();
        this.ensureSelectedVisible();
    }

    private void scrollCarousel(int delta) {
        this.carouselScroll = Mth.clamp(this.carouselScroll + delta, 0, TANKS.size() - 1);
    }

    private void clampCarouselScroll() {
        this.carouselScroll = Mth.clamp(this.carouselScroll, 0, TANKS.size() - 1);
    }

    private void ensureSelectedVisible() {
        int index = TANKS.indexOf(this.selected);
        if (index < this.carouselScroll) {
            this.carouselScroll = index;
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    // ------------------------------------------------------------ utilitaires

    private static String format(long value) {
        String raw = String.valueOf(value);
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (int i = raw.length() - 1; i >= 0; i--) {
            sb.append(raw.charAt(i));
            count++;
            if (count % 3 == 0 && i > 0) {
                sb.append(' ');
            }
        }
        return sb.reverse().toString();
    }

    /** Le grand bouton rouge "Au combat !". */
    private class BattleButton extends Button {
        BattleButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            Minecraft mc = Minecraft.getInstance();
            boolean hovered = this.isHovered();
            float pulse = 0.5F + 0.5F * Mth.sin(net.minecraft.Util.getMillis() / 260.0F);

            int top = hovered ? BATTLE_ORANGE : BATTLE_RED;
            int bottom = hovered ? 0xFFC43D0B : 0xFF8F1D16;
            if (!hovered) {
                // léger halo pulsé quand prêt
                top = 0xFF000000 | mixColor(BATTLE_RED, BATTLE_ORANGE, pulse * 0.35F);
            }

            g.fillGradient(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, top, bottom);
            g.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + 1, 0xFF4A2018);
            g.fill(this.getX(), this.getY() + this.height - 1, this.getX() + this.width, this.getY() + this.height, 0xFF000000 | 0x4A2018);

            g.drawCenteredString(mc.font, this.getMessage(), this.getX() + this.width / 2, this.getY() + this.height / 2 - 9, 0xFFFFF3E0);
            g.drawCenteredString(mc.font, Component.translatable("gui.tankscraft.battle_sub"),
                    this.getX() + this.width / 2, this.getY() + this.height / 2 + 3, 0x90FFE8CC);
        }
    }

    private static int mixColor(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t);
        int gg = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        return (r << 16) | (gg << 8) | bl;
    }
}
