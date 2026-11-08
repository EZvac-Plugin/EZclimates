package com.github.blade.ezclimates;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The seasonal copies of one vanilla biome: for each stage and each kind of
 * weather, the biome's own file with only its temperature, precipitation and
 * colours changed. Its spawns, sky, fog, music and water stay as they are;
 * its features and carvers are left empty, since no copy is ever generated
 * into a world. The copy that is just the original (summer, with the
 * biome's own weather) is not made: the original is shown instead.
 */
public final class BiomeCopies {
    public static final String NAMESPACE = "ezclimates";
    /** A rain copy's temperature, at least: warm enough to rain at any height a player reaches. */
    static final double RAIN_TEMPERATURE = 0.8;

    private BiomeCopies() {}

    /** A copy's path inside the namespace: {@code plains/autumn_late_snow}. */
    public static String path(String biome, Stage stage, Kind kind) {
        return biome + "/" + stage.key() + "_" + kind.key();
    }

    /** Every copy of a biome, by path, in a fixed order. */
    public static Map<String, JsonObject> of(String biome, JsonObject original, Palette palette) {
        Kind own = Kind.vanilla(original);
        Family family = Family.of(biome);
        Map<String, JsonObject> copies = new LinkedHashMap<>();
        for (Stage stage : Stage.values())
            for (Kind kind : Kind.values()) {
                if (stage == Stage.SUMMER && kind == own) continue;
                copies.put(path(biome, stage, kind), copy(original, stage, kind, palette, family));
            }
        return copies;
    }

    static JsonObject copy(JsonObject original, Stage stage, Kind kind, Palette palette, Family family) {
        JsonObject copy = original.deepCopy();
        double temperature = number(original, "temperature", 0.8);
        double downfall = number(original, "downfall", 0.5);
        // Never generated: no features or carvers to resolve, and nothing to place.
        JsonArray steps = new JsonArray();
        JsonElement features = original.get("features");
        int count = features != null && features.isJsonArray() ? features.getAsJsonArray().size() : 0;
        for (int i = 0; i < count; i++) steps.add(new JsonArray());
        copy.add("features", steps);
        copy.add("carvers", new JsonArray());
        copy.remove("temperature_modifier");
        switch (kind) {
            case RAIN -> {
                copy.addProperty("has_precipitation", true);
                copy.addProperty("temperature", Math.max(temperature, RAIN_TEMPERATURE));
            }
            case SNOW -> {
                copy.addProperty("has_precipitation", true);
                copy.addProperty("temperature", 0.0);
            }
            case DRY -> copy.addProperty("has_precipitation", false);
        }
        // Colours set outright: the client would otherwise take them from the copy's own temperature.
        JsonObject effects = copy.has("effects") && copy.get("effects").isJsonObject()
                ? copy.getAsJsonObject("effects") : new JsonObject();
        int grass = colour(effects, "grass_color", Colours.grass(temperature, downfall));
        int foliage = colour(effects, "foliage_color", Colours.foliage(temperature, downfall));
        int dry = colour(effects, "dry_foliage_color", Colours.dryFoliage(temperature, downfall));
        effects.addProperty("grass_color", Colours.format(palette.grass(grass, stage, family)));
        effects.addProperty("foliage_color", Colours.format(palette.foliage(foliage, stage, family)));
        effects.addProperty("dry_foliage_color", Colours.format(palette.dryFoliage(dry, stage, family)));
        copy.add("effects", effects);
        return copy;
    }

    private static int colour(JsonObject effects, String key, int fallback) {
        if (!effects.has(key)) return fallback;
        JsonElement value = effects.get(key);
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) return value.getAsInt() & 0xFFFFFF;
        try {
            return Colours.parse(value.getAsString());
        } catch (RuntimeException unreadable) {
            return fallback;
        }
    }

    private static double number(JsonObject object, String key, double fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsDouble() : fallback;
    }
}
