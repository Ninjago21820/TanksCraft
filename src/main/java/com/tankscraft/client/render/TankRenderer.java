package com.tankscraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tankscraft.entity.TankEntity;
import com.tankscraft.tank.Tanks;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Rendu du char en jeu. La tourelle suit la vue du pilote ;
 * sans pilote, elle balaie lentement (veille du hangar).
 */
@OnlyIn(Dist.CLIENT)
public class TankRenderer extends EntityRenderer<TankEntity> {
    private static final ResourceLocation TEXTURE = Tanks.T34.texture();

    private final ModelPart model;

    public TankRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = TankModels.bake(Tanks.T34);
    }

    @Override
    public void render(TankEntity entity, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight) {
        pose.pushPose();
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F - entityYaw));
        pose.scale(-1.0F, -1.0F, 1.0F);
        pose.translate(0.0F, -1.501F, 0.0F);

        float turretYaw;
        float gunPitch;
        if (entity.getFirstPassenger() instanceof LivingEntity rider) {
            float riderYaw = Mth.rotLerp(partialTick, rider.yRotO, rider.getYRot());
            float tankYaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
            turretYaw = Mth.wrapDegrees(riderYaw - tankYaw);
            gunPitch = Mth.lerp(partialTick, rider.xRotO, rider.getXRot());
        } else {
            float age = entity.tickCount + partialTick;
            turretYaw = Mth.sin(age * 0.06F) * 22.0F;
            gunPitch = 2.0F + Mth.sin(age * 0.09F) * 2.5F;
        }

        TankModels.poseParts(this.model, turretYaw, gunPitch);

        VertexConsumer consumer = buffer.getBuffer(
                net.minecraft.client.renderer.RenderType.entityCutoutNoCull(getTextureLocation(entity)));
        this.model.render(pose, consumer, packedLight, OverlayTexture.NO_OVERLAY);

        pose.popPose();
        super.render(entity, entityYaw, partialTick, pose, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(TankEntity entity) {
        return TEXTURE;
    }
}
