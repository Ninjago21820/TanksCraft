package com.tankscraft.client;

import com.tankscraft.TanksCraft;
import com.tankscraft.client.render.ShellRenderer;
import com.tankscraft.client.render.TankRenderer;
import com.tankscraft.registry.ModEntities;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * Événements client (mod bus) : enregistrement des rendus et de la couche HUD.
 */
@EventBusSubscriber(modid = TanksCraft.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientModEvents {

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.TANK.get(), TankRenderer::new);
        event.registerEntityRenderer(ModEntities.SHELL.get(), ShellRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(TanksCraft.MODID, "tank_hud"),
                TankHudLayer::render);
    }

    private ClientModEvents() {
    }
}
