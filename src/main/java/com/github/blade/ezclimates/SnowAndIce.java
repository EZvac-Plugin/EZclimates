package com.github.blade.ezclimates;

import com.github.blade.hvac.api.ClimateReading;
import com.github.blade.hvac.api.EzvacApi;
import com.github.blade.hvac.api.Precipitation;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.type.Snow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Snow that settles where EZvac says it snows, and ice on water EZvac's
 * water temperature has frozen, ponds to oceans and unheated pools, and both
 * melting again when it warms. Every block placed is recorded in its chunk,
 * and only recorded blocks are ever melted, so nothing EZclimates puts down
 * can stay forever, and nothing else is touched. Vanilla's own weather snow
 * and ice are stopped in seasonal worlds, so all of it follows EZvac.
 * Main thread only.
 */
public final class SnowAndIce implements Listener {
    /** Melting starts a little above freezing, so a sheet doesn't flicker at 32 F. */
    static final double THAW_ABOVE_F = 34.0;
    static final double FRESH_FREEZES_F = 32.0;
    /** Salt water freezes colder. */
    static final double SEA_FREEZES_F = 28.4;

    private final EzvacApi ezvac;
    private final Settings settings;
    private final NamespacedKey key;
    /** Records of loaded chunks that have any, per world. */
    private final Map<UUID, Long2ObjectOpenHashMap<Placed>> records = new HashMap<>();
    /** Chunks to look over: newly loaded ones first, then every recorded chunk in turn. */
    private final ArrayDeque<long[]> queue = new ArrayDeque<>();
    private boolean clearing;
    private int placedTotal, meltedTotal;

    private final NamespacedKey coveredKey;
    /** How much faster snow settles in a world right now: a planned snowstorm's rate, or 1. */
    private java.util.function.ToDoubleFunction<World> boost = world -> 1.0;

    public void setBoost(java.util.function.ToDoubleFunction<World> boost) { this.boost = boost; }

    public SnowAndIce(EzvacApi ezvac, Settings settings, NamespacedKey key, NamespacedKey coveredKey, boolean clearing) {
        this.ezvac = ezvac;
        this.settings = settings;
        this.key = key;
        this.coveredKey = coveredKey;
        this.clearing = clearing;
    }

    /**
     * Ground cover snow buries and the melt brings back: grasses, ferns,
     * bushes, flowers, petals and leaf litter, never crops or saplings.
     */
    static boolean covers(Material type) {
        if (org.bukkit.Tag.SMALL_FLOWERS.isTagged(type)) return true;
        return switch (type.name()) {
            case "SHORT_GRASS", "TALL_GRASS", "FERN", "LARGE_FERN", "DEAD_BUSH", "BUSH", "SHORT_DRY_GRASS",
                 "TALL_DRY_GRASS", "FIREFLY_BUSH", "LEAF_LITTER", "WILDFLOWERS", "PINK_PETALS", "SUNFLOWER",
                 "LILAC", "ROSE_BUSH", "PEONY" -> true;
            default -> false;
        };
    }

    public void start(Iterable<World> worlds) {
        for (World world : worlds)
            if (LookUpdater.seasonal(world))
                for (Chunk chunk : world.getLoadedChunks()) read(chunk);
    }

    private void read(Chunk chunk) {
        int[] saved = chunk.getPersistentDataContainer().get(key, PersistentDataType.INTEGER_ARRAY);
        if (saved == null || saved.length == 0) return;
        records.computeIfAbsent(chunk.getWorld().getUID(), ignored -> new Long2ObjectOpenHashMap<>())
                .put(Looks.chunkKey(chunk.getX(), chunk.getZ()), new Placed(saved,
                        chunk.getPersistentDataContainer().get(coveredKey, PersistentDataType.STRING)));
        // Caught up at once: a chunk unloaded through a thaw melts as it loads.
        queue.addFirst(new long[]{chunk.getWorld().getUID().getMostSignificantBits(),
                chunk.getWorld().getUID().getLeastSignificantBits(), Looks.chunkKey(chunk.getX(), chunk.getZ())});
    }

