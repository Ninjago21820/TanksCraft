package com.tankscraft.client.render;

import com.tankscraft.tank.TankDefinition;
import net.minecraft.client.model.CubeListBuilder;
import net.minecraft.client.model.MeshDefinition;
import net.minecraft.client.model.PartDefinition;
import net.minecraft.client.model.PartPose;
import net.minecraft.client.model.geom.LayerDefinition;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

/**
 * Fabrique de modèles de chars. Un seul et même squelette de pièces
 * (coque, chenilles, tourelle, canon, frein de bouche, cupule) est "cuit"
 * avec les dimensions propres à chaque char de la collection.
 *
 * Convention vanilla (modèle) : y vers le bas, sol à y = 24, avant vers -Z.
 * La texture est une planche 256x128 partagée par nation ; le layout UV
 * est synchronisé avec tools/gen_textures.py.
 */
public final class TankModels {
    /** Hauteur de chenille constante, en unités modèle. */
    private static final float TRACK_H = 9.0F;
    private static final float TRACK_W = 5.0F;

    private static final Map<String, ModelPart> BAKED = new HashMap<>();

    public static synchronized ModelPart bake(TankDefinition def) {
        return BAKED.computeIfAbsent(def.id(), id -> build(def));
    }

    private static ModelPart build(TankDefinition def) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        float hullBottom = 24.0F - TRACK_H - def.hullHeight() + 2.0F; // la caque chevauche les chenilles de 2 u
        float hullTop = hullBottom - def.hullHeight();

        // --- coque
        root.addOrReplaceChild("hull",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-def.hullWidth() / 2.0F, -def.hullHeight() / 2.0F, -def.hullLength() / 2.0F,
                                def.hullWidth(), def.hullHeight(), def.hullLength()),
                PartPose.offset(0.0F, hullBottom - def.hullHeight() / 2.0F, 0.0F));

        // --- chenilles (gauche / droite, même texture)
        float trackLen = def.hullLength() - 4.0F;
        float trackX = def.hullWidth() / 2.0F - TRACK_W / 2.0F + 1.0F;
        CubeListBuilder trackBox = CubeListBuilder.create()
                .texOffs(0, 60)
                .addBox(-TRACK_W / 2.0F, -TRACK_H / 2.0F, -trackLen / 2.0F, TRACK_W, TRACK_H, trackLen);
        root.addOrReplaceChild("track_left", trackBox, PartPose.offset(-trackX, 24.0F - TRACK_H / 2.0F, 0.0F));
        root.addOrReplaceChild("track_right", trackBox, PartPose.offset(trackX, 24.0F - TRACK_H / 2.0F, 0.0F));

        // --- tourelle (pivot au centre de la couronne)
        PartDefinition turret = root.addOrReplaceChild("turret",
                CubeListBuilder.create()
                        .texOffs(140, 0)
                        .addBox(-def.turretWidth() / 2.0F, -def.turretHeight(), -def.turretLength() / 2.0F,
                                def.turretWidth(), def.turretHeight(), def.turretLength()),
                PartPose.offset(0.0F, hullTop, -1.0F));

        // --- canon (enfant de la tourelle, pivot sur l'axe de site)
        PartDefinition gun = turret.addOrReplaceChild("gun",
                CubeListBuilder.create()
                        .texOffs(150, 26)
                        .addBox(-2.0F, -2.0F, -def.gunLength() + 2.0F, 4.0F, 4.0F, def.gunLength()),
                PartPose.offset(0.0F, -def.turretHeight() / 2.0F + 0.5F, -def.turretLength() / 2.0F - 0.5F));

        // --- frein de bouche
        if (def.muzzleBrake()) {
            gun.addOrReplaceChild("muzzle",
                    CubeListBuilder.create()
                            .texOffs(230, 0)
                            .addBox(-3.0F, -3.0F, -5.0F, 6.0F, 6.0F, 5.0F),
                    PartPose.offset(0.0F, 0.0F, -def.gunLength() + 2.0F));
        }

        // --- cupule de commandant
        if (def.cupola()) {
            turret.addOrReplaceChild("cupola",
                    CubeListBuilder.create()
                            .texOffs(230, 30)
                            .addBox(-3.0F, -4.0F, -3.0F, 6.0F, 4.0F, 6.0F),
                    PartPose.offset(0.0F, -def.turretHeight(), def.turretLength() / 2.0F - 4.0F));
        }

        return LayerDefinition.create(mesh, 256, 128).bakeRoot();
    }

    /**
     * Oriente tourelle et canon. Les angles sont relatifs au châssis :
     * un tourelleYaw de 0 pointe droit devant.
     */
    public static void poseParts(ModelPart root, float turretYawDeg, float gunPitchDeg) {
        ModelPart turret = root.getChild("turret");
        turret.yRot = turretYawDeg * Mth.DEG_TO_RAD;
        ModelPart gun = turret.getChild("gun");
        gun.xRot = gunPitchDeg * Mth.DEG_TO_RAD;
    }

    private TankModels() {
    }
}
