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
 * @param snowRadius     how far round players snow and ice are placed, in blocks
 * @param maxLayers      how deep snow gets, in layers
 * @param linesPerTick   snow lines worked out per tick, over all players
 * @param chunksPerTick  chunks resent to each player per tick when their look changes
 * @param blocksPerTick  columns looked at for new snow and ice per tick
 * @param recordsPerTick recorded blocks looked at for melting per tick
 */
public record Settings(boolean rainAndSnow, boolean colours, boolean snow, boolean ice, int snowRadius, int maxLayers,
                       int linesPerTick, int chunksPerTick, int blocksPerTick, int recordsPerTick) {
    public static Settings defaults() {
        return new Settings(true, true, true, true, 64, 2, 200, 12, 24, 64);
    }

    public static Settings load(FileConfiguration config, Consumer<String> warn) {
        Settings d = defaults();
        return new Settings(
                config.getBoolean("rain-and-snow", d.rainAndSnow),
                config.getBoolean("colours.enabled", d.colours),
                config.getBoolean("snow.enabled", d.snow),
                config.getBoolean("ice.enabled", d.ice),
                range(config, "snow.radius-blocks", d.snowRadius, 16, 160, warn),
                range(config, "snow.max-layers", d.maxLayers, 1, 8, warn),
                range(config, "budget.snow-lines-per-tick", d.linesPerTick, 10, 5000, warn),
                range(config, "budget.chunks-resent-per-tick", d.chunksPerTick, 1, 100, warn),
                range(config, "budget.blocks-per-tick", d.blocksPerTick, 1, 500, warn),
                range(config, "budget.records-per-tick", d.recordsPerTick, 1, 2000, warn));
    }

    private static int range(FileConfiguration config, String path, int fallback, int low, int high, Consumer<String> warn) {
        int value = config.getInt(path, fallback);
        if (value >= low && value <= high) return value;
        warn.accept(path + " must be " + low + " to " + high + "; using " + fallback + ".");
        return fallback;
    }
}
