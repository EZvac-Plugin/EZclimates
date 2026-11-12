package com.github.blade.ezclimates.food;

import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * food.yml (EZclimates 0.3.0): how long each food keeps and what spoiled food
 * does. A bad food entry warns and is left out; bad effects keep their
 * defaults. Pure.
 *
 * @param pace every food's days are multiplied by this
 */
public record FoodSettings(boolean onByDefault, double pace, double staleFeeds, double spoiledFeeds,
                           int spoiledHungerSeconds, double spoiledNauseaChance, Map<String, Food> foods) {

    /**
     * One food: its days at 70 F, how many times longer a fridge and a
     * freezer keep it, and what it turns into (null: it just goes). A food
     * that never spoils has {@code never} set.
     */
    public record Food(double days, double fridge, double freezer, String into, boolean never) {
        public static final Food NEVER = new Food(0, 1, 1, null, true);

        /** Its whole life, in room ticks, at this pace. */
        public double lifeRoomTicks(double pace) {
            return days * pace * Freshness.DAY;
        }

        public double rate(double temperatureF) {
            return Freshness.rate(temperatureF, fridge, freezer);
        }
    }

    public FoodSettings {
        foods = Map.copyOf(foods);
    }

    /** The food an item is, by its name (beef, mushroom_stew), or null when it isn't food that keeps. */
    public Food food(String item) {
        Food food = foods.get(item);
        return food == null || food.never() ? null : food;
    }

    public static FoodSettings load(ConfigurationSection config, Consumer<String> warn) {
        double pace = config.getDouble("pace", 1.0);
        if (!(pace > 0.0) || pace > 100.0) {
            warn.accept("food.yml: pace must be above 0 and at most 100; using 1.");
            pace = 1.0;
        }
        double stale = share(config, "stale-feeds", 0.5, warn), spoiled = share(config, "spoiled-feeds", 0.25, warn);
        int hunger = config.getInt("spoiled-hunger-seconds", 30);
        if (hunger < 0 || hunger > 600) {
            warn.accept("food.yml: spoiled-hunger-seconds must be 0 to 600; using 30.");
            hunger = 30;
        }
        double nausea = share(config, "spoiled-nausea-chance", 0.3, warn);
        Map<String, Food> foods = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection("foods");
        if (section != null)
            for (String name : section.getKeys(false)) {
                String key = name.toLowerCase(Locale.ROOT);
                if ("never".equalsIgnoreCase(section.getString(name, ""))) {
                    foods.put(key, Food.NEVER);
                    continue;
                }
                ConfigurationSection entry = section.getConfigurationSection(name);
                double days = entry == null ? -1 : entry.getDouble("days", -1);
                double fridge = entry == null ? -1 : entry.getDouble("fridge", 1);
                double freezer = entry == null ? -1 : entry.getDouble("freezer", 1);
                if (!(days > 0) || !(fridge >= 1) || !(freezer >= 1)) {
                    warn.accept("food.yml: foods." + name + " needs days above 0, and fridge and freezer of 1 or more"
                            + " (or just: never); it is left out, so it never spoils.");
                    continue;
                }
                String into = entry.getString("into", "none");
                foods.put(key, new Food(days, fridge, freezer,
                        into == null || into.equalsIgnoreCase("none") ? null : into.toLowerCase(Locale.ROOT), false));
            }
        return new FoodSettings(config.getBoolean("on-by-default", false), pace, stale, spoiled, hunger, nausea, foods);
    }

    private static double share(ConfigurationSection config, String path, double fallback, Consumer<String> warn) {
        double value = config.getDouble(path, fallback);
        if (Double.isFinite(value) && value >= 0.0 && value <= 1.0) return value;
        warn.accept("food.yml: " + path + " must be 0 to 1; using " + fallback + ".");
        return fallback;
    }
}
