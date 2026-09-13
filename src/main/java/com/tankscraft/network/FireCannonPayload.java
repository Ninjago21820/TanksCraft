package com.tankscraft.network;

import com.tankscraft.TanksCraft;
import com.tankscraft.entity.TankEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Demande de tir de canon envoyée par le pilote d'un char.
 */
public record FireCannonPayload() implements CustomPacketPayload {

    public static final FireCannonPayload INSTANCE = new FireCannonPayload();

    public static final CustomPacketPayload.Type<FireCannonPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(TanksCraft.MODID, "fire_cannon"));

    @SuppressWarnings("unused")
    public static final StreamCodec<FriendlyByteBuf, FireCannonPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FireCannonPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (player.getControlledVehicle() instanceof TankEntity tank) {
            tank.fire(player);
        }
    }
}