    private void save(Chunk chunk, Placed placed) {
        if (placed.isEmpty()) chunk.getPersistentDataContainer().remove(key);
        else chunk.getPersistentDataContainer().set(key, PersistentDataType.INTEGER_ARRAY, placed.toArray());
        String covered = placed.coveredText();
        if (covered == null) chunk.getPersistentDataContainer().remove(coveredKey);
        else chunk.getPersistentDataContainer().set(coveredKey, PersistentDataType.STRING, covered);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (LookUpdater.seasonal(event.getWorld())) read(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        Long2ObjectOpenHashMap<Placed> chunks = records.get(event.getWorld().getUID());
        if (chunks != null) chunks.remove(Looks.chunkKey(event.getChunk().getX(), event.getChunk().getZ()));
    }

    /**
     * Vanilla's own weather snow and ice in a seasonal world: stopped, so all
     * of it follows EZvac and can melt. A snow golem's trail and frost
     * walking are left alone.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onForm(BlockFormEvent event) {
        if (event instanceof EntityBlockFormEvent || !LookUpdater.seasonal(event.getBlock().getWorld())) return;
        Material to = event.getNewState().getType();
        if (to == Material.SNOW && settings.snow()) event.setCancelled(true);
        else if (to == Material.ICE && event.getBlock().getType() == Material.WATER && settings.ice()) event.setCancelled(true);
    }

    /** Every tick: a few columns round players for new snow and ice, and a few records to melt. */
    public void tick(Iterable<World> worlds) {
        if (!clearing && (settings.snow() || settings.ice())) place(worlds);
        check();
    }

    private void place(Iterable<World> worlds) {
        List<Player> players = new ArrayList<>();
        for (World world : worlds) if (LookUpdater.seasonal(world)) players.addAll(world.getPlayers());
        if (players.isEmpty()) return;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int radius = settings.snowRadius();
        // Vanilla's pace: each chunk gets a try one tick in 16, times snow.rate, within the budget.
        double chunks = (2.0 * radius + 1) * (2.0 * radius + 1) / 256.0;
        double rate = 0.0;
        for (Player player : players) rate += boost.applyAsDouble(player.getWorld());
        owed += rate * chunks / 16.0 * settings.snowRate();
        int tries = (int) Math.min(settings.blocksPerTick(), Math.floor(owed));
        owed = Math.min(owed - tries, settings.blocksPerTick());
        for (int i = 0; i < tries; i++) {
            Player player = players.get(random.nextInt(players.size()));
            World world = player.getWorld();
            int x = player.getLocation().getBlockX() + random.nextInt(-radius, radius + 1);
            int z = player.getLocation().getBlockZ() + random.nextInt(-radius, radius + 1);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
            Block top = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING);
            if (top.getType() == Material.WATER) {
                if (settings.ice()) freeze(top);
            } else if (settings.snow()) {
                settle(top);
            }
        }
    }

    /** Tries not yet made, carried from tick to tick. */
    private double owed;

    private void settle(Block top) {
        World world = top.getWorld();
        if (ezvac.precipitationAt(world, top.getX(), top.getY() + 1, top.getZ()) != Precipitation.SNOW) return;
        Placed placed = placedIn(top.getChunk(), true);
        if (top.getType() == Material.SNOW) {
            // Deeper with longer snowfall, but only on its own snow.
            if (!placed.contains(Placed.pack(top.getX() & 15, top.getY(), top.getZ() & 15, Placed.What.SNOW))) return;
            Snow snow = (Snow) top.getBlockData();
            // A snowstorm piles it a layer deeper.
            int most = settings.maxLayers() + (boost.applyAsDouble(world) > 1.0 ? 1 : 0);
            if (snow.getLayers() >= Math.min(8, most)) return;
            snow.setLayers(snow.getLayers() + 1);
            top.setBlockData(snow, false);
            return;
        }
        Block above = top.getRelative(BlockFace.UP);
        BlockData snow = Material.SNOW.createBlockData();
        // Grass and flowers are buried, and recorded so the melt brings them back.
        String covered = null, coveredTop = null;
        if (covers(above.getType())) {
            if (above.getBlockData() instanceof org.bukkit.block.data.Bisected half
                    && half.getHalf() == org.bukkit.block.data.Bisected.Half.TOP) return;
            covered = above.getBlockData().getAsString();
            Block upper = above.getRelative(BlockFace.UP);
            if (above.getBlockData() instanceof org.bukkit.block.data.Bisected && upper.getType() == above.getType())
                coveredTop = upper.getBlockData().getAsString();
        } else if (!above.getType().isAir()) return;
        // Snow lies where vanilla's would: on a solid top, on grass, or on leaves.
        if (!top.getBlockData().isFaceSturdy(BlockFace.UP, org.bukkit.block.BlockSupport.FULL)
                && !org.bukkit.Tag.LEAVES.isTagged(top.getType())) return;
        int entry = Placed.pack(above.getX() & 15, above.getY(), above.getZ() & 15, Placed.What.SNOW);
        if (coveredTop != null) above.getRelative(BlockFace.UP).setType(Material.AIR, false);
        above.setBlockData(snow, false);
        placed.add(entry);
        if (covered != null) placed.cover(entry, coveredTop == null ? covered : covered + "|" + coveredTop);
        save(above.getChunk(), placed);
        placedTotal++;
    }

