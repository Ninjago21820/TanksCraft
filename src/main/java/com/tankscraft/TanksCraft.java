package com.tankscraft;

import com.mojang.logging.LogUtils;
import com.tankscraft.registry.ModCreativeTabs;
import com.tankscraft.registry.ModEntities;
import com.tankscraft.registry.ModItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * TanksCraft — le char, tout un art de vivre.
 *
 * Le menu principal de Minecraft est remplacé par le hangar façon
 * World of Tanks (voir {@code com.tankscraft.client.garage.GarageScreen}).
 */
@Mod(TanksCraft.MODID)
public class TanksCraft {
    public static final String MODID = "tankscraft";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TanksCraft(IEventBus modEventBus) {
        ModEntities.register(modEventBus);
        ModItems.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        LOGGER.info("TanksCraft : le hangar ouvre ses portes.");
    }
}
