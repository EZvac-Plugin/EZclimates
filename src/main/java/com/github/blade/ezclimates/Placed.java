package com.github.blade.ezclimates;

import java.util.Arrays;

/**
 * The record of every block of snow and ice EZclimates placed in a chunk,
 * kept in the chunk's own saved data so it lives with the world: one number
 * per block (its x and z in the chunk, its height, and what it is). Only
 * recorded blocks are ever melted, so a player's own snow, the snow of snowy
 * biomes and the world's own ice are never touched.
 */
public final class Placed {
    public enum What { SNOW, ICE }

    private int[] entries;
    private int size;
    /** Where each entry is in {@code entries}, so looking one up or removing it is instant. */
    private final it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap at = new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap();
    /** What each block of snow covered (grass, flowers), to bring back when it melts. */
    private final java.util.Map<Integer, String> covered = new java.util.HashMap<>();

    public Placed(int[] saved) {
        this(saved, null);
    }

    /** As saved: the blocks, and what the snow covered, one {@code entry=block data} a line. */
    public Placed(int[] saved, String coveredSaved) {
        entries = saved == null ? new int[0] : saved.clone();
        size = 0;
        at.defaultReturnValue(-1);
        // Read back without repeats, whatever was saved.
        for (int entry : entries) if (!at.containsKey(entry)) { at.put(entry, size); entries[size++] = entry; }
        if (coveredSaved != null)
            for (String line : coveredSaved.split("\\n")) {
                int equals = line.indexOf('=');
                if (equals <= 0) continue;
                try {
                    covered.put(Integer.parseInt(line.substring(0, equals)), line.substring(equals + 1));
                } catch (NumberFormatException ignored) {
                    // A line that isn't ours: left out.
                }
            }
    }

    public Placed() { this(null, null); }

    /** Records what a block of snow covered. */
    public void cover(int entry, String blockData) { covered.put(entry, blockData); }

    /** What a block of snow covered, or null; it is forgotten. */
    public String uncover(int entry) { return covered.remove(entry); }

    /** What the snow covers, to save; null when nothing. */
    public String coveredText() {
        if (covered.isEmpty()) return null;
        StringBuilder text = new StringBuilder();
        for (java.util.Map.Entry<Integer, String> entry : covered.entrySet()) {
            if (text.length() > 0) text.append('\n');
            text.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return text.toString();
    }

    /** One block in a number: x and z 0-15, y from -2048 to 2047. */
    public static int pack(int x, int y, int z, What what) {
        return ((y & 0xFFF) << 10) | ((z & 15) << 6) | ((x & 15) << 2) | what.ordinal();
    }

    public static int x(int entry) { return (entry >> 2) & 15; }

    public static int z(int entry) { return (entry >> 6) & 15; }

    public static int y(int entry) { return (entry << 10) >> 20; }

    public static What what(int entry) { return What.values()[entry & 3]; }

    public int size() { return size; }

    public boolean isEmpty() { return size == 0; }

    public int get(int index) { return entries[index]; }

    public boolean contains(int entry) {
        return at.containsKey(entry);
    }

    /** Records a block; false when it already was. */
    public boolean add(int entry) {
        if (contains(entry)) return false;
        if (size == entries.length) entries = Arrays.copyOf(entries, Math.max(8, size * 2));
        at.put(entry, size);
        entries[size++] = entry;
        return true;
    }

    public boolean remove(int entry) {
        covered.remove(entry);
        int index = at.remove(entry);
        if (index < 0) return false;
        int last = entries[--size];
        if (index != size) {
            entries[index] = last;
            at.put(last, index);
        }
        return true;
    }

    /** What to save. */
    public int[] toArray() { return Arrays.copyOf(entries, size); }
}
