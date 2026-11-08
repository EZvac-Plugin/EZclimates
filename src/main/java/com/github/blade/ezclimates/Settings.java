package com.github.blade.ezclimates;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.function.Consumer;

/**
 * EZclimates' config.yml, read leniently: a value out of range keeps its
 * default with a warning. The colour palette itself is read at start-up, in
 * the bootstrapper, since it shapes the biome copies.
 *
 * @param rainAndSnow   rain and snow where EZvac says
 * @param colours       seasonal leaf and grass colours
 * @param linesPerTick  snow lines worked out per tick, over all players
 * @param chunksPerTick chunks resent to each player per tick when their look changes
 */
public record Settings(boolean rainAndSnow, boolean colours, int linesPerTick, int chunksPerTick) {
    public static Settings defaults() {
        return new Settings(true, true, 200, 12);
    }

    public static Settings load(FileConfiguration config, Consumer<String> warn) {
        Settings d = defaults();
        return new Settings(
                config.getBoolean("rain-and-snow", d.rainAndSnow),
                config.getBoolean("colours.enabled", d.colours),
                range(config, "budget.snow-lines-per-tick", d.linesPerTick, 10, 5000, warn),
                range(config, "budget.chunks-resent-per-tick", d.chunksPerTick, 1, 100, warn));
    }

    private static int range(FileConfiguration config, String path, int fallback, int low, int high, Consumer<String> warn) {
        int value = config.getInt(path, fallback);
        if (value >= low && value <= high) return value;
        warn.accept(path + " must be " + low + " to " + high + "; using " + fallback + ".");
        return fallback;
    }
}
