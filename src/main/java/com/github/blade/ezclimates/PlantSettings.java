package com.github.blade.ezclimates;

import org.bukkit.configuration.ConfigurationSection;

import java.util.function.Consumer;

/**
 * How plants follow EZvac's climate, from config.yml's {@code plants}
 * section, read leniently: a value out of range keeps its default with a
 * warning.
 *
 * @param crops        crops and saplings grow by the air where they stand
 * @param stopsBelowF  nothing grows in air this cold or colder
 * @param fullFromF    growth is at vanilla's pace from here, rising to it from {@code stopsBelowF}
 * @param hotAboveF    above this, heat slows growth again
 * @param noneAboveF   and here it stops
 * @param humidBoost   how much faster growth is in warm air at 60% humidity or more
 * @param dryFactor    how much of it is left in air drier than 30%
 * @param flowers      flowers come up on open grass in spring
 * @param mushrooms    mushrooms come up in shade in autumn
 * @param maxPerChunk  the most flowers and mushrooms EZclimates keeps in one chunk
 * @param rate         how fast they come up, 1 filling a chunk over some minutes
 */
public record PlantSettings(boolean crops, double stopsBelowF, double fullFromF, double hotAboveF, double noneAboveF,
                            double humidBoost, double dryFactor, boolean flowers, boolean mushrooms, int maxPerChunk,
                            double rate) {
    public static PlantSettings defaults() {
        return new PlantSettings(true, 36.0, 60.0, 95.0, 115.0, 1.5, 0.75, true, true, 4, 1.0);
    }

    public static PlantSettings load(ConfigurationSection config, Consumer<String> warn) {
        PlantSettings d = defaults();
        if (config == null) return d;
        double stops = config.getDouble("crops.stops-below-f", d.stopsBelowF);
        double full = config.getDouble("crops.full-from-f", d.fullFromF);
        double hot = config.getDouble("crops.slows-above-f", d.hotAboveF);
        double none = config.getDouble("crops.stops-above-f", d.noneAboveF);
        if (!(stops < full && full <= hot && hot < none)) {
            warn.accept("plants.crops: stops-below-f, full-from-f, slows-above-f and stops-above-f must rise in that"
                    + " order; using " + d.stopsBelowF + ", " + d.fullFromF + ", " + d.hotAboveF + " and " + d.noneAboveF + ".");
            stops = d.stopsBelowF;
            full = d.fullFromF;
            hot = d.hotAboveF;
            none = d.noneAboveF;
        }
        return new PlantSettings(config.getBoolean("crops.enabled", d.crops), stops, full, hot, none,
                range(config, "crops.humid-boost", d.humidBoost, 1.0, 3.0, warn),
                range(config, "crops.dry-factor", d.dryFactor, 0.0, 1.0, warn),
                config.getBoolean("flowers", d.flowers), config.getBoolean("mushrooms", d.mushrooms),
                (int) range(config, "max-per-chunk", d.maxPerChunk, 0, 32, warn),
                range(config, "rate", d.rate, 0.1, 20.0, warn));
    }

    private static double range(ConfigurationSection config, String path, double fallback, double low, double high,
                                Consumer<String> warn) {
        double value = config.getDouble(path, fallback);
        if (value >= low && value <= high) return value;
        warn.accept("plants." + path + " must be " + low + " to " + high + "; using " + fallback + ".");
        return fallback;
    }

    /**
     * How fast a plant grows in this air, against vanilla's pace: 0 where
     * it is too cold or too hot to grow at all, rising to 1 as the air
     * warms, more in warm humid air and less in dry air. NaN humidity (no
     * air to measure) counts as ordinary.
     */
    public double growth(double airF, double humidity) {
        if (!Double.isFinite(airF)) return 1.0;
        double warmth = airF <= stopsBelowF ? 0.0
                : airF < fullFromF ? (airF - stopsBelowF) / (fullFromF - stopsBelowF)
                : airF <= hotAboveF ? 1.0
                : airF < noneAboveF ? 1.0 - (airF - hotAboveF) / (noneAboveF - hotAboveF)
                : 0.0;
        if (warmth <= 0.0 || !Double.isFinite(humidity)) return Math.max(0.0, warmth);
        double moisture = humidity < 30.0 ? dryFactor
                : humidity >= 60.0 && airF >= fullFromF && airF <= hotAboveF ? humidBoost : 1.0;
        return warmth * moisture;
    }

    /** Why a plant grows as it does, in a few words, for /ezclimates here. */
    public String reason(double airF, double humidity) {
        if (airF <= stopsBelowF) return "too cold to grow";
        if (airF >= noneAboveF) return "too hot to grow";
        if (airF < fullFromF) return "cool, so slow";
        if (airF > hotAboveF) return "hot, so slow";
        if (Double.isFinite(humidity) && humidity < 30.0) return "dry air, so slower";
        if (Double.isFinite(humidity) && humidity >= 60.0) return "warm and humid, so faster";
        return "comfortable";
    }
}
