package com.tankscraft.client.garage;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
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

import java.util.Locale;

/**
 * Le hangar, façon World of Tanks moderne (1.0+) : fond anthracite, char au
 * centre-gauche, fiche du char à droite au-dessus du grand bouton rouge
 * « Au combat ! », carrousel compact pleine largeur en bas avec le rendu 3D
 * de chaque char dans sa carte.
 */
@OnlyIn(Dist.CLIENT)
public class GarageScreen extends Screen {
    private static final ResourceLocation HANGAR =
            ResourceLocation.fromNamespaceAndPath("tankscraft", "textures/gui/hangar.png");
    private static final ResourceLocation LOGO =
            ResourceLocation.fromNamespaceAndPath("tankscraft", "textures/gui/logo.png");

    // ------------------------------------------------------------ layout
    private static final int CARD_W = 92;
    private static final int CARD_H = 52;
    private static final int CARD_GAP = 4;
    private static final int STRIP_MARGIN = 30;
    /** Hauteur réservée en bas : cartes + ligne de pied de page. */
    private static final int STRIP_BOTTOM = 64;
    private static final int RIGHT_W = 196;
    private static final int MARGIN = 10;

    /** Hauteur (en blocs) du centre de rotation de la caméra du hangar. */
    private static final float ORBIT_CENTER_Y = 0.85F;

    // ------------------------------------------------------------ palette (anthracite + or)
    private static final int OVERLAY_DARK = 0x59000000;
    private static final int PANEL_BG = 0xE6080A0C;
    private static final int PANEL_BG_DARK = 0xF0050708;
    private static final int PANEL_BORDER = 0xFF8A6D3B;
    private static final int GOLD = 0xFFD9A441;
    private static final int GOLD_DIM = 0xFF9C7B3E;
    private static final int TEXT_MAIN = 0xFFF2EFE6;
    private static final int TEXT_DIM = 0xFF9B9F93;
    private static final int BATTLE_RED = 0xFFC23B22;
    private static final int BATTLE_RED_DARK = 0xFF7E1F10;
    private static final int BATTLE_HOVER = 0xFFE8590C;

    private final ItemStack goldIcon = new ItemStack(Items.GOLD_INGOT);
    private final ItemStack creditIcon = new ItemStack(Items.EMERALD);
    private final ItemStack xpIcon = new ItemStack(Items.EXPERIENCE_BOTTLE);

    private TankDefinition selected;
    private int carouselScroll = 0;

    // caméra
    private float targetYaw = -32.0F;
    private float renderYaw = -32.0F;
    private float targetPitch = 10.0F;
    private float renderPitch = 10.0F;
    private float targetZoom = 1.0F;
    private float renderZoom = 1.0F;

    private boolean dragging = false;
    private double lastMouseX, lastMouseY;

    /** Capture automatique du garage (CI) : -Dtankscraft.autoshot=true */
    private static final boolean AUTOSHOT = Boolean.getBoolean("tankscraft.autoshot");
    private int autoshotCountdown = AUTOSHOT ? 30 : -1;

    public GarageScreen() {
        super(Component.translatable("screen.tankscraft.garage"));
    }

    // ------------------------------------------------------------ initialisation

