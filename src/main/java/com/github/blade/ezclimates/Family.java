package com.github.blade.ezclimates;

import java.util.Locale;

/**
 * Biomes that turn alike through the year: a broadleaf forest turns strongly,
 * a jungle barely, conifers a little. Each family's strength scales how far
 * the stages move its colours.
 */
public enum Family {
    FOREST(1.0), PLAINS(0.8), TAIGA(0.45), JUNGLE(0.15), SAVANNA(0.5), SWAMP(0.6), DRY(0.35),
    COLD(0.3), MOUNTAIN(0.6), CHERRY(0.6), PALE(0.2), MUSHROOM(0.2), WATER(0.7);

    private final double strength;

    Family(double strength) { this.strength = strength; }

    /** How far its colours move, 0 to 1, as built in. */
    public double strength() { return strength; }

    public String key() { return name().toLowerCase(Locale.ROOT); }

    /** A biome's family, from its name. */
    public static Family of(String biome) {
        String name = biome.contains(":") ? biome.substring(biome.indexOf(':') + 1) : biome;
        if (name.startsWith("snowy") || name.startsWith("frozen") || name.equals("ice_spikes")
                || name.equals("grove") || name.equals("jagged_peaks")) return COLD;
        if (name.contains("ocean") || name.contains("river") || name.contains("beach")
                || name.equals("stony_shore")) return WATER;
        if (name.contains("jungle")) return JUNGLE;
        if (name.contains("taiga")) return TAIGA;
        if (name.contains("savanna")) return SAVANNA;
        if (name.contains("swamp")) return SWAMP;
        if (name.contains("badlands") || name.equals("desert")) return DRY;
        if (name.equals("cherry_grove")) return CHERRY;
        if (name.equals("pale_garden")) return PALE;
        if (name.equals("mushroom_fields")) return MUSHROOM;
        if (name.startsWith("windswept") || name.equals("meadow") || name.equals("stony_peaks")) return MOUNTAIN;
        if (name.contains("forest")) return FOREST;
        return PLAINS;
    }
}
