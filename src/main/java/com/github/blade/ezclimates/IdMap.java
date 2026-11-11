package com.github.blade.ezclimates;

import java.util.HashMap;
import java.util.Map;

/**
 * One player's biome numbers: which number each biome has in the registry
 * their client received, and for each vanilla biome, the number of its copy
 * for every stage and kind of weather. Numbers are the client's, so a copy is
 * found by its name, never assumed.
 */
public final class IdMap {
    /** Per original number: stage x (rain, snow, dry, own) to the number shown. */
    private final Map<Integer, int[]> copies;
    private final Map<String, Integer> byName;
    /** Worked out once: asked for every section of every chunk sent. */
    private final int bits;

    private IdMap(Map<Integer, int[]> copies, Map<String, Integer> byName) {
        this.copies = copies;
        this.byName = byName;
        int highest = 0;
        for (int id : byName.values()) highest = Math.max(highest, id);
        this.bits = Math.max(1, 32 - Integer.numberOfLeadingZeros(highest));
    }

    public boolean isEmpty() { return copies.isEmpty(); }

    /** Bits a biome number takes when a section lists them directly: enough for the whole registry. */
    public int bits() { return bits; }

    /** A biome's name by its number, or the number itself if the client has none by it. */
    public String name(int id) {
        for (Map.Entry<String, Integer> entry : byName.entrySet()) if (entry.getValue() == id) return entry.getKey();
        return String.valueOf(id);
    }

    /** The number of a biome by its name, as {@code minecraft:plains}; -1 if the client has none. */
    public int id(String name) {
        Integer id = byName.get(name);
        return id == null ? -1 : id;
    }

    /** What to show for a biome numbered {@code original} at a stage, with this weather (null: its own). */
    public int map(int original, Stage stage, Kind kind) {
        int[] table = copies.get(original);
        if (table == null) return original;
        return table[stage.ordinal() * 4 + (kind == null ? 3 : kind.ordinal())];
    }

    /** Builds the map from a registry's names and numbers. */
    public static IdMap of(Map<String, Integer> registry) {
        Map<String, Integer> byName = Map.copyOf(registry);
        Map<Integer, int[]> copies = new HashMap<>();
        Map<String, Map<String, Integer>> found = new HashMap<>();
        String prefix = BiomeCopies.NAMESPACE + ":";
        for (Map.Entry<String, Integer> entry : registry.entrySet()) {
            String name = entry.getKey();
            if (!name.startsWith(prefix)) continue;
            String path = name.substring(prefix.length());
            int slash = path.indexOf('/');
            if (slash <= 0) continue;
            found.computeIfAbsent("minecraft:" + path.substring(0, slash), ignored -> new HashMap<>())
                    .put(path.substring(slash + 1), entry.getValue());
        }
        for (Map.Entry<String, Map<String, Integer>> biome : found.entrySet()) {
            Integer original = byName.get(biome.getKey());
            if (original == null) continue;
            Map<String, Integer> variants = biome.getValue();
            int[] table = new int[Stage.values().length * 4];
            java.util.Arrays.fill(table, original);
            // The biome's own weather is the one summer has no copy of.
            Kind own = null;
            for (Kind kind : Kind.values())
                if (!variants.containsKey(Stage.SUMMER.key() + "_" + kind.key())) own = kind;
            for (Stage stage : Stage.values()) {
                for (Kind kind : Kind.values()) {
                    Integer copy = variants.get(stage.key() + "_" + kind.key());
                    if (copy != null) table[stage.ordinal() * 4 + kind.ordinal()] = copy;
                }
                if (own != null) table[stage.ordinal() * 4 + 3] = table[stage.ordinal() * 4 + own.ordinal()];
            }
            copies.put(original, table);
        }
        return new IdMap(Map.copyOf(copies), byName);
    }
}