    @Override
    protected void init() {
        super.init();
        this.selected = Tanks.byId(GarageState.get().selectedTank);
        this.ensureSelectedVisible();

        // menu vertical à gauche (comme le bandeau latéral WoT)
        int mx = MARGIN;
        int my = 58;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.tankscraft.multiplayer"),
                        b -> this.minecraft.setScreen(new JoinMultiplayerScreen(this)))
                .bounds(mx, my, 92, 18).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.tankscraft.options"),
                        b -> this.minecraft.setScreen(new OptionsScreen(this, this.minecraft.options)))
                .bounds(mx, my + 20, 92, 18).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.tankscraft.mods"),
                        b -> this.minecraft.setScreen(new ModListScreen(this)))
                .bounds(mx, my + 40, 92, 18).build());
        this.addRenderableWidget(Button.builder(Component.translatable("menu.quit"),
                        b -> this.minecraft.stop())
                .bounds(mx, my + 60, 92, 18).build());

        // grand bouton rouge, en bas à droite au-dessus du carrousel
        this.addRenderableWidget(new BattleButton(this.width - MARGIN - 6 - RIGHT_W, this.stripCardsY() - 52,
                RIGHT_W, 44, Component.translatable("gui.tankscraft.to_battle"),
                b -> this.minecraft.setScreen(new SelectWorldScreen(this))));

        // flèches du carrousel
        int cy = this.stripCardsY();
        this.addRenderableWidget(Button.builder(Component.literal("<"), b -> this.scrollCarousel(-1))
                .bounds(5, cy + CARD_H / 2 - 9, 18, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal(">"), b -> this.scrollCarousel(1))
                .bounds(this.width - 5 - 18, cy + CARD_H / 2 - 9, 18, 18).build());
    }

    // ------------------------------------------------------------ rendu

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderProbe(g, 0); // SONDE : avant le fond (attendu : recouvert par le fond)
        this.renderBackdrop(g);
        renderProbe(g, 1); // SONDE : après le fond

        this.renderTankPreview(g, partialTick);
        renderProbe(g, 2); // SONDE : après le char vedette

        // toute l'UI passe devant le char (z=400 : plan des tooltips vanilla,
        // rester en dessous du plan far de la projection ortho GUI)
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(0.0F, 0.0F, 400.0F);

        this.renderLogo(g);
        this.renderTopBar(g);
        renderProbe(g, 3); // SONDE : après logo + barre du haut (z=400)

        this.renderRightPanel(g);
        renderProbe(g, 4); // SONDE : après le panneau droit (z=400)

        // SONDE triplet z + drawManaged (même instant que la sonde 4) :
        // si la couleur de base l'emporte, z n'a pas d'effet sur les fills.
        pose.pushPose();
        pose.translate(0.0F, 0.0F, -400.0F);
        g.fill(10, 188, 18, 196, 0xFFFF4040); // z≈0
        pose.popPose();
        g.fill(18, 188, 26, 196, 0xFF40FF40); // z≈400
        pose.pushPose();
        pose.translate(0.0F, 0.0F, -460.0F);
        g.fill(26, 188, 34, 196, 0xFF4040FF); // z≈-60
        pose.popPose();
        g.drawManaged(() -> g.fill(34, 188, 42, 196, 0xFFFF8000));

        this.renderCarousel(g, mouseX, mouseY);
        renderProbe(g, 5); // SONDE : après le carrousel

        this.renderFooter(g);

        super.render(g, mouseX, mouseY, partialTick);
        renderProbe(g, 6); // SONDE : après les widgets

        pose.popPose();

        if (AUTOSHOT && this.autoshotCountdown > -200) {
            // on ne décompte qu'une fois l'écran de chargement disparu ;
            // 3 captures espacées pour détecter une accumulation inter-frames
            if (this.minecraft.getOverlay() == null) {
                int c = this.autoshotCountdown--;
                if (c == 0) {
                    this.captureAutoshot("");
                } else if (c == -30) {
                    this.captureAutoshot("_b");
                } else if (c == -60) {
                    this.captureAutoshot("_c");
                    this.minecraft.stop();
                }
            }
        }
    }

    /**
     * SONDE de diagnostic rendu (à retirer une fois le problème résolu) :
     * carré opaque 8x8 + « A » blanc, colonne x=0/10, ligne y=140+stage*8.
     * La couleur et la luminosité finales de chaque carré renseignent
     * l'ordre effectif de composition GPU des draws de la frame.
     */
    private void renderProbe(GuiGraphics g, int stage) {
        int y = 140 + stage * 8;
        int color = switch (stage) {
            case 0 -> 0xFFFF0000; // rouge : avant le fond
            case 1 -> 0xFF00FF00; // vert : après le fond
            case 2 -> 0xFF0000FF; // bleu : après le char vedette
            case 3 -> 0xFFFFFF00; // jaune : après logo + topbar
            case 4 -> 0xFF00FFFF; // cyan : après le panneau droit
            case 5 -> 0xFFFF00FF; // magenta : après le carrousel
            default -> 0xFFFFFFFF; // blanc : après les widgets
        };
        g.fill(0, y, 8, y + 8, color);
        g.drawString(this.font, "A", 10, y + 1, 0xFFFFFFFF, false);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // no-op volontaire : le garage dessine son propre fond dès le début de
        // render(). En 1.21.1, Screen.render() appelle renderBackground() qui
        // dessine panorama + flou de menu (menuBackgroundBlurriness) + gradient
        // sombre PAR-DESSUS l'UI déjà dessinée, juste avant les widgets.
    }

    /** Screenshot du garage (utilisé par la CI) ; suffixe "", "_b" ou "_c". */
    private void captureAutoshot(String suffix) {
        try {
            String path = System.getProperty("tankscraft.autoshot.path", "autoshot.png");
            String out = path.endsWith(".png")
                    ? path.substring(0, path.length() - 4) + suffix + ".png"
                    : path + suffix;
            net.minecraft.client.Screenshot.takeScreenshot(this.minecraft.getMainRenderTarget())
                    .writeToFile(new java.io.File(out));
        } catch (Exception ignored) {
        }
    }

    private void renderBackdrop(GuiGraphics g) {
        g.blit(HANGAR, 0, 0, this.width, this.height, 0.0F, 0.0F, 1376, 768, 1376, 768);
        // ambiance « garage WoT » : anthracite profond + vignettage
        g.fill(0, 0, this.width, this.height, OVERLAY_DARK);
        g.fillGradient(0, 0, this.width, this.height / 3, 0x8C000000, 0x00000000);
        g.fillGradient(0, this.height - 120, this.width, this.height, 0x00000000, 0xB3000000);
    }

    private void renderLogo(GuiGraphics g) {
        // petit logo, calé dans la bande libre entre le bloc commandant et les devises
        int logoH = 20;
        int logoW = logoH * 4;
        int bandL = 118;
        int bandR = this.width - 250;
        if (bandR - bandL < logoW + 8) {
            return; // écran trop étroit : pas de logo
        }
        int x = (bandL + bandR) / 2 - logoW / 2;
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        g.blit(LOGO, x, 6, logoW, logoH, 0.0F, 0.0F, 1024, 256, 1024, 256);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private void renderTopBar(GuiGraphics g) {
        GarageState state = GarageState.get();

        // commandant, en haut à gauche
        g.drawString(this.font, Component.translatable("gui.tankscraft.commander"), MARGIN + 2, 8, GOLD_DIM, true);
        String playerName = this.minecraft != null ? this.minecraft.getUser().getName() : "Commandant";
        g.drawString(this.font, playerName, MARGIN + 2, 19, TEXT_MAIN, true);

        // devises, en haut à droite
        int x = this.width - MARGIN;
        x = drawCurrency(g, x, format(state.credits), this.creditIcon);
        x = drawCurrency(g, x, format(state.gold), this.goldIcon);
        drawCurrency(g, x, format(state.freeXp), this.xpIcon);
    }

    /** Dessine une devise de droite à gauche ; renvoie le x suivant. */
    private int drawCurrency(GuiGraphics g, int rightX, String value, ItemStack icon) {
        int textW = this.font.width(value);
        int w = textW + 22;
        fillChamfer(g, rightX - w - 8, 5, rightX, 26, 3, PANEL_BG);
        g.renderItem(icon, rightX - w - 4, 8);
        g.drawString(this.font, value, rightX - textW - 3, 13, GOLD, true);
        return rightX - w - 14;
    }

    // ------------------------------------------------------------ fiche char (droite)

    private void renderRightPanel(GuiGraphics g) {
        TankDefinition def = this.selected;
        int x1 = this.width - MARGIN - 6 - RIGHT_W;
        int x2 = this.width - MARGIN - 6;
        int y1 = 32;
        int y2 = this.stripCardsY() - 60;

        panelChamfer(g, x1, y1, x2, y2, 5);

        int px = x1 + 8;
        int pw = RIGHT_W - 16;

        // plaque nominale : tier + nom
        g.drawString(this.font, TankDefinition.roman(def.tier()), px, y1 + 6, GOLD, true);
        String name = this.font.plainSubstrByWidth(def.name().getString(), pw - 14);
        g.drawString(this.font, name, px + 13, y1 + 6, TEXT_MAIN, true);
        g.drawString(this.font, def.className().getString() + " • " + def.nation().displayName().getString(),
                px, y1 + 17, TEXT_DIM, true);

        // barres de caractéristiques, une ligne chacune
        int sy = y1 + 31;
        sy = drawStatBar(g, px, sy, pw, "gui.tankscraft.stat.firepower", def.firepower());
        sy = drawStatBar(g, px, sy, pw, "gui.tankscraft.stat.mobility", def.mobility());
        sy = drawStatBar(g, px, sy, pw, "gui.tankscraft.stat.armor", def.armor());
        sy = drawStatBar(g, px, sy, pw, "gui.tankscraft.stat.camo", def.camo());
        sy = drawStatBar(g, px, sy, pw, "gui.tankscraft.stat.view", def.viewRange());

        // chiffres clés en grille 2 x 2
        int gy = sy + 6;
        int colW = pw / 2;
        g.fill(px, gy - 4, px + pw, gy - 3, PANEL_BORDER);
        drawKeyValue(g, px, gy, colW, Component.translatable("gui.tankscraft.stat.hp"), def.hp() + "");
        drawKeyValue(g, px + colW, gy, colW, Component.translatable("gui.tankscraft.stat.damage"), def.damage() + "");
        drawKeyValue(g, px, gy + 11, colW, Component.translatable("gui.tankscraft.stat.speed"), def.topSpeedKmh() + "");
        drawKeyValue(g, px + colW, gy + 11, colW, Component.translatable("gui.tankscraft.stat.reload"),
                String.format(Locale.ROOT, "%.1fs", def.reloadTicks() / 20.0F));
    }

    /** Une caractéristique sur une ligne : libellé, barre segmentée or, valeur. */
    private int drawStatBar(GuiGraphics g, int x, int y, int w, String labelKey, int value) {
        g.drawString(this.font, Component.translatable(labelKey), x, y, TEXT_DIM, true);
        String num = String.valueOf(value);
        g.drawString(this.font, num, x + w - this.font.width(num), y, GOLD, true);

        int barX = x + 92;
        int barW = w - 92 - 15;
        int segments = 8;
        int segW = Math.max(2, (barW - (segments - 1)) / segments);
        int filled = Mth.clamp(value, 0, 100) * segments / 100;
        int barY = y + 3;
        for (int i = 0; i < segments; i++) {
            int sx = barX + i * (segW + 1);
            g.fill(sx, barY, sx + segW, barY + 3, 0xFF26221A);
            if (i < filled) {
                g.fill(sx, barY, sx + segW, barY + 3, GOLD);
            }
        }
        return y + 11;
    }

    private void drawKeyValue(GuiGraphics g, int x, int y, int w, Component key, String value) {
        g.drawString(this.font, key, x, y, TEXT_DIM, true);
        g.drawString(this.font, value, x + w - this.font.width(value), y, TEXT_MAIN, true);
    }

    // ------------------------------------------------------------ carrousel

    private int stripCardsY() {
        return this.height - STRIP_BOTTOM;
    }

    private void renderCarousel(GuiGraphics g, int mouseX, int mouseY) {
        int cardsY = this.stripCardsY();
        int left = STRIP_MARGIN;

        // bandeau sombre pleine largeur
        g.fill(0, cardsY - 4, this.width, this.height, 0xCC050607);

        for (int i = 0; i < Tanks.ALL.size(); i++) {
            TankDefinition def = Tanks.ALL.get(i);
            int cx = left + (i - this.carouselScroll) * (CARD_W + CARD_GAP);
            if (cx + CARD_W < left - 4 || cx > this.width + 4) {
                continue;
            }
            boolean isSelected = def == this.selected;
            boolean hovered = mouseX >= cx && mouseX < cx + CARD_W && mouseY >= cardsY && mouseY < cardsY + CARD_H;

            // fond de carte (sous le char 3D)
            PoseStack pose = g.pose();
            pose.pushPose();
            pose.translate(0.0F, 0.0F, -60.0F);
            int border = isSelected ? GOLD : (hovered ? 0xFFC7A457 : PANEL_BORDER);
            fillChamfer(g, cx - 1, cardsY - 1, cx + CARD_W + 1, cardsY + CARD_H + 1, 3, border);
            fillChamfer(g, cx, cardsY, cx + CARD_W, cardsY + CARD_H, 3,
                    isSelected ? 0xFF1A1710 : PANEL_BG_DARK);
            pose.popPose();

            // SONDE : carré dans la carte #0 juste après ses fills de fond —
            // visible ⇒ les draws suivants passent bien au-dessus des fills
            if (i == 0) {
                g.fill(40, cardsY + 22, 48, cardsY + 30, 0xFFFF00FF);
            }

            // rendu 3D du char dans la carte (vignette façon WoT).
            // z=360 : AU-DESSUS des fills de fond de carte (z=340, qui écrivent le
            // depth buffer) et EN-DESSOUS des textes (z=400). Un z négatif serait
            // rejeté par le test de profondeur contre le fond plein écran (z=0).
            renderTankModel(g, def, cx + CARD_W / 2.0F, cardsY + CARD_H - 5, 26.0F,
                    -28.0F, 14.0F, -6.0F, 1.5F, 360.0F, 0.02F);

            // tier + classe
            g.drawString(this.font, TankDefinition.roman(def.tier()), cx + 4, cardsY + 3, GOLD, true);
            int badgeW = 20;
            g.fill(cx + CARD_W - badgeW - 4, cardsY + 3, cx + CARD_W - 4, cardsY + 12, def.tankClass().color | 0xFF000000);
            g.drawCenteredString(this.font, def.tankClass().shortLabel(), cx + CARD_W - badgeW / 2 - 4, cardsY + 4, 0xFF101210);

            // liseré de nation + nom
            g.fill(cx + 4, cardsY + 15, cx + 7, cardsY + 18, def.nation().color);
            g.fill(cx + 4, cardsY + 15, cx + 5, cardsY + 18, def.nation().accent);
            String name = this.font.plainSubstrByWidth(def.name().getString(), CARD_W - 8);
            g.drawString(this.font, name, cx + 4, cardsY + CARD_H - 11, isSelected ? TEXT_MAIN : TEXT_DIM, true);

            if (isSelected) {
                g.fill(cx + 6, cardsY + CARD_H - 2, cx + CARD_W - 6, cardsY + CARD_H - 1, GOLD);
            }
        }
    }

    private void renderFooter(GuiGraphics g) {
        g.drawString(this.font, "TanksCraft 0.1.0 — 1.21.1 NeoForge", 6, this.height - 10, 0x80605E58, true);
        String hint = Component.translatable("gui.tankscraft.hint").getString();
        int hintW = this.font.width(hint);
        g.drawString(this.font, hint, this.width - hintW - 26, this.height - 10, 0x80706E64, true);
    }

    // ------------------------------------------------------------ rendu 3D des chars

    /** Le char vedette, au centre-gauche de l'écran. */
    private void renderTankPreview(GuiGraphics g, float partialTick) {
        this.renderYaw += Mth.wrapDegrees(this.targetYaw - this.renderYaw) * 0.25F;
        this.renderPitch += (this.targetPitch - this.renderPitch) * 0.25F;
        this.renderZoom += (this.targetZoom - this.renderZoom) * 0.25F;

        float anchorX = this.width * 0.36F;
        float anchorY = this.height - STRIP_BOTTOM - 12;

        // ombre au sol
        float shadow = 0.55F * this.renderZoom;
        int halfW = (int) (60 * shadow);
        g.fill((int) anchorX - halfW, (int) anchorY - 2, (int) anchorX + halfW, (int) anchorY + 2, 0x50000000);
        g.fill((int) anchorX - (int) (halfW * 0.8F), (int) anchorY - 1, (int) anchorX + (int) (halfW * 0.8F), (int) anchorY + 1, 0x50000000);

        float ppb = (this.height * 0.5F * this.renderZoom) / 3.0F;
        float time = (net.minecraft.Util.getMillis() % 100000L) / 1000.0F;
        float turretYaw = Mth.sin(time * 0.6F) * 20.0F;
        float gunPitch = 3.0F + Mth.sin(time * 0.4F) * 2.0F;
        renderTankModel(g, this.selected, anchorX, anchorY, ppb,
                this.renderYaw, this.renderPitch, turretYaw, gunPitch, 150.0F, 0.25F);
    }

    /**
     * Rend un char façon InventoryScreen.renderEntityInInventory (1.21.1) :
     * espace GUI (z inversé), flip écran, pitch caméra, orbite yaw autour du
     * centre, flip modèle, pieds posés sur l'ancre. ModelPart divise par 16.
     *
     * @param zBase        décalage z de base (unités GUI) — 150 pour la scène,
     *                     valeur négative pour rester sous l'UI dans les cartes
     * @param zCompression écrase la profondeur pour rester dans le plan voulu
     */
    private static void renderTankModel(GuiGraphics g, TankDefinition def, float cx, float cyBottom,
                                        float ppb, float yawDeg, float pitchDeg,
                                        float turretYawDeg, float gunPitchDeg,
                                        float zBase, float zCompression) {
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cyBottom, zBase);
        pose.scale(1.0F, 1.0F, zCompression);
        pose.scale(ppb, ppb, -ppb);
        pose.mulPose(Axis.ZP.rotationDegrees(180.0F));
        pose.mulPose(Axis.XP.rotationDegrees(pitchDeg));
        pose.translate(0.0F, ORBIT_CENTER_Y, 0.0F);
        pose.mulPose(Axis.YP.rotationDegrees(-yawDeg));
        pose.translate(0.0F, -ORBIT_CENTER_Y, 0.0F);
        pose.scale(-1.0F, -1.0F, 1.0F);
        pose.translate(0.0F, -1.501F, 0.0F);

        ModelPart model = TankModels.bake(def);
        TankModels.poseParts(model, turretYawDeg, gunPitchDeg);

        Lighting.setupForEntityInInventory();
        VertexConsumer consumer = g.bufferSource().getBuffer(RenderType.entityCutoutNoCull(def.texture()));
        model.render(pose, consumer, 0xF000F0, OverlayTexture.NO_OVERLAY);
        g.flush();
        Lighting.setupFor3DItems();

        pose.popPose();
    }

    // ------------------------------------------------------------ primitives chamfreinées

    /** Rectangle à coins coupés (style angulaire WoT) : union de deux rects. */
    private static void fillChamfer(GuiGraphics g, int x1, int y1, int x2, int y2, int c, int color) {
        if (c <= 0) {
            g.fill(x1, y1, x2, y2, color);
            return;
        }
        g.fill(x1, y1 + c, x2, y2 - c, color); // bande horizontale
        g.fill(x1 + c, y1, x2 - c, y2, color); // bande verticale
    }

    /** Panneau chamferiné avec fine bordure dorée. */
    private static void panelChamfer(GuiGraphics g, int x1, int y1, int x2, int y2, int c) {
        fillChamfer(g, x1, y1, x2, y2, c + 1, PANEL_BORDER);
        fillChamfer(g, x1 + 1, y1 + 1, x2 - 1, y2 - 1, c, PANEL_BG);
    }

    // ------------------------------------------------------------ interactions

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // carte du carrousel ?
        int cardsY = this.stripCardsY();
        int left = STRIP_MARGIN;
        if (button == 0 && mouseY >= cardsY && mouseY < cardsY + CARD_H && mouseX >= left && mouseX <= this.width - STRIP_MARGIN) {
            int index = (int) ((mouseX - left) / (CARD_W + CARD_GAP));
            int cx = left + index * (CARD_W + CARD_GAP);
            if (mouseX - cx < CARD_W && index >= 0 && index < Tanks.ALL.size()) {
                this.select(Tanks.ALL.get(index));
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
        if (mouseY >= this.stripCardsY() - 8) {
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
        this.carouselScroll = Mth.clamp(this.carouselScroll + delta, 0, Tanks.ALL.size() - 1);
    }

    private void ensureSelectedVisible() {
        int index = Tanks.ALL.indexOf(this.selected);
        if (index >= 0 && index < this.carouselScroll) {
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

    /** Le grand bouton rouge « Au combat ! », chamferiné, bordure or, pulsant. */
    private class BattleButton extends Button {
        BattleButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            Minecraft mc = Minecraft.getInstance();
            boolean hovered = this.isHovered();
            float pulse = 0.5F + 0.5F * Mth.sin(net.minecraft.Util.getMillis() / 260.0F);

            int top = hovered ? BATTLE_HOVER : mixColor(BATTLE_RED, BATTLE_HOVER, pulse * 0.30F);
            int bottom = hovered ? 0xFFB23E09 : mixColor(BATTLE_RED_DARK, 0xFFB23E09, pulse * 0.30F);

            int x1 = this.getX();
            int y1 = this.getY();
            int x2 = x1 + this.width;
            int y2 = y1 + this.height;

            // bordure or, anneau rouge, corps en dégradé
            fillChamfer(g, x1, y1, x2, y2, 4, GOLD_DIM);
            fillChamfer(g, x1 + 1, y1 + 1, x2 - 1, y2 - 1, 4, top);
            g.fillGradient(x1 + 4, y1 + 4, x2 - 4, y2 - 4, top, bottom);

            g.drawCenteredString(mc.font, this.getMessage(), x1 + this.width / 2, y1 + this.height / 2 - 9, 0xFFFFF3E0);
            g.drawCenteredString(mc.font, Component.translatable("gui.tankscraft.battle_sub"),
                    x1 + this.width / 2, y1 + this.height / 2 + 3, 0x90FFE8CC);
        }
    }

    private static int mixColor(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t);
        int gg = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        // OBLIGATOIRE : sans le OR alpha, la couleur vaut 0x00RRGGBB = fill invisible
        return 0xFF000000 | (r << 16) | (gg << 8) | bl;
    }
}
