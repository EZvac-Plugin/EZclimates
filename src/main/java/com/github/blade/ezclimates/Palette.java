package com.github.blade.ezclimates;

import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Where each stage moves leaves and grass, and how far for each family. A
 * stage pulls a biome's own summer colour toward its target by its share
 * times the family's strength; summer is vanilla.
 *
 * @param foliage  each stage's leaf colour and how far toward it (summer has none)
 * @param grass    each stage's grass colour and how far toward it
 * @param strength each family's strength, 0 to 1
 */
public record Palette(Map<Stage, Target> foliage, Map<Stage, Target> grass, Map<Family, Double> strength) {
    /** A colour and how far toward it, 0 to 1. */
    public record Target(int rgb, double share) {}

    public static Palette defaults() {
        Map<Stage, Target> foliage = new EnumMap<>(Stage.class);
        foliage.put(Stage.SPRING, new Target(0x8CD45A, 0.25));
        foliage.put(Stage.AUTUMN_EARLY, new Target(0xD2B832, 0.55));
        foliage.put(Stage.AUTUMN_LATE, new Target(0xC8562A, 0.75));
        foliage.put(Stage.WINTER, new Target(0x8C7A5A, 0.6));
        Map<Stage, Target> grass = new EnumMap<>(Stage.class);
        grass.put(Stage.SPRING, new Target(0x7ED050, 0.2));
        grass.put(Stage.AUTUMN_EARLY, new Target(0xB4A848, 0.35));
        grass.put(Stage.AUTUMN_LATE, new Target(0xA88C4C, 0.5));
        grass.put(Stage.WINTER, new Target(0x8E8C66, 0.55));
        Map<Family, Double> strength = new EnumMap<>(Family.class);
        for (Family family : Family.values()) strength.put(family, family.strength());
        return new Palette(Map.copyOf(foliage), Map.copyOf(grass), Map.copyOf(strength));
    }

    /** The leaf colour of a biome whose summer leaves are {@code summer}, at a stage. */
    public int foliage(int summer, Stage stage, Family family) {
        return toward(summer, foliage.get(stage), family, 1.0);
    }

    public int grass(int summer, Stage stage, Family family) {
        return toward(summer, grass.get(stage), family, 1.0);
    }

    /** Fallen leaves follow the leaves, half as far. */
    public int dryFoliage(int summer, Stage stage, Family family) {
        return toward(summer, foliage.get(stage), family, 0.5);
    }

    private int toward(int summer, Target target, Family family, double scale) {
        return target == null ? summer : Colours.mix(summer, target.rgb(), target.share() * strength(family) * scale);
    }

    private double strength(Family family) {
        Double value = strength.get(family);
        return value == null ? family.strength() : value;
    }

    /**
     * {@code colours} from config.yml: {@code foliage.<stage>} and
     * {@code grass.<stage>} as {@code { color: "#rrggbb", share: 0.5 }}, and
     * {@code strength.<family>}. Anything unreadable keeps its default.
     */
    public static Palette load(ConfigurationSection section, Consumer<String> warn) {
        Palette d = defaults();
        if (section == null) return d;
        Map<Stage, Target> foliage = new EnumMap<>(d.foliage);
        Map<Stage, Target> grass = new EnumMap<>(d.grass);
        Map<Family, Double> strength = new EnumMap<>(d.strength);
        for (Stage stage : Stage.values()) {
            if (stage == Stage.SUMMER) continue;
            read(section, "foliage." + stage.key(), foliage, stage, warn);
            read(section, "grass." + stage.key(), grass, stage, warn);
        }
        for (Family family : Family.values()) {
            String path = "strength." + family.key();
            if (!section.contains(path)) continue;
            double value = section.getDouble(path, -1);
            if (value < 0.0 || value > 1.0) warn.accept("colours." + path + " must be 0 to 1; kept " + family.strength());
            else strength.put(family, value);
        }
        return new Palette(Map.copyOf(foliage), Map.copyOf(grass), Map.copyOf(strength));
    }

    private static void read(ConfigurationSection section, String path, Map<Stage, Target> into, Stage stage,
                             Consumer<String> warn) {
        if (!section.contains(path)) return;
        Target fallback = into.get(stage);
        try {
            int rgb = Colours.parse(section.getString(path + ".color", Colours.format(fallback.rgb())));
            double share = section.getDouble(path + ".share", fallback.share());
            if (share < 0.0 || share > 1.0) throw new IllegalArgumentException("share must be 0 to 1");
            into.put(stage, new Target(rgb, share));
        } catch (IllegalArgumentException invalid) {
            warn.accept("colours." + path + " is not a colour and share (" + invalid.getMessage() + "); kept the default.");
        }
    }
}
