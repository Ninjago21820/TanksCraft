package com.tankscraft.client;

import com.tankscraft.TanksCraft;
import com.tankscraft.client.garage.GarageScreen;
import com.tankscraft.entity.TankEntity;
import com.tankscraft.network.FireCannonPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Événements client (game bus) : remplacement du menu principal par le hangar,
 * lecture des entrées du pilote, tir au clic gauche.
 */
@EventBusSubscriber(modid = TanksCraft.MODID, value = Dist.CLIENT)
public final class ClientEvents {

    /** Game time du dernier tir local (pour la barre de rechargement). */
    public static long lastFireGameTime = Long.MIN_VALUE;

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        // Le hangar remplace le menu principal de Minecraft.
        if (event.getScreen() instanceof TitleScreen) {
            event.setNewScreen(new GarageScreen());
        }
    }

    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (mc.player.getControlledVehicle() instanceof TankEntity tank) {
            tank.setClientInput(
                    mc.options.keyUp.isDown(),
                    mc.options.keyDown.isDown(),
                    mc.options.keyLeft.isDown(),
                    mc.options.keyRight.isDown());
        }
    }

    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.isAttack() && mc.player != null
                && mc.player.getControlledVehicle() instanceof TankEntity tank) {
            event.setCanceled(true);
            event.setSwingHand(false);
            PacketDistributor.sendToServer(FireCannonPayload.INSTANCE);
            lastFireGameTime = tank.level().getGameTime();
        }
    }

    private ClientEvents() {
    }
}
