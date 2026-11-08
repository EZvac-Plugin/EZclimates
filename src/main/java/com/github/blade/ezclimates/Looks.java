package com.github.blade.ezclimates;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What each player should see, shared between the main thread, which works
 * it out from EZvac, and the network threads, which only read it while they
 * rewrite chunk packets. Nothing here calls EZvac or Bukkit.
 */
public final class Looks {
    /** One world's look: its stage, and each chunk's snow line (NaN where rain doesn't reach). */
    public static final class WorldLook {
        public final int minY;
        public final int height;
        public volatile Stage stage = Stage.SUMMER;
        public final ConcurrentHashMap<Long, Float> lines = new ConcurrentHashMap<>();

        public WorldLook(int minY, int height) {
            this.minY = minY;
            this.height = height;
        }
    }

    /** Overworld-like worlds that get seasons, by id. */
    public final ConcurrentHashMap<UUID, WorldLook> worlds = new ConcurrentHashMap<>();
    /** The world each player is in, kept by the main thread. */
    public final ConcurrentHashMap<UUID, UUID> playerWorld = new ConcurrentHashMap<>();
    /** What each player was last sent for each chunk: {@link #look}. */
    public final ConcurrentHashMap<UUID, ConcurrentHashMap<Long, Long>> sent = new ConcurrentHashMap<>();

    public static long chunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public static int chunkX(long key) { return (int) (key >> 32); }

    public static int chunkZ(long key) { return (int) key; }

    /** A stage and a snow line in one number; a null line is the biomes' own weather. */
    public static long look(Stage stage, Float line) {
        long stageBits = (long) stage.ordinal() << 33;
        return line == null ? stageBits | (1L << 32) : stageBits | (Float.floatToRawIntBits(line) & 0xFFFFFFFFL);
    }

    /** What an untouched chunk shows: summer, with each biome's own weather. */
    public static final long ORIGINAL = look(Stage.SUMMER, null);

    public static Stage stage(long look) { return Stage.values()[(int) (look >>> 33)]; }

    /** The snow line in a look, or null for the biomes' own weather. */
    public static Float line(long look) {
        return (look & (1L << 32)) != 0 ? null : Float.intBitsToFloat((int) look);
    }

    /** What falls in a section whose middle is at {@code y}; null for the biomes' own. */
    public static Kind kind(Float line, double y) {
        return line == null ? null : Kind.at(line, y);
    }

    /** True when two looks show the same in every section of a world this tall. */
    public static boolean same(long a, long b, int minY, int height) {
        if (a == b) return true;
        if (stage(a) != stage(b)) return false;
        Float lineA = line(a), lineB = line(b);
        if (lineA == null || lineB == null) return false;
        for (int y = minY + 8; y < minY + height; y += 16)
            if (Kind.at(lineA, y) != Kind.at(lineB, y)) return false;
        return true;
    }
}
