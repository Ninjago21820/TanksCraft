package com.tankscraft.tank;

import java.util.List;

/**
 * La collection de chars du hangar, dans l'ordre du carrousel (du tier I au tier VII).
 */
public final class Tanks {
    public static final TankDefinition RENAULT_FT = new TankDefinition(
            "renault_ft", TankDefinition.Nation.FRANCE, 1, TankDefinition.TankClass.LIGHT,
            14, 20, 16, 55, 40,
            180, 45, 18, 60,
            15f, 6f, 25f, 8f, 6f, 8f, 14f, false, false);

    public static final TankDefinition LUCHS = new TankDefinition(
            "luchs", TankDefinition.Nation.GERMANY, 3, TankDefinition.TankClass.LIGHT,
            24, 68, 12, 62, 55,
            290, 60, 58, 45,
            20f, 6f, 32f, 11f, 5f, 12f, 19f, false, true);

    public static final TankDefinition MATILDA = new TankDefinition(
            "matilda", TankDefinition.Nation.UK, 4, TankDefinition.TankClass.MEDIUM,
            36, 24, 52, 32, 46,
            470, 90, 26, 70,
            22f, 8f, 34f, 14f, 7f, 14f, 20f, false, false);

    public static final TankDefinition T34 = new TankDefinition(
            "t34", TankDefinition.Nation.USSR, 5, TankDefinition.TankClass.MEDIUM,
            54, 56, 48, 40, 50,
            690, 140, 54, 65,
            24f, 8f, 40f, 14f, 7f, 16f, 24f, false, true);

    public static final TankDefinition PANZER_IV = new TankDefinition(
            "panzer_iv", TankDefinition.Nation.GERMANY, 5, TankDefinition.TankClass.MEDIUM,
            52, 48, 42, 38, 52,
            660, 135, 48, 70,
            24f, 8f, 40f, 14f, 7f, 16f, 25f, true, true);

    public static final TankDefinition SHERMAN = new TankDefinition(
            "sherman", TankDefinition.Nation.USA, 5, TankDefinition.TankClass.MEDIUM,
            55, 50, 45, 36, 58,
            700, 145, 50, 70,
            24f, 9f, 40f, 16f, 7f, 16f, 23f, true, true);

    public static final TankDefinition STUG_III = new TankDefinition(
            "stug_iii", TankDefinition.Nation.GERMANY, 5, TankDefinition.TankClass.TD,
            64, 46, 52, 70, 48,
            640, 165, 42, 80,
            24f, 8f, 42f, 10f, 4f, 11f, 30f, true, false);

    public static final TankDefinition KV2 = new TankDefinition(
            "kv2", TankDefinition.Nation.USSR, 6, TankDefinition.TankClass.HEAVY,
            96, 20, 62, 12, 40,
            920, 320, 28, 160,
            26f, 9f, 44f, 16f, 8f, 16f, 18f, true, true);

    public static final TankDefinition CHURCHILL = new TankDefinition(
            "churchill", TankDefinition.Nation.UK, 6, TankDefinition.TankClass.HEAVY,
            48, 26, 74, 18, 46,
            980, 130, 26, 90,
            24f, 9f, 46f, 14f, 7f, 16f, 21f, false, true);

    public static final TankDefinition TIGER = new TankDefinition(
            "tiger", TankDefinition.Nation.GERMANY, 7, TankDefinition.TankClass.HEAVY,
            82, 32, 72, 15, 55,
            1050, 220, 40, 100,
            26f, 9f, 48f, 16f, 7f, 18f, 30f, true, true);

    public static final List<TankDefinition> ALL = List.of(
            RENAULT_FT, LUCHS, MATILDA, T34, PANZER_IV, SHERMAN, STUG_III, KV2, CHURCHILL, TIGER);

    private Tanks() {
    }

    public static TankDefinition byId(String id) {
        for (TankDefinition def : ALL) {
            if (def.id().equals(id)) {
                return def;
            }
        }
        return T34;
    }
}
