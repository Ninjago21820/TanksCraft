package com.tankscraft.registry;

import com.tankscraft.TanksCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TanksCraft.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TANKS_TAB =
            TABS.register("tanks", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.tankscraft"))
                    .icon(() -> ModItems.TANK.get().getDefaultInstance())
                    .displayItems((parameters, output) -> output.accept(ModItems.TANK.get()))
                    .build());

    private ModCreativeTabs() {
    }

    public static void register(IEventBus modEventBus) {
        TABS.register(modEventBus);
    }
}