    private void freeze(Block water) {
        if (!(water.getBlockData() instanceof Levelled level) || level.getLevel() != 0) return;
        if (!water.getRelative(BlockFace.UP).getType().isAir()) return;
        if (waterF(water) > freezesAtF(water)) return;
        // Never round a boat, a swimmer or anything else in it.
        if (!water.getWorld().getNearbyEntities(BoundingBox.of(water).expand(0.0, 0.0, 0.0, 0.0, 1.0, 0.0)).isEmpty()) return;
        Placed placed = placedIn(water.getChunk(), true);
        water.setType(Material.ICE, false);
        placed.add(Placed.pack(water.getX() & 15, water.getY(), water.getZ() & 15, Placed.What.ICE));
        save(water.getChunk(), placed);
        placedTotal++;
    }

    /** EZvac holds liquid water at this or warmer; a pool kept above it is being heated. */
    static final double LIQUID_MINIMUM_F = 33.0;

    /**
     * How cold the water is getting. A pool system's water that is warmer
     * than liquid water's minimum is heated, so it never freezes; otherwise,
     * natural water's temperature before EZvac holds it at 33 F, so a lake
     * (or an unheated pool) can reach freezing.
     */
    private double waterF(Block water) {
        ClimateReading reading = ezvac.readingAt(water.getLocation().add(0.5, 0.5, 0.5));
        if (reading.inWater() && reading.system().isPresent() && reading.influence() > 0.0
                && reading.temperatureF() > LIQUID_MINIMUM_F + 0.5) return reading.temperatureF();
        return ezvac.freezingWaterF(water.getWorld(), water.getX(), water.getY(), water.getZ());
    }

    private static double freezesAtF(Block water) {
        String biome = water.getBiome().getKey().getKey();
        return biome.contains("ocean") || biome.contains("beach") ? SEA_FREEZES_F : FRESH_FREEZES_F;
    }

    private Placed placedIn(Chunk chunk, boolean create) {
        Long2ObjectOpenHashMap<Placed> chunks = records.computeIfAbsent(chunk.getWorld().getUID(),
                ignored -> new Long2ObjectOpenHashMap<>());
        long id = Looks.chunkKey(chunk.getX(), chunk.getZ());
        Placed placed = chunks.get(id);
        if (placed == null && create) chunks.put(id, placed = new Placed());
        return placed;
    }

    /** Looks over recorded blocks, a chunk at a time, melting what is warm (or everything while clearing). */
    private void check() {
        int budget = settings.recordsPerTick();
        if (queue.isEmpty()) refill();
        while (budget > 0 && !queue.isEmpty()) {
            long[] next = queue.pollFirst();
            World world = org.bukkit.Bukkit.getWorld(new UUID(next[0], next[1]));
            Long2ObjectOpenHashMap<Placed> chunks = world == null ? null : records.get(world.getUID());
            Placed placed = chunks == null ? null : chunks.get(next[2]);
            if (placed == null || placed.isEmpty()) continue;
            int cx = Looks.chunkX(next[2]), cz = Looks.chunkZ(next[2]);
            if (!world.isChunkLoaded(cx, cz)) continue;
            Chunk chunk = world.getChunkAt(cx, cz);
            boolean changed = false;
            // The chunk's air once for its snow; its water block by block, for pools.
            double airF = Double.NaN;
            for (int i = placed.size() - 1; i >= 0 && budget > 0; i--, budget--) {
                int entry = placed.get(i);
                Block block = chunk.getBlock(Placed.x(entry), Placed.y(entry), Placed.z(entry));
                if (Placed.what(entry) == Placed.What.SNOW) {
                    if (block.getType() != Material.SNOW) { placed.remove(entry); changed = true; continue; }
                    if (!clearing) {
                        // Nothing melts while it's still snowing on it: snow falls up to winter's line
                        // (40 F), above where melting starts, and the two would fight.
                        if (ezvac.precipitationAt(world, block.getX(), block.getY(), block.getZ()) == Precipitation.SNOW)
                            continue;
                        if (Double.isNaN(airF)) airF = ezvac.outdoorAt(block.getLocation()).airF();
                        if (!melts(airF, ThreadLocalRandom.current().nextDouble())) continue;
                    }
                    Snow snow = (Snow) block.getBlockData();
                    if (snow.getLayers() > 1 && !clearing) {
                        snow.setLayers(snow.getLayers() - 1);
                        block.setBlockData(snow, false);
                    } else {
                        uncover(block, placed.uncover(entry));
                        placed.remove(entry);
                        meltedTotal++;
                    }
                    changed = true;
                } else {
                    if (block.getType() != Material.ICE) { placed.remove(entry); changed = true; continue; }
                    if (!clearing) {
                        Block below = block.getRelative(BlockFace.DOWN);
                        double waterF = below.getType() == Material.WATER ? waterF(below)
                                : ezvac.freezingWaterF(world, block.getX(), block.getY(), block.getZ());
                        if (waterF <= THAW_ABOVE_F) continue;
                    }
                    block.setType(Material.WATER, false);
                    placed.remove(entry);
                    meltedTotal++;
                    changed = true;
                }
            }
            if (changed) save(chunk, placed);
            if (placed.isEmpty()) chunks.remove(next[2]);
        }
    }

