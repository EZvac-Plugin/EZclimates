package com.github.blade.ezclimates.body.model;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * Brings a server's config.yml up to date with the bundled one: settings
 * added since it was written arrive with their comments, and nothing the
 * server set is changed. The one exception is a value that is still the
 * stock value of an older release, where the stock value itself changed:
 * that moves to the new one. Pure.
 */
public final class ConfigUpgrade {
    private ConfigUpgrade() {}

    /** 0.1.0's shipped body rates; 0.2.0 doubled them. */
    static final double OLD_COLD = 0.034, OLD_HEAT = 0.027;

    /** @return what changed, empty when the file is already up to date */
    public static List<String> apply(YamlConfiguration config, YamlConfiguration bundled) {
        List<String> changed = new ArrayList<>();
        for (String path : bundled.getKeys(true)) {
            if (config.contains(path, true)) continue;
            if (bundled.isConfigurationSection(path)) config.createSection(path);
            else config.set(path, bundled.get(path));
            config.setComments(path, bundled.getComments(path));
            config.setInlineComments(path, bundled.getInlineComments(path));
            // A new section brings everything under it, so report the section alone.
            if (changed.stream().noneMatch(parent -> path.startsWith(parent + "."))) changed.add(path);
        }
        if (config.getDouble("rates.cold-f-per-minute") == OLD_COLD
                && config.getDouble("rates.heat-f-per-minute") == OLD_HEAT) {
            config.set("rates.cold-f-per-minute", bundled.getDouble("rates.cold-f-per-minute"));
            config.set("rates.heat-f-per-minute", bundled.getDouble("rates.heat-f-per-minute"));
            changed.add("rates (the stock rates, now twice as fast)");
        }
        return changed;
    }
}
