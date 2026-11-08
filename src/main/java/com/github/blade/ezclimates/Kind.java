package com.github.blade.ezclimates;

import com.google.gson.JsonObject;

import java.util.Locale;

/** What a copy of a biome brings when it rains: rain, snow, or nothing. */
public enum Kind {
    RAIN, SNOW, DRY;

    /** Snow falls where a biome's temperature is under this, as in vanilla. */
    static final double SNOW_BELOW = 0.15;

    public String key() { return name().toLowerCase(Locale.ROOT); }

    /** What a vanilla biome brings as Minecraft has it. */
    public static Kind vanilla(JsonObject biome) {
        if (biome.has("has_precipitation") && !biome.get("has_precipitation").getAsBoolean()) return DRY;
        double temperature = biome.has("temperature") ? biome.get("temperature").getAsDouble() : 0.8;
        return temperature < SNOW_BELOW ? SNOW : RAIN;
    }

    /**
     * What falls in a section whose middle is at {@code y}, by EZvac's snow
     * line: NaN where rain doesn't reach, otherwise snow above it.
     */
    public static Kind at(double snowLine, double y) {
        if (Double.isNaN(snowLine)) return DRY;
        return y > snowLine ? SNOW : RAIN;
    }
}
