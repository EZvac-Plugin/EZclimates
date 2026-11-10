package com.github.blade.ezclimates;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What each player should see, shared between the main thread, which works
 * it out from EZvac, and the network threads, which only read it while they
 * rewrite chunk packets. Nothing here calls EZvac or Bukkit.
 *
 * <p>Each chunk has 16 snow lines, one for each 4×4 column of biome cells
 * (index {@code z * 4 + x}), as the client stores biomes, so rain and snow
 * can meet along EZvac's own wavy edge rather than at chunk borders.
 */
public final class Looks {
    /** Biome cells across a chunk, and blocks across a cell. */
    public static final int CELLS = 4, CELL_BLOCKS = 4;

    /** One world's look: its stage, and each chunk's 16 snow lines (NaN where rain doesn't reach). */
    public static final class WorldLook {
        public final int minY;
        public final int height;
        public volatile Stage stage = Stage.SUMMER;
        public final ConcurrentHashMap<Long, float[]> lines = new ConcurrentHashMap<>();

        public WorldLook(int minY, int height) {
            this.minY = minY;
            this.height = height;
        }
    }

    /**
     * What a chunk was or should be shown as: a stage, and its 16 snow lines,
     * or null lines for each biome's own weather.
     */
    public record Look(Stage stage, float[] lines) {
        /** An untouched chunk: summer, with each biome's own weather. */
        public static final Look ORIGINAL = new Look(Stage.SUMMER, null);
    }

    /** Overworld-like worlds that get seasons, by id. */
    public final ConcurrentHashMap<UUID, WorldLook> worlds = new ConcurrentHashMap<>();
    /** The world each player is in, kept by the main thread. */
    public final ConcurrentHashMap<UUID, UUID> playerWorld = new ConcurrentHashMap<>();
    /** What each player was last sent for each chunk. */
    public final ConcurrentHashMap<UUID, ConcurrentHashMap<Long, Look>> sent = new ConcurrentHashMap<>();

    public static long chunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public static int chunkX(long key) { return (int) (key >> 32); }

    public static int chunkZ(long key) { return (int) key; }

    /** The middle of a biome cell's column, in blocks from the chunk's corner. */
    public static int cellMiddle(int cell) { return cell * CELL_BLOCKS + CELL_BLOCKS / 2; }

    /** What falls in a biome cell (x and z 0-3 in its chunk) whose middle is at {@code y}; null for the biome's own. */
    public static Kind kind(float[] lines, int x, int z, double y) {
        return lines == null ? null : Kind.at(lines[z * CELLS + x], y);
    }

    /** True when two looks show the same in every cell of a world this tall. */
    public static boolean same(Look a, Look b, int minY, int height) {
        if (a == b) return true;
        if (a == null || b == null || a.stage() != b.stage()) return false;
        if (a.lines() == null || b.lines() == null) return a.lines() == b.lines();
        if (a.lines() == b.lines()) return true;
        for (int cell = 0; cell < CELLS * CELLS; cell++)
            if (band(a.lines()[cell], minY, height) != band(b.lines()[cell], minY, height)) return false;
        return true;
    }

    /**
     * How many of a column's biome cells, from the bottom of a world this
     * tall, are at or below a snow line (they rain; the rest snow), or -1
     * where rain doesn't reach. Two lines with the same band show the same,
     * which is all a comparison needs: the lines themselves move a little
     * every time they are worked out.
     */
    static int band(float line, int minY, int height) {
        if (Float.isNaN(line)) return -1;
        double below = Math.floor((line - (minY + CELL_BLOCKS / 2.0)) / CELL_BLOCKS) + 1.0;
        return (int) Math.max(0.0, Math.min(height / CELL_BLOCKS, below));
    }
}
