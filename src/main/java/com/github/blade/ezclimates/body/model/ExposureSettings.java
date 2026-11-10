package com.github.blade.ezclimates.body.model;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Every setting from body.yml, validated. A section with a bad value falls
 * back to its defaults with a warning; the rest still load as written.
 * Temperatures are Fahrenheit, as EZvac's API gives them.
 */
public record ExposureSettings(Comfort comfort, Rates rates, Balance balance, Thresholds thresholds, Damage damage,
                               ColdResistance coldResistance, Scope scope, Sickness sickness,
                               Hud hud) {

    /**
     * @param lowF  below this the body cools
     * @param highF above this it warms; in between it recovers toward its resting point
     */
    public record Comfort(double lowF, double highF) {
        public Comfort {
            finite("comfort.low-f", lowF);
            finite("comfort.high-f", highF);
            if (lowF >= highF) throw new IllegalArgumentException("comfort.low-f must be below comfort.high-f");
        }
    }

    /**
     * @param coldFPerMinute     body cooling per minute, per degree below comfort
     * @param heatFPerMinute     body warming per minute, per degree above comfort
     * @param recoveryFPerMinute how fast the body returns to its resting point when comfortable
     * @param waterFactor        how much faster water moves body heat than air
     */
    public record Rates(double coldFPerMinute, double heatFPerMinute, double recoveryFPerMinute, double waterFactor) {
        public Rates {
            nonNegative("rates.cold-f-per-minute", coldFPerMinute);
            nonNegative("rates.heat-f-per-minute", heatFPerMinute);
            nonNegative("rates.recovery-f-per-minute", recoveryFPerMinute);
            if (!Double.isFinite(waterFactor) || waterFactor < 1.0)
                throw new IllegalArgumentException("rates.water-factor must be at least 1");
        }
    }

    /**
     * Where the body settles. Every degree it feels past the comfort band moves
     * the body's balance point this far from its resting temperature, and the
     * body heads there and stays. So a warm day only ever makes it run a bit
     * warm, and only real extremes can bring on heatstroke or freezing.
     */
    public record Balance(double heatFPerF, double coldFPerF) {
        public Balance {
            positive("balance.heat-f-per-f", heatFPerF);
            positive("balance.cold-f-per-f", coldFPerF);
        }
    }

    /** Body temperatures where each stage starts. */
    public record Thresholds(double chilledF, double hypothermiaF, double severeHypothermiaF, double freezingF,
                             double overheatingF, double heatExhaustionF, double severeHeatF, double heatstrokeF) {
        public static final double REST_F = 98.6;
        public static final double MINIMUM_F = 85.0;
        public static final double MAXIMUM_F = 108.0;

        public Thresholds {
            if (!(freezingF < severeHypothermiaF && severeHypothermiaF < hypothermiaF && hypothermiaF < chilledF
                    && chilledF < REST_F))
                throw new IllegalArgumentException("stages: the cold stages must fall in order, all below 98.6");
            if (!(REST_F < overheatingF && overheatingF < heatExhaustionF && heatExhaustionF < severeHeatF
                    && severeHeatF < heatstrokeF))
                throw new IllegalArgumentException("stages: the heat stages must rise in order, all above 98.6");
            if (freezingF <= MINIMUM_F || heatstrokeF >= MAXIMUM_F)
                throw new IllegalArgumentException("stages must lie between 85 and 108 F");
        }
    }

    /**
     * @param lethal          false keeps players alive at {@code floorHealth}
     * @param floorHealth     health heat and cold never take a player below when not lethal (1.0 = half a heart)
     * @param amount          damage per hit in the freezing and heatstroke stages
     * @param intervalSeconds time between hits
     */
    public record Damage(boolean lethal, double floorHealth, double amount, double intervalSeconds) {
        public Damage {
            if (!Double.isFinite(floorHealth) || floorHealth < 0.5)
                throw new IllegalArgumentException("damage.floor must be at least 0.5");
            positive("damage.amount", amount);
            positive("damage.interval-seconds", intervalSeconds);
        }
    }

    /** Points each leather piece gives against cold, and what a point is worth. */
    public record ColdResistance(double helmet, double chestplate, double leggings, double boots, double fPerPoint) {
        public ColdResistance {
            nonNegative("cold-resistance.helmet", helmet);
            nonNegative("cold-resistance.chestplate", chestplate);
            nonNegative("cold-resistance.leggings", leggings);
            nonNegative("cold-resistance.boots", boots);
            nonNegative("cold-resistance.f-per-point", fPerPoint);
        }

        /** Degrees F of extra cold tolerance for the leather pieces worn. */
        public double resistanceF(boolean helmetWorn, boolean chestplateWorn, boolean leggingsWorn, boolean bootsWorn) {
            double points = (helmetWorn ? helmet : 0) + (chestplateWorn ? chestplate : 0)
                    + (leggingsWorn ? leggings : 0) + (bootsWorn ? boots : 0);
            return points * fPerPoint;
        }
    }

    /**
     * Who is affected.
     *
     * @param enabledWorlds  worlds where it applies; empty means every world
     * @param disabledWorlds worlds where it never applies
     * @param gameModes      game modes it applies in, upper case
     */
    public record Scope(Set<String> enabledWorlds, Set<String> disabledWorlds, Set<String> gameModes) {
        public Scope {
            enabledWorlds = Set.copyOf(enabledWorlds);
            disabledWorlds = Set.copyOf(disabledWorlds);
            gameModes = Set.copyOf(gameModes);
        }

        public boolean appliesIn(String world, String gameMode) {
            if (disabledWorlds.contains(world)) return false;
            if (!enabledWorlds.isEmpty() && !enabledWorlds.contains(world)) return false;
            return gameModes.contains(gameMode.toUpperCase(Locale.ROOT));
        }
    }

    /**
     * The optional sickness system.
     *
     * @param chances           daily chance by season: spring, summer, autumn, winter, and with no seasons
     * @param coldMultiplier    a day spent entirely cold multiplies the chance by this
     * @param contagionRange    blocks from a sick player that count as contact
     * @param contagionSeconds  contact needed in a day before it counts
     * @param contagionBonus    added to the chance after contact (0.35 = 35 points)
     * @param maximumChance     the chance never goes above this
     * @param severityWeights   mild, moderate, severe
     * @param feverF            resting body temperature with a mild, moderate, severe fever
     * @param heatMultipliers   how much faster heat warms a feverish body: mild, moderate, severe
     * @param nauseaMinutes     minutes between nausea bursts: mild, moderate, severe
     * @param waterDaysPerBottle days a water bottle takes off
     * @param waterMaximumDays  days water can take off one illness
     */
    public record Sickness(boolean enabled, double[] chances, double coldMultiplier, double contagionRange,
                           double contagionSeconds, double contagionBonus, double maximumChance,
                           double[] severityWeights, double[] feverF, double[] heatMultipliers,
                           double[] nauseaMinutes, int minimumDays, int maximumDays,
                           double waterDaysPerBottle, double waterMaximumDays) {
        public Sickness {
            if (chances.length != 5) throw new IllegalArgumentException("sickness.chance needs spring, summer, autumn, winter and no-seasons");
            for (double chance : chances) fraction("sickness.chance", chance);
            if (!Double.isFinite(coldMultiplier) || coldMultiplier < 1.0)
                throw new IllegalArgumentException("sickness.cold-multiplier must be at least 1");
            positive("sickness.contagion.range-blocks", contagionRange);
            nonNegative("sickness.contagion.seconds", contagionSeconds);
            fraction("sickness.contagion.bonus", contagionBonus);
            fraction("sickness.maximum-chance", maximumChance);
            three("sickness.severity", severityWeights);
            for (double weight : severityWeights) nonNegative("sickness.severity", weight);
            if (severityWeights[0] + severityWeights[1] + severityWeights[2] <= 0)
                throw new IllegalArgumentException("sickness.severity weights must not all be 0");
            three("sickness.fever-f", feverF);
            for (double fever : feverF)
                if (!(fever > Thresholds.REST_F && fever < Thresholds.MAXIMUM_F))
                    throw new IllegalArgumentException("sickness.fever-f must be above 98.6 and below 108");
            three("sickness.heat-multiplier", heatMultipliers);
            for (double multiplier : heatMultipliers)
                if (!(multiplier >= 1.0)) throw new IllegalArgumentException("sickness.heat-multiplier must be at least 1");
            three("sickness.nausea-every-minutes", nauseaMinutes);
            for (double minutes : nauseaMinutes) positive("sickness.nausea-every-minutes", minutes);
            if (minimumDays < 1 || maximumDays < minimumDays)
                throw new IllegalArgumentException("sickness.days needs 1 <= minimum <= maximum");
            nonNegative("sickness.water.days-per-bottle", waterDaysPerBottle);
            nonNegative("sickness.water.maximum-days", waterMaximumDays);
            chances = chances.clone();
            severityWeights = severityWeights.clone();
            feverF = feverF.clone();
            heatMultipliers = heatMultipliers.clone();
            nauseaMinutes = nauseaMinutes.clone();
        }
    }

    /**
     * The body temperature on EZvac's climate HUD (EZvac API 1.1 and newer).
     *
     * @param lines      sidebar lines, MiniMessage, with {body} {meter} {stage} {fever}
     * @param inline     the same on one line, for action-bar and boss-bar HUDs
     * @param feverText  what {fever} shows while ill
     * @param meterColdF the body temperature where the meter is empty
     * @param meterHotF  where it is full
     */
    public record Hud(boolean enabled, List<String> lines, String inline, String feverText,
                      double meterColdF, double meterHotF, String normalColor, String chilledColor,
                      String coldColor, String warmColor, String hotColor) {
        public static final List<String> LINES = List.of("<gray>Body: <white>{body}{fever}", "{meter} {stage}");
        public static final String INLINE = "<gray>Body <white>{body}</white> {stage}";

        public Hud {
            lines = List.copyOf(lines);
            if (lines.size() > 4) throw new IllegalArgumentException("hud.lines has room for at most 4 lines");
            if (inline == null || feverText == null) throw new IllegalArgumentException("hud.inline and hud.fever must be text");
            finite("hud.meter.cold-f", meterColdF);
            finite("hud.meter.hot-f", meterHotF);
            if (meterColdF >= meterHotF) throw new IllegalArgumentException("hud.meter.cold-f must be below hud.meter.hot-f");
            for (String color : List.of(normalColor, chilledColor, coldColor, warmColor, hotColor))
                if (color == null || TextColor.fromHexString(color) == null)
                    throw new IllegalArgumentException("hud.colors must be #RRGGBB, got '" + color + "'");
        }
    }

    private static Hud defaultHud(boolean enabled) {
        return new Hud(enabled, Hud.LINES, Hud.INLINE, " <gray>(fever)", 91.0, 106.0,
                "#6ee07a", "#8fd3ff", "#5ab0ff", "#ffb347", "#ff5c5c");
    }

    public static ExposureSettings defaults() {
        return load(new MemoryConfiguration(), warning -> { });
    }

    /** Reads body.yml; a section with a bad value warns and uses its defaults. */
    public static ExposureSettings load(ConfigurationSection config, Consumer<String> warn) {
        Comfort comfort = section("comfort", warn, () -> new Comfort(config.getDouble("comfort.low-f", 50.0),
                config.getDouble("comfort.high-f", 86.0)), () -> new Comfort(50.0, 86.0));
        Rates rates = section("rates", warn, () -> new Rates(config.getDouble("rates.cold-f-per-minute", 0.068),
                config.getDouble("rates.heat-f-per-minute", 0.054),
                config.getDouble("rates.recovery-f-per-minute", 0.7),
                config.getDouble("rates.water-factor", 3.0)), () -> new Rates(0.068, 0.054, 0.7, 3.0));
        Balance balance = section("balance", warn, () -> new Balance(config.getDouble("balance.heat-f-per-f", 0.25),
                config.getDouble("balance.cold-f-per-f", 0.25)), () -> new Balance(0.25, 0.25));
        Thresholds thresholds = section("stages", warn, () -> new Thresholds(
                config.getDouble("stages.chilled-below-f", 97.0),
                config.getDouble("stages.hypothermia-below-f", 95.0),
                config.getDouble("stages.severe-hypothermia-below-f", 93.0),
                config.getDouble("stages.freezing-below-f", 91.0),
                config.getDouble("stages.overheating-above-f", 99.5),
                config.getDouble("stages.heat-exhaustion-above-f", 100.5),
                config.getDouble("stages.severe-heat-above-f", 102.0),
                config.getDouble("stages.heatstroke-above-f", 104.0)),
                () -> new Thresholds(97.0, 95.0, 93.0, 91.0, 99.5, 100.5, 102.0, 104.0));
        Damage damage = section("damage", warn, () -> new Damage(config.getBoolean("lethal", true),
                config.getDouble("damage.floor", 1.0), config.getDouble("damage.amount", 1.0),
                config.getDouble("damage.interval-seconds", 4.0)), () -> new Damage(true, 1.0, 1.0, 4.0));
        ColdResistance cold = section("cold-resistance", warn, () -> new ColdResistance(
                config.getDouble("cold-resistance.helmet", 1.5), config.getDouble("cold-resistance.chestplate", 3.5),
                config.getDouble("cold-resistance.leggings", 3.5), config.getDouble("cold-resistance.boots", 1.5),
                config.getDouble("cold-resistance.f-per-point", 1.5)), () -> new ColdResistance(1.5, 3.5, 3.5, 1.5, 1.5));
        Scope scope = new Scope(Set.copyOf(config.getStringList("worlds.enabled")),
                Set.copyOf(config.getStringList("worlds.disabled")),
                (config.isList("game-modes") ? config.getStringList("game-modes") : List.of("SURVIVAL", "ADVENTURE"))
                        .stream().map(mode -> mode.toUpperCase(Locale.ROOT)).collect(Collectors.toSet()));
        Sickness sickness = section("sickness", warn, () -> new Sickness(config.getBoolean("sickness.enabled", true),
                new double[] {config.getDouble("sickness.chance.spring", 0.03), config.getDouble("sickness.chance.summer", 0.01),
                        config.getDouble("sickness.chance.autumn", 0.04), config.getDouble("sickness.chance.winter", 0.08),
                        config.getDouble("sickness.chance.no-seasons", 0.03)},
                config.getDouble("sickness.cold-multiplier", 4.0),
                config.getDouble("sickness.contagion.range-blocks", 10.0),
                config.getDouble("sickness.contagion.seconds", 30.0),
                config.getDouble("sickness.contagion.bonus", 0.35),
                config.getDouble("sickness.maximum-chance", 0.95),
                triple(config, "sickness.severity", 0.6, 0.3, 0.1),
                triple(config, "sickness.fever-f", 99.4, 100.0, 100.6),
                triple(config, "sickness.heat-multiplier", 1.5, 2.0, 2.5),
                triple(config, "sickness.nausea-every-minutes", 5.0, 3.0, 2.0),
                config.getInt("sickness.days.minimum", 2), config.getInt("sickness.days.maximum", 3),
                config.getDouble("sickness.water.days-per-bottle", 0.25),
                config.getDouble("sickness.water.maximum-days", 1.0)),
                () -> defaultSickness(config.getBoolean("sickness.enabled", true)));
        Hud hud = section("hud", warn, () -> new Hud(config.getBoolean("hud.enabled", true),
                config.isList("hud.lines") ? config.getStringList("hud.lines") : Hud.LINES,
                config.getString("hud.inline", Hud.INLINE), config.getString("hud.fever", " <gray>(fever)"),
                config.getDouble("hud.meter.cold-f", 91.0), config.getDouble("hud.meter.hot-f", 106.0),
                config.getString("hud.colors.normal", "#6ee07a"), config.getString("hud.colors.chilled", "#8fd3ff"),
                config.getString("hud.colors.cold", "#5ab0ff"), config.getString("hud.colors.warm", "#ffb347"),
                config.getString("hud.colors.hot", "#ff5c5c")),
                () -> defaultHud(config.getBoolean("hud.enabled", true)));
        return new ExposureSettings(comfort, rates, balance, thresholds, damage, cold, scope, sickness, hud);
    }

    private static Sickness defaultSickness(boolean enabled) {
        return new Sickness(enabled, new double[] {0.03, 0.01, 0.04, 0.08, 0.03}, 4.0, 10.0, 30.0, 0.35, 0.95,
                new double[] {0.6, 0.3, 0.1}, new double[] {99.4, 100.0, 100.6}, new double[] {1.5, 2.0, 2.5},
                new double[] {5.0, 3.0, 2.0}, 2, 3, 0.25, 1.0);
    }

    /** mild, moderate and severe values of one setting. */
    private static double[] triple(ConfigurationSection config, String path, double mild, double moderate, double severe) {
        return new double[] {config.getDouble(path + ".mild", mild), config.getDouble(path + ".moderate", moderate),
                config.getDouble(path + ".severe", severe)};
    }

    private static <T> T section(String name, Consumer<String> warn, Supplier<T> reader, Supplier<T> fallback) {
        try {
            return reader.get();
        } catch (IllegalArgumentException invalid) {
            warn.accept(invalid.getMessage() + ", so every " + name + " setting uses its default until body.yml is fixed.");
            return fallback.get();
        }
    }

    private static void finite(String name, double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be a number");
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

    private static void three(String name, double[] values) {
        if (values.length != 3) throw new IllegalArgumentException(name + " needs mild, moderate and severe");
    }
}
