package com.github.blade.ezclimates;

import com.github.blade.hvac.api.SeasonView;
import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * How EZclimates plans the weather, from config.yml's {@code weather}
 * section, read leniently: a value out of range keeps its default with a
 * warning.
 *
 * @param enabled             EZclimates plans and drives the weather; false leaves Minecraft's alone
 * @param seasons             each season's dry spells, fronts and thunder
 * @param snowstormChance     the share of winter's fronts that are snowstorms
 * @param heatWave            heat waves: how often, how long, how strong
 * @param coldSnap            cold snaps, the same
 * @param exactDays           days from today the plan never changes (1 to 7)
 * @param reviseChance        each morning, the chance a day beyond those is planned afresh (0 to 1)
 * @param certaintyLossPerDay how much each day beyond the exact ones leans toward the season's usual odds (0 to 1)
 * @param snowstormSnowRate   how much faster snow settles in a snowstorm
 * @param snowstormSky        how dark a snowstorm's sky is, as Minecraft's thunder level (0 to 1)
 */
public record WeatherSettings(boolean enabled, Map<SeasonView.Season, Season> seasons, double snowstormChance,
                              Swing heatWave, Swing coldSnap, int exactDays, double reviseChance,
                              double certaintyLossPerDay, double snowstormSnowRate, double snowstormSky) {
    /**
     * One season's weather.
     *
     * @param dryMinDays   the shortest dry spell between fronts, in days
     * @param dryMaxDays   the longest
     * @param frontMinHours the shortest front, in hours
     * @param frontMaxHours the longest
     * @param thunder      the share of fronts that bring thunder
     */
    public record Season(double dryMinDays, double dryMaxDays, double frontMinHours, double frontMaxHours,
                         double thunder) {
        /** The chance a day sees rain or snow, about: a front's length and a day, over a front and a dry spell. */
        public double rainChance() {
            double dry = (dryMinDays + dryMaxDays) / 2.0 * WeatherPlanner.DAY;
            double front = (frontMinHours + frontMaxHours) / 2.0 * WeatherPlanner.HOUR;
            return Math.min(1.0, (front + WeatherPlanner.DAY) / (dry + front));
        }
    }

    /**
     * A kind of heat wave or cold snap.
     *
     * @param chances  per season, the chance one starts on a day
     * @param minDays  the shortest
     * @param maxDays  the longest
     * @param minF     the least it moves the air at its peak, in F
     * @param maxF     the most
     */
    public record Swing(Map<SeasonView.Season, Double> chances, double minDays, double maxDays, double minF,
                        double maxF) {
        public double chance(SeasonView.Season season) {
            Double chance = chances.get(season);
            return chance == null ? 0.0 : chance;
        }
    }

    public static WeatherSettings defaults() {
        Map<SeasonView.Season, Season> seasons = new EnumMap<>(SeasonView.Season.class);
        seasons.put(SeasonView.Season.SPRING, new Season(1.5, 3.0, 6, 18, 0.25));
        seasons.put(SeasonView.Season.SUMMER, new Season(2.0, 5.0, 2, 8, 0.55));
        seasons.put(SeasonView.Season.AUTUMN, new Season(1.5, 3.0, 8, 24, 0.15));
        seasons.put(SeasonView.Season.WINTER, new Season(2.0, 4.0, 8, 30, 0.05));
        return new WeatherSettings(true, Map.copyOf(seasons), 0.35,
                new Swing(Map.of(SeasonView.Season.SPRING, 0.02, SeasonView.Season.SUMMER, 0.06,
                        SeasonView.Season.AUTUMN, 0.02, SeasonView.Season.WINTER, 0.0), 3, 6, 8, 15),
                new Swing(Map.of(SeasonView.Season.SPRING, 0.02, SeasonView.Season.SUMMER, 0.0,
                        SeasonView.Season.AUTUMN, 0.02, SeasonView.Season.WINTER, 0.06), 3, 6, 8, 15),
                2, 0.3, 0.15, 4.0, 0.7);
    }

    public Season season(SeasonView.Season season) {
        Season own = seasons.get(season);
        return own == null ? defaults().seasons.get(season) : own;
    }

    public static WeatherSettings load(ConfigurationSection section, Consumer<String> warn) {
        WeatherSettings d = defaults();
        if (section == null) return d;
        Map<SeasonView.Season, Season> seasons = new EnumMap<>(SeasonView.Season.class);
        for (SeasonView.Season season : SeasonView.Season.values()) {
            Season own = d.seasons.get(season);
            String path = "seasons." + season.name().toLowerCase(java.util.Locale.ROOT);
            double dryMin = number(section, path + ".dry-days.min", own.dryMinDays(), 0.1, 30, warn);
            double dryMax = number(section, path + ".dry-days.max", own.dryMaxDays(), dryMin, 60, warn);
            double frontMin = number(section, path + ".front-hours.min", own.frontMinHours(), 0.5, 72, warn);
            double frontMax = number(section, path + ".front-hours.max", own.frontMaxHours(), frontMin, 144, warn);
            seasons.put(season, new Season(dryMin, dryMax, frontMin, frontMax,
                    number(section, path + ".thunder", own.thunder(), 0, 1, warn)));
        }
        return new WeatherSettings(section.getBoolean("enabled", d.enabled), Map.copyOf(seasons),
                number(section, "snowstorm.share-of-winter-fronts", d.snowstormChance, 0, 1, warn),
                swing(section, "heat-wave", d.heatWave, warn), swing(section, "cold-snap", d.coldSnap, warn),
                (int) number(section, "forecast.exact-days", d.exactDays, 1, 7, warn),
                number(section, "forecast.revise-chance", d.reviseChance, 0, 1, warn),
                number(section, "forecast.certainty-loss-per-day", d.certaintyLossPerDay, 0, 1, warn),
                number(section, "snowstorm.snow-rate", d.snowstormSnowRate, 1, 20, warn),
                number(section, "snowstorm.sky", d.snowstormSky, 0, 1, warn));
    }

    private static Swing swing(ConfigurationSection section, String path, Swing fallback, Consumer<String> warn) {
        Map<SeasonView.Season, Double> chances = new EnumMap<>(SeasonView.Season.class);
        for (SeasonView.Season season : SeasonView.Season.values())
            chances.put(season, number(section, path + ".chance-per-day." + season.name().toLowerCase(java.util.Locale.ROOT),
                    fallback.chance(season), 0, 1, warn));
        double minDays = number(section, path + ".days.min", fallback.minDays(), 1, 30, warn);
        double maxDays = number(section, path + ".days.max", fallback.maxDays(), minDays, 60, warn);
        double minF = number(section, path + ".strength-f.min", fallback.minF(), 1, 40, warn);
        double maxF = number(section, path + ".strength-f.max", fallback.maxF(), minF, 60, warn);
        return new Swing(Map.copyOf(chances), minDays, maxDays, minF, maxF);
    }

    private static double number(ConfigurationSection section, String path, double fallback, double low, double high,
                                 Consumer<String> warn) {
        if (!section.contains(path)) return fallback;
        double value = section.getDouble(path, Double.NaN);
        if (Double.isFinite(value) && value >= low && value <= high) return value;
        warn.accept("weather." + path + " must be " + low + " to " + high + "; using " + fallback + ".");
        return fallback;
    }
}
