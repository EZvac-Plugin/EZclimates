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

    public Placed(int[] saved) {
        entries = saved == null ? new int[0] : saved.clone();
        size = entries.length;
    }

    public Placed() { this(null); }

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
        for (int i = 0; i < size; i++) if (entries[i] == entry) return true;
        return false;
    }

    /** Records a block; false when it already was. */
    public boolean add(int entry) {
        if (contains(entry)) return false;
        if (size == entries.length) entries = Arrays.copyOf(entries, Math.max(8, size * 2));
        entries[size++] = entry;
        return true;
    }

    public boolean remove(int entry) {
        for (int i = 0; i < size; i++)
            if (entries[i] == entry) {
                entries[i] = entries[--size];
                return true;
            }
        return false;
    }

    /** What to save. */
    public int[] toArray() { return Arrays.copyOf(entries, size); }
}
