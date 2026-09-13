package com.tankscraft.client.garage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tankscraft.tank.Tanks;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * État du hangar, persisté dans config/tankscraft-garage.json :
 * char sélectionné, devises et statistiques du commandant.
 */
public final class GarageState {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("tankscraft-garage.json");

    private static GarageState instance;

    public String selectedTank = Tanks.T34.id();
    public long credits = 2_500_000;
    public long gold = 12_500;
    public long freeXp = 75_000;
    public int battles = 0;
    public int wins = 0;

    public static synchronized GarageState get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static GarageState load() {
        try {
            if (Files.exists(FILE)) {
                return GSON.fromJson(Files.readString(FILE), GarageState.class);
            }
        } catch (Exception ignored) {
        }
        return new GarageState();
    }

    public static synchronized void save() {
        if (instance == null) {
            return;
        }
        try {
            Files.writeString(FILE, GSON.toJson(instance));
        } catch (IOException ignored) {
        }
    }

    private GarageState() {
    }
}
