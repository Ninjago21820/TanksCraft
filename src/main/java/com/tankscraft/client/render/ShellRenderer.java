package com.tankscraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tankscraft.entity.ShellEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ShellRenderer extends EntityRenderer<ShellEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("tankscraft", "textures/entity/shell.png");

    private final ModelPart model;

    public ShellRenderer(EntityRendererProvider.Context context) {
        super(context);
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("shell",
                CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, -2.0F, -6.0F, 4.0F, 4.0F, 12.0F),
                PartPose.ZERO);
        this.model = LayerDefinition.create(mesh, 32, 32).bakeRoot();
    }

    @Override
    public void render(ShellEntity entity, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight) {
        pose.pushPose();
        float yaw = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
        float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F - yaw));
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(pitch));
        pose.scale(-1.0F, -1.0F, 1.0F);

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        this.model.render(pose, consumer, packedLight, OverlayTexture.NO_OVERLAY);

        pose.popPose();
        super.render(entity, entityYaw, partialTick, pose, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(ShellEntity entity) {
        return TEXTURE;
    }
}
