package com.github.blade.ezclimates.body.model;

import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * What a body meets besides the air (body.yml): getting wet, the warmth of
 * fires, warm and cool food, metal armor in the sun, exertion, breath in the
 * cold and sleeping cold. Each can be switched off. Pure.
 */
public record Surroundings(Wetness wetness, Fires fires, Food food, Armor armor, Exertion exertion, Breath breath,
                           Sleep sleep, boolean darkness) {

    /**
     * Rain and snow soak a body in the open, water at once; being wet makes the
     * cold colder and the heat a little milder, and it dries by warmth.
     *
     * @param soakMinutes  minutes of rain in the open from dry to soaked
     * @param snowShare    how much of rain's soaking falling snow does
     * @param dryMinutes   minutes from soaked to dry, out of the rain, at a comfortable 60 F
     * @param colderF      how much colder the cold feels, soaked
     * @param coldRateBonus how much faster a soaked body loses heat to the cold (1 = twice as fast)
     * @param coolerF      how much milder the heat feels, soaked
     */
    public record Wetness(boolean enabled, double soakMinutes, double snowShare, double dryMinutes, double colderF,
                          double coldRateBonus, double coolerF) {
        public Wetness {
            positive("wetness.soaked-after-minutes", soakMinutes);
            fraction("wetness.snow-share", snowShare);
            positive("wetness.dry-minutes", dryMinutes);
            nonNegative("wetness.colder-by-f", colderF);
            nonNegative("wetness.cold-rate-bonus", coldRateBonus);
            nonNegative("wetness.milder-heat-by-f", coolerF);
        }
    }

    /**
     * Campfires, fire, lava and magma warm a body near them, more the closer.
     *
     * @param radius  blocks round the body looked at
     * @param warmthF each kind's warmth right beside it, by block name (campfire, lava, ...)
     * @param mostF   the most warmth fires can add together
     * @param rewarmPerF how much faster a chilled body warms back up, per degree of a fire's warmth (0.07: 7%)
     */
    public record Fires(boolean enabled, int radius, Map<String, Double> warmthF, double mostF, double rewarmPerF) {
        public Fires {
            nonNegative("fires.rewarm-per-f", rewarmPerF);
            if (radius < 1 || radius > 8) throw new IllegalArgumentException("fires.radius must be 1 to 8");
            warmthF = Map.copyOf(warmthF);
            for (Map.Entry<String, Double> kind : warmthF.entrySet())
                nonNegative("fires.warmth-f." + kind.getKey(), kind.getValue());
            nonNegative("fires.most-f", mostF);
        }
    }

    /** One food's effect: degrees and minutes. */
    public record Meal(double f, double minutes) {}

    /**
     * Warm food holds the cold off for a while, and cool food the heat.
     *
     * @param warm warm foods by item name: how many degrees against the cold, for how long
     * @param cool cool foods by item name (water_bottle for a water bottle): degrees against the heat
     */
    public record Food(boolean enabled, Map<String, Meal> warm, Map<String, Meal> cool) {
        public Food {
            warm = Map.copyOf(warm);
            cool = Map.copyOf(cool);
        }
    }

    /** Metal armor bakes in direct sun: degrees per piece worn, only against the heat. */
    public record Armor(boolean enabled, double sunFPerPiece) {
        public Armor {
            nonNegative("armor.sun-f-per-piece", sunFPerPiece);
        }
    }

    /**
     * Sprinting, swimming and mining warm the body: up to {@code mostF},
     * built up over {@code buildSeconds} of work and gone {@code restSeconds}
     * after it stops.
     */
    public record Exertion(boolean enabled, double mostF, double buildSeconds, double restSeconds) {
        public Exertion {
            nonNegative("exertion.most-f", mostF);
            positive("exertion.build-up-seconds", buildSeconds);
            positive("exertion.cool-down-seconds", restSeconds);
        }
    }

    /** Breath shows below this air temperature, every few seconds. */
    public record Breath(boolean enabled, double belowF, double everySeconds) {
        public Breath {
            positive("breath.every-seconds", everySeconds);
        }
    }

    /**
     * Sleeping where the body feels cold wakes it chilled, and counts toward
     * catching a cold.
     *
     * @param coldMinutes how many minutes out in the cold a cold night counts as, for sickness
     */
    public record Sleep(boolean enabled, double coldMinutes) {
        public Sleep {
            nonNegative("sleep.counts-as-cold-minutes", coldMinutes);
        }
    }

    public static Surroundings defaults() {
        return load(new org.bukkit.configuration.MemoryConfiguration(), warning -> { });
    }

    public static Surroundings load(ConfigurationSection config, Consumer<String> warn) {
        Wetness wetness = section("wetness", warn, () -> new Wetness(config.getBoolean("wetness.enabled", true),
                config.getDouble("wetness.soaked-after-minutes", 3.0), config.getDouble("wetness.snow-share", 0.4),
                config.getDouble("wetness.dry-minutes", 8.0), config.getDouble("wetness.colder-by-f", 12.0),
                config.getDouble("wetness.cold-rate-bonus", 1.0), config.getDouble("wetness.milder-heat-by-f", 4.0)),
                () -> new Wetness(config.getBoolean("wetness.enabled", true), 3.0, 0.4, 8.0, 12.0, 1.0, 4.0));
        Fires fires = section("fires", warn, () -> new Fires(config.getBoolean("fires.enabled", true),
                config.getInt("fires.radius", 4), numbers(config, "fires.warmth-f", DEFAULT_FIRES),
                config.getDouble("fires.most-f", 80.0), config.getDouble("fires.rewarm-per-f", 0.07)),
                () -> new Fires(config.getBoolean("fires.enabled", true), 4, DEFAULT_FIRES, 80.0, 0.07));
        Food food = section("food", warn, () -> new Food(config.getBoolean("food.enabled", true),
                meals(config, "food.warm", DEFAULT_WARM), meals(config, "food.cool", DEFAULT_COOL)),
                () -> new Food(config.getBoolean("food.enabled", true), DEFAULT_WARM, DEFAULT_COOL));
        Armor armor = section("armor", warn, () -> new Armor(config.getBoolean("armor.enabled", true),
                config.getDouble("armor.sun-f-per-piece", 2.5)),
                () -> new Armor(config.getBoolean("armor.enabled", true), 2.5));
        Exertion exertion = section("exertion", warn, () -> new Exertion(config.getBoolean("exertion.enabled", true),
                config.getDouble("exertion.most-f", 8.0), config.getDouble("exertion.build-up-seconds", 30.0),
                config.getDouble("exertion.cool-down-seconds", 45.0)),
                () -> new Exertion(config.getBoolean("exertion.enabled", true), 8.0, 30.0, 45.0));
        Breath breath = section("breath", warn, () -> new Breath(config.getBoolean("breath.enabled", true),
                config.getDouble("breath.below-f", 40.0), config.getDouble("breath.every-seconds", 3.0)),
                () -> new Breath(config.getBoolean("breath.enabled", true), 40.0, 3.0));
        Sleep sleep = section("sleep", warn, () -> new Sleep(config.getBoolean("sleep.enabled", true),
                config.getDouble("sleep.counts-as-cold-minutes", 20.0)),
                () -> new Sleep(config.getBoolean("sleep.enabled", true), 20.0));
        return new Surroundings(wetness, fires, food, armor, exertion, breath, sleep,
                config.getBoolean("darkness-when-dying", true));
    }

    static final Map<String, Double> DEFAULT_FIRES = Map.of("campfire", 60.0, "soul_campfire", 45.0, "fire", 45.0,
            "soul_fire", 30.0, "lava", 70.0, "magma_block", 15.0);

    static final Map<String, Meal> DEFAULT_WARM = Map.ofEntries(
            Map.entry("mushroom_stew", new Meal(12.0, 5.0)), Map.entry("rabbit_stew", new Meal(15.0, 6.0)),
            Map.entry("beetroot_soup", new Meal(12.0, 5.0)), Map.entry("suspicious_stew", new Meal(12.0, 5.0)),
            Map.entry("baked_potato", new Meal(6.0, 2.0)), Map.entry("cooked_beef", new Meal(5.0, 2.0)),
            Map.entry("cooked_porkchop", new Meal(5.0, 2.0)), Map.entry("cooked_chicken", new Meal(5.0, 2.0)),
            Map.entry("cooked_mutton", new Meal(5.0, 2.0)), Map.entry("cooked_rabbit", new Meal(5.0, 2.0)),
            Map.entry("cooked_cod", new Meal(4.0, 2.0)), Map.entry("cooked_salmon", new Meal(4.0, 2.0)),
            Map.entry("pumpkin_pie", new Meal(4.0, 2.0)));

    static final Map<String, Meal> DEFAULT_COOL = Map.of(
            "melon_slice", new Meal(6.0, 3.0), "sweet_berries", new Meal(3.0, 2.0),
            "glow_berries", new Meal(3.0, 2.0), "water_bottle", new Meal(6.0, 3.0));

    private static Map<String, Double> numbers(ConfigurationSection config, String path, Map<String, Double> fallback) {
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) return fallback;
        Map<String, Double> read = new HashMap<>();
        for (String key : section.getKeys(false)) {
            if (!section.isDouble(key) && !section.isInt(key))
                throw new IllegalArgumentException(path + "." + key + " must be a number");
            read.put(key.toLowerCase(Locale.ROOT), section.getDouble(key));
        }
        return read;
    }

    /** Foods as {@code name: [degrees, minutes]}. */
    private static Map<String, Meal> meals(ConfigurationSection config, String path, Map<String, Meal> fallback) {
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) return fallback;
        Map<String, Meal> read = new HashMap<>();
        for (String key : section.getKeys(false)) {
            java.util.List<?> pair = section.getList(key);
            if (pair == null || pair.size() != 2 || !(pair.get(0) instanceof Number f) || !(pair.get(1) instanceof Number m)
                    || f.doubleValue() < 0 || m.doubleValue() <= 0)
                throw new IllegalArgumentException(path + "." + key + " must be [degrees, minutes], such as [12, 5]");
            read.put(key.toLowerCase(Locale.ROOT), new Meal(f.doubleValue(), m.doubleValue()));
        }
        return read;
    }

    // ---- The arithmetic ----

    /**
     * Wetness (0 dry .. 1 soaked) after {@code seconds}: soaked at once in
     * water; in the open in rain it soaks over {@code soakMinutes} (snow
     * slower, a thunderstorm faster); otherwise it dries, faster the warmer
     * it feels, by a fire and in the sun.
     *
     * @param rain 0 none, {@code snowShare} for snow, 1 rain, 1.5 a thunderstorm, on the body in the open
     */
    public static double wetness(double wet, boolean inWater, double rain, double feelsF, boolean byFire,
                                 boolean inSun, double seconds, Wetness settings) {
        if (!settings.enabled()) return 0.0;
        if (inWater) return 1.0;
        if (!Double.isFinite(wet)) wet = 0.0;
        if (rain > 0.0) return clamp01(wet + rain * seconds / (settings.soakMinutes() * 60.0));
        double warmth = Math.max(0.25, Math.min(4.0, 1.0 + (feelsF - 60.0) / 20.0));
        double rate = warmth * (byFire ? 3.0 : 1.0) * (inSun ? 1.5 : 1.0) / (settings.dryMinutes() * 60.0);
        return clamp01(wet - rate * seconds);
    }

    /** How strongly falling weather wets a body in the open: by its kind, 0 under cover or in clear weather. */
    public static double rainOn(String weather, boolean open, Wetness settings) {
        if (!open || weather == null) return 0.0;
        return switch (weather) {
            case "Rain" -> 1.0;
            case "Thunder", "Thunderstorm" -> 1.5;
            case "Snow", "Snowstorm" -> settings.snowShare();
            default -> 0.0;
        };
    }

    /** "Damp", "Wet" or "Soaked", or empty when dry enough not to matter. */
    public static String wetWord(double wet) {
        if (wet >= 0.75) return "Soaked";
        if (wet >= 0.35) return "Wet";
        if (wet >= 0.1) return "Damp";
        return "";
    }

    /** One fire's warmth at a distance: its full warmth beside it, nothing past the radius. */
    public static double fireWarmthF(double warmthF, double distance, int radius) {
        if (!(distance >= 0.0) || distance > radius + 0.5) return 0.0;
        return warmthF * Math.max(0.0, 1.0 - Math.max(0.0, distance - 1.0) / radius);
    }

    /** Exertion (0 .. 1) after {@code seconds}: building while working, fading while resting. */
    public static double exertion(double level, boolean working, double seconds, Exertion settings) {
        if (!settings.enabled()) return 0.0;
        if (!Double.isFinite(level)) level = 0.0;
        return clamp01(working ? level + seconds / settings.buildSeconds() : level - seconds / settings.restSeconds());
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static <T> T section(String name, Consumer<String> warn, java.util.function.Supplier<T> reader,
                                 java.util.function.Supplier<T> fallback) {
        try {
            return reader.get();
        } catch (IllegalArgumentException invalid) {
            warn.accept(invalid.getMessage() + ", so every " + name + " setting uses its default until body.yml is fixed.");
            return fallback.get();
        }
    }

    private static void positive(String name, double value) {
        if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException(name + " must be above 0");
    }

    private static void nonNegative(String name, double value) {
        if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException(name + " must not be negative");
    }

    private static void fraction(String name, double value) {
        if (!Double.isFinite(value) || value < 0 || value > 1)
            throw new IllegalArgumentException(name + " must be between 0 and 1");
    }
}
