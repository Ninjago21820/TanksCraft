package com.tankscraft.registry;

import com.tankscraft.TanksCraft;
import com.tankscraft.entity.ShellEntity;
import com.tankscraft.entity.TankEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, TanksCraft.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<TankEntity>> TANK =
            ENTITY_TYPES.register("tank", () -> EntityType.Builder.<TankEntity>of(TankEntity::new, MobCategory.MISC)
                    .sized(1.8F, 1.75F)
                    .clientTrackingRange(10)
                    .updateInterval(3)
                    .build("tank"));

    public static final DeferredHolder<EntityType<?>, EntityType<ShellEntity>> SHELL =
            ENTITY_TYPES.register("shell", () -> EntityType.Builder.<ShellEntity>of(ShellEntity::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F)
                    .clientTrackingRange(6)
                    .updateInterval(1)
                    .build("shell"));

    private ModEntities() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}
