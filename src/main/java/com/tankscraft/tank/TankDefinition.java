package com.tankscraft.tank;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Définition d'un char du garage, façon World of Tanks :
 * nation, tier, classe, statistiques et paramètres du modèle 3D.
 *
 * Les dimensions du modèle sont en "unités modèle" (16 unités = 1 bloc),
 * convention vanilla : y vers le bas, sol à y = 24, l'avant du char vers -Z.
 */
public record TankDefinition(
        String id,
        Nation nation,
        int tier,
        TankClass tankClass,
        int firepower,
        int mobility,
        int armor,
        int camo,
        int viewRange,
        int hp,
        int damage,
        int topSpeedKmh,
        int reloadTicks,
        // --- dimensions du modèle (unités modèle) ---
        float hullWidth,
        float hullHeight,
        float hullLength,
        float turretWidth,
        float turretHeight,
        float turretLength,
        float gunLength,
        boolean muzzleBrake,
        boolean cupola
) {
    /** Vitesse de pointe en blocs par tick (conversion 1 bloc = 1 m, 20 ticks/s). */
    public double topSpeedBlocksPerTick() {
        return this.topSpeedKmh / 3.6 / 20.0;
    }

    public Component name() {
        return Component.translatable("tank.tankscraft." + this.id);
    }

    public Component className() {
        return Component.translatable("tankclass.tankscraft." + this.tankClassSerializedName());
    }

    public String tankClassSerializedName() {
        return this.tankClass.name().toLowerCase();
    }

    public ResourceLocation texture() {
        return ResourceLocation.fromNamespaceAndPath("tankscraft", "textures/entity/tank_" + this.nation.name().toLowerCase() + ".png");
    }

    public ResourceLocation carouselTexture() {
        return this.texture();
    }

    public enum Nation {
        FRANCE(0x2E4A8F, 0xB34A4A),
        GERMANY(0x3A3A40, 0x8F8F96),
        USSR(0x7A3B3B, 0xC9A227),
        USA(0x3F5E46, 0x9AB0C9),
        UK(0x6B4E2E, 0xA88F5A);

        public final int color;
        public final int accent;

        Nation(int color, int accent) {
            this.color = color;
            this.accent = accent;
        }

        public Component displayName() {
            return Component.translatable("nation.tankscraft." + name().toLowerCase());
        }
    }

    public enum TankClass {
        LIGHT(0xD9C98A),
        MEDIUM(0x9AB56B),
        HEAVY(0xC97B6B),
        TD(0x8AA0C9),
        SPG(0xC98AC9);

        public final int color;

        TankClass(int color) {
            this.color = color;
        }

        public String shortLabel() {
            return switch (this) {
                case LIGHT -> "LT";
                case MEDIUM -> "MT";
                case HEAVY -> "HT";
                case TD -> "TD";
                case SPG -> "SPG";
            };
        }
    }

    /** Chiffre romain du tier (I..X), comme dans WoT. */
    public static String roman(int tier) {
        return switch (tier) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            default -> "X";
        };
    }
}
