package com.github.blade.ezclimates;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.function.Consumer;

/**
 * EZclimates' config.yml, read leniently: a value out of range keeps its
 * default with a warning. The colour palette itself is read at start-up, in
 * the bootstrapper, since it shapes the biome copies.
 *
 * @param rainAndSnow    rain and snow where EZvac says
 * @param colours        seasonal leaf and grass colours
 * @param snow           snow settles where it snows, and melts
 * @param ice            water freezes where it's cold enough, and thaws
 * @param skimIce        a hard frost ices over shallow still water and edges
 * @param skimBelowF     skim ice forms where the air is colder than this, in F
 * @param snowRadius     how far round players snow and ice are placed, in blocks; 0 for the server's simulation distance
 * @param maxLayers      how deep snow gets, in layers
 * @param snowRate       how fast snow settles and water freezes, 1 being vanilla's pace
 * @param holdDays       the coldest days of winter, round its middle, through which snow and skim ice hold; 0 for none
 * @param holdUnlessAboveF through those days, they melt only on a day averaging warmer than this, in F
 * @param linesPerTick   chunks whose 16 snow lines are worked out per tick, over all players
 * @param chunksPerTick  chunks resent to each player per tick when their look changes
 * @param blocksPerTick  columns looked at for new snow and ice per tick
 * @param recordsPerTick recorded blocks looked at for melting per tick
 */
public record Settings(boolean rainAndSnow, boolean colours, boolean snow, boolean ice, boolean skimIce,
                       double skimBelowF, int snowRadius, int maxLayers,
                       double snowRate, int holdDays, double holdUnlessAboveF, int linesPerTick, int chunksPerTick,
                       int blocksPerTick, int recordsPerTick) {
    public static Settings defaults() {
        return new Settings(true, true, true, true, true, 25.0, 0, 2, 1.0, 10, 45.0, 40, 12, 64, 64);
    }

    public static Settings load(FileConfiguration config, Consumer<String> warn) {
        Settings d = defaults();
        return new Settings(
                config.getBoolean("rain-and-snow", d.rainAndSnow),
                config.getBoolean("colours.enabled", d.colours),
                config.getBoolean("snow.enabled", d.snow),
                config.getBoolean("ice.enabled", d.ice),
                config.getBoolean("ice.skim.enabled", d.skimIce),
                skimBelow(config, d.skimBelowF, warn),
                radius(config, d.snowRadius, warn),
                range(config, "snow.max-layers", d.maxLayers, 1, 8, warn),
                rate(config, d.snowRate, warn),
                range(config, "snow.melt.deep-winter-hold-days", d.holdDays, 0, 120, warn),
                holdAbove(config, d.holdUnlessAboveF, warn),
                range(config, "budget.snow-lines-per-tick", d.linesPerTick, 4, 1000, warn),
                range(config, "budget.chunks-resent-per-tick", d.chunksPerTick, 1, 100, warn),
                range(config, "budget.blocks-per-tick", d.blocksPerTick, 1, 500, warn),
                range(config, "budget.records-per-tick", d.recordsPerTick, 1, 2000, warn));
    }

    private static double skimBelow(FileConfiguration config, double fallback, Consumer<String> warn) {
        double value = config.getDouble("ice.skim.below-f", fallback);
        if (value >= -20.0 && value <= 32.0) return value;
        warn.accept("ice.skim.below-f must be -20 to 32; using " + fallback + ".");
        return fallback;
    }

    private static int radius(FileConfiguration config, int fallback, Consumer<String> warn) {
        int value = config.getInt("snow.radius-blocks", fallback);
        if (value == 0 || value >= 16 && value <= 512) return value;
        warn.accept("snow.radius-blocks must be 0 (the simulation distance) or 16 to 512; using " + fallback + ".");
        return fallback;
    }

    private static double holdAbove(FileConfiguration config, double fallback, Consumer<String> warn) {
        double value = config.getDouble("snow.melt.hold-unless-above-f", fallback);
        if (value >= 34.0 && value <= 80.0) return value;
        warn.accept("snow.melt.hold-unless-above-f must be 34 to 80; using " + fallback + ".");
        return fallback;
    }

    private static double rate(FileConfiguration config, double fallback, Consumer<String> warn) {
        double value = config.getDouble("snow.rate", fallback);
        if (value >= 0.1 && value <= 20.0) return value;
        warn.accept("snow.rate must be 0.1 to 20; using " + fallback + ".");
        return fallback;
    }

    private static int range(FileConfiguration config, String path, int fallback, int low, int high, Consumer<String> warn) {
        int value = config.getInt(path, fallback);
        if (value >= low && value <= high) return value;
        warn.accept(path + " must be " + low + " to " + high + "; using " + fallback + ".");
        return fallback;
    }
}