    /**
     * Whether a look at a block of snow melts a layer: never at or below
     * 34 F, and above it gradually, the warmer the likelier (every look at
     * 42 F and up), so a thaw eats the snow over a while rather than at once.
     */
    static boolean melts(double airF, double roll) {
        if (!(airF > THAW_ABOVE_F)) return false;
        return roll < Math.min(1.0, (airF - THAW_ABOVE_F) / 8.0);
    }

    /** The last layer melts: what it buried grows back, or the air returns. */
    static void uncover(Block block, String covered) {
        if (covered == null) {
            block.setType(Material.AIR, false);
            return;
        }
        String[] halves = covered.split("\\|");
        try {
            BlockData lower = org.bukkit.Bukkit.createBlockData(halves[0]);
            Block upper = block.getRelative(BlockFace.UP);
            if (halves.length > 1 && !upper.getType().isAir()) {
                block.setType(Material.AIR, false);   // no room for its top half any more
                return;
            }
            block.setBlockData(lower, false);
            if (halves.length > 1) upper.setBlockData(org.bukkit.Bukkit.createBlockData(halves[1]), false);
        } catch (IllegalArgumentException unknown) {
            block.setType(Material.AIR, false);
        }
    }

    private void refill() {
        for (Map.Entry<UUID, Long2ObjectOpenHashMap<Placed>> world : records.entrySet())
            for (long chunk : world.getValue().keySet())
                queue.addLast(new long[]{world.getKey().getMostSignificantBits(),
                        world.getKey().getLeastSignificantBits(), chunk});
    }

    /** Starts or stops clearing: everything recorded melts, and nothing new is placed. */
    public void setClearing(boolean clearing) {
        this.clearing = clearing;
        queue.clear();
    }

    public boolean clearing() { return clearing; }

    /** Recorded blocks in loaded chunks: snow, then ice. */
    public int[] recorded() {
        int snow = 0, ice = 0;
        for (Long2ObjectOpenHashMap<Placed> chunks : records.values())
            for (Placed placed : chunks.values())
                for (int i = 0; i < placed.size(); i++)
                    if (Placed.what(placed.get(i)) == Placed.What.SNOW) snow++; else ice++;
        return new int[]{snow, ice};
    }

    /** What EZclimates sees at a column, for /ezclimates here. */
    public List<String> describe(World world, int x, int z) {
        List<String> lines = new ArrayList<>();
        Block top = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING);
        lines.add("Top block " + top.getType().getKey().getKey() + " at y " + top.getY() + "; falling above it: "
                + ezvac.precipitationAt(world, x, top.getY() + 1, z).name().toLowerCase(java.util.Locale.ROOT)
                + "; air " + String.format(java.util.Locale.ROOT, "%.1f F", ezvac.outdoorAt(top.getLocation().add(0, 1, 0)).airF())
                + "; snow line " + ezvac.snowLineAt(world, x, z) + ".");
        if (top.getType() == Material.WATER) {
            ClimateReading reading = ezvac.readingAt(top.getLocation().add(0.5, 0.5, 0.5));
            lines.add(String.format(java.util.Locale.ROOT, "Water: reads %.1f F%s; natural water getting to %.1f F;"
                            + " freezes at %.1f F; it counts %.1f F.", reading.temperatureF(),
                    reading.system().map(system -> " (system " + system + ")").orElse(""),
                    ezvac.freezingWaterF(world, x, top.getY(), z), freezesAtF(top), waterF(top)));
        }
        Placed placed = placedIn(top.getChunk(), false);
        lines.add("This chunk has " + (placed == null ? 0 : placed.size()) + " recorded blocks of snow and ice.");
        return lines;
    }

    public int placedTotal() { return placedTotal; }

    public int meltedTotal() { return meltedTotal; }
}
