package com.github.blade.ezclimates;

import java.util.Locale;

/**
 * Colours as Minecraft writes them in biome files ({@code #rrggbb}), and the
 * colours vanilla gives a biome that sets none.
 *
 * <p>When a biome names no grass or leaf colour, the client takes it from a
 * colour map by the biome's temperature and downfall. The maps are the
 * client's textures, so EZclimates never ships them: each is a smooth blend
 * between three corner colours (hot and wet, hot and dry, cold), which comes
 * close to vanilla's (within about 16 of 255 on a channel). It only shows
 * where a summer copy stands in for the original, such as summer snow.
 */
public final class Colours {
    private Colours() {}

    static final int GRASS_WET = 0x47CD33, GRASS_DRY = 0xBFB755, GRASS_COLD = 0x80B497;
    static final int FOLIAGE_WET = 0x1ABF00, FOLIAGE_DRY = 0xAEA42A, FOLIAGE_COLD = 0x60A17B;
    /** Fallen leaves, which vanilla browns from the leaf colour. */
    static final int DRY_FOLIAGE_BROWN = 0xA27D4D;

    public static int parse(String text) {
        String hex = text.startsWith("#") ? text.substring(1) : text;
        if (hex.length() != 6) throw new IllegalArgumentException("not #rrggbb: " + text);
        return Integer.parseInt(hex, 16) & 0xFFFFFF;
    }

    public static String format(int rgb) {
        return String.format(Locale.ROOT, "#%06x", rgb & 0xFFFFFF);
    }

    /** {@code a} moved {@code share} of the way to {@code b}. */
    public static int mix(int a, int b, double share) {
        double t = Math.max(0.0, Math.min(1.0, share));
        int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    public static int grass(double temperature, double downfall) {
        return colourMap(temperature, downfall, GRASS_WET, GRASS_DRY, GRASS_COLD);
    }

    public static int foliage(double temperature, double downfall) {
        return colourMap(temperature, downfall, FOLIAGE_WET, FOLIAGE_DRY, FOLIAGE_COLD);
    }

    public static int dryFoliage(double temperature, double downfall) {
        return mix(foliage(temperature, downfall), DRY_FOLIAGE_BROWN, 0.65);
    }

    /** Vanilla's lookup: temperature and downfall clamped to 0..1, downfall scaled by the temperature. */
    static int colourMap(double temperature, double downfall, int wet, int dry, int cold) {
        double t = clamp(temperature);
        double d = clamp(downfall) * t;
        double wCold = 1.0 - t, wWet = d, wDry = t - d;
        int r = (int) Math.round(wCold * ((cold >> 16) & 255) + wWet * ((wet >> 16) & 255) + wDry * ((dry >> 16) & 255));
        int g = (int) Math.round(wCold * ((cold >> 8) & 255) + wWet * ((wet >> 8) & 255) + wDry * ((dry >> 8) & 255));
        int b = (int) Math.round(wCold * (cold & 255) + wWet * (wet & 255) + wDry * (dry & 255));
        return (r << 16) | (g << 8) | b;
    }

    private static double clamp(double value) {
        return Double.isFinite(value) ? Math.max(0.0, Math.min(1.0, value)) : 0.5;
    }
}
