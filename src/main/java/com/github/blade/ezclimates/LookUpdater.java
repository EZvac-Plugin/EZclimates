package com.github.blade.ezclimates;

import com.github.blade.hvac.api.EzvacApi;
import com.github.blade.hvac.api.SeasonView;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.DataPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.GlobalPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.ListPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.PaletteType;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.SingletonPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.storage.BitStorage;
import com.github.retrooper.packetevents.util.Vector2i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkBiomes;
import io.papermc.paper.event.packet.PlayerChunkLoadEvent;
import io.papermc.paper.event.packet.PlayerChunkUnloadEvent;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The main thread's half: works out each world's stage and the snow line of
 * the chunks round each player from EZvac, and resends the biomes of chunks a
 * player already has whose look changed, nearest first, a few per tick.
 */
public final class LookUpdater implements Listener {
    /** How long a snow line is trusted before it is worked out again, in ticks: the air changes through the day. */
    static final long LINE_TICKS = 600L;

    private final EzvacApi ezvac;
    private final Looks looks;
    private final ChunkRewriter rewriter;
    private final Settings settings;
    /** How often the chunks round players, and each player's own, are looked over, in ticks: twice a second. */
    static final long SCAN_TICKS = 10L;

    /** A world's snow lines: when each chunk's were worked out, and the chunks waiting for theirs, nearest first. */
    private static final class LineWork {
        final Long2LongOpenHashMap at = new Long2LongOpenHashMap();
        final ArrayDeque<long[]> wanted = new ArrayDeque<>();
        long scannedAt = -SCAN_TICKS, purgedAt;

        LineWork() { at.defaultReturnValue(Long.MIN_VALUE); }
    }

    /** A player's chunks found out of date at the last look over them, nearest first. */
    private static final class Pending {
        final ArrayDeque<long[]> stale = new ArrayDeque<>();
        long scannedAt;

        Pending(long scannedAt) { this.scannedAt = scannedAt; }
    }

    private final Map<UUID, LineWork> work = new HashMap<>();
    private final Map<UUID, Pending> pending = new HashMap<>();
    /** The chunks each player has, as Paper reports them. */
    private final Map<UUID, LongOpenHashSet> loaded = new HashMap<>();
    private long tick;
    private int resentTotal;

    public LookUpdater(EzvacApi ezvac, Looks looks, ChunkRewriter rewriter, Settings settings) {
        this.ezvac = ezvac;
        this.looks = looks;
        this.rewriter = rewriter;
        this.settings = settings;
    }

    /**
     * Switched in EZvac's Addons menu: off, players are shown the world's own
     * biomes again (their chunks are resent as they are), and no snow lines
     * are worked out.
     */
    private boolean showing = true;

    public void setShowing(boolean showing) { this.showing = showing; }

    /** Worlds with a sky get seasons: the Nether and the End are left as they are. */
    static boolean seasonal(World world) {
        return world.getEnvironment() == World.Environment.NORMAL;
    }

    public void start(Iterable<? extends Player> online) {
        for (Player player : online) placed(player, player.getWorld());
    }

    private void placed(Player player, World world) {
        looks.sent.remove(player.getUniqueId());
        pending.remove(player.getUniqueId());
        if (seasonal(world)) {
            looks.worlds.computeIfAbsent(world.getUID(), ignored -> new Looks.WorldLook(world.getMinHeight(),
                    world.getMaxHeight() - world.getMinHeight()));
            looks.playerWorld.put(player.getUniqueId(), world.getUID());
        } else {
            looks.playerWorld.remove(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        placed(event.getPlayer(), event.getPlayer().getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo().getWorld() != null && event.getTo().getWorld() != event.getFrom().getWorld())
            placed(event.getPlayer(), event.getTo().getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        placed(event.getPlayer(), event.getRespawnLocation().getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        placed(event.getPlayer(), event.getPlayer().getWorld());
        LongOpenHashSet chunks = loaded.get(event.getPlayer().getUniqueId());
        if (chunks != null) chunks.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        looks.playerWorld.remove(id);
        looks.sent.remove(id);
        loaded.remove(id);
        pending.remove(id);
        rewriter.forget(id);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkSent(PlayerChunkLoadEvent event) {
        loaded.computeIfAbsent(event.getPlayer().getUniqueId(), ignored -> new LongOpenHashSet())
                .add(Looks.chunkKey(event.getChunk().getX(), event.getChunk().getZ()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkForgotten(PlayerChunkUnloadEvent event) {
        long key = Looks.chunkKey(event.getChunk().getX(), event.getChunk().getZ());
        LongOpenHashSet chunks = loaded.get(event.getPlayer().getUniqueId());
        if (chunks != null) chunks.remove(key);
        Map<Long, Looks.Look> sent = looks.sent.get(event.getPlayer().getUniqueId());
        if (sent != null) sent.remove(key);
    }

    /** Every tick. */
    public void tick(Iterable<World> worlds) {
        tick++;
        int budget = settings.linesPerTick();
        for (World world : worlds) {
            if (!seasonal(world) || world.getPlayers().isEmpty()) continue;
            Looks.WorldLook look = looks.worlds.computeIfAbsent(world.getUID(),
                    ignored -> new Looks.WorldLook(world.getMinHeight(), world.getMaxHeight() - world.getMinHeight()));
            if (tick % 20 == 0) look.stage = stage(world);
            if (jumped(world)) {
                work.remove(world.getUID());
                // EZvac takes the new weather in on its next cycle, up to a second later: whether this rain
                // reaches dry ground is only known then, so the lines are worked out once more after it.
                recheck.put(world.getUID(), tick + RECHECK_TICKS);
            } else {
                Long due = recheck.get(world.getUID());
                if (due != null && tick >= due) {
                    recheck.remove(world.getUID());
                    work.remove(world.getUID());
                }
            }
            if (showing) budget = lines(world, look, budget);
        }
        for (World world : worlds) {
            Looks.WorldLook look = looks.worlds.get(world.getUID());
            if (look == null) continue;
            for (Player player : world.getPlayers()) resend(player, world, look);
        }
    }

    /** When each world's lines are to be worked out once more after its weather turned. */
    private final Map<UUID, Long> recheck = new HashMap<>();
    /** Two of EZvac's cycles. */
    static final long RECHECK_TICKS = 45L;

    /** Each world's clock, weather and season as last seen: [full time, rain, thunder, season day]. */
    private final Map<UUID, long[]> seen = new HashMap<>();

    /**
     * True when the snow lines can't be trusted any more: the clock jumped
     * (/time set, sleeping), the weather turned, or the season moved. They
     * are worked out again at once, nearest first, rather than as they age.
     */
    private boolean jumped(World world) {
        long time = world.getFullTime();
        long day = ezvac.season(world).map(view -> (long) view.season().ordinal() * 1000 + view.dayOfSeason()).orElse(-1L);
        long[] now = {time, world.hasStorm() ? 1 : 0, world.isThundering() ? 1 : 0, day};
        long[] before = seen.put(world.getUID(), now);
        if (before == null) return false;
        long passed = time - before[0];
        return passed < 0 || passed > 200 || now[1] != before[1] || now[2] != before[2] || now[3] != before[3];
    }

    private Stage stage(World world) {
        if (!settings.colours()) return Stage.SUMMER;
        return ezvac.season(world).map(SeasonView::progress).map(Stage::at).orElse(Stage.SUMMER);
    }

    /**
     * Works out snow lines round the world's players, nearest and oldest
     * first, within the budget. Which chunks need theirs is looked for twice
     * a second; in between, the ones found are worked through.
     */
    private int lines(World world, Looks.WorldLook look, int budget) {
        LineWork work = this.work.computeIfAbsent(world.getUID(), ignored -> new LineWork());
        if (!settings.rainAndSnow()) {
            look.lines.clear();
            work.at.clear();
            work.wanted.clear();
            return budget;
        }
        if (tick - work.scannedAt >= SCAN_TICKS) wanted(world, look, work);
        while (budget > 0 && !work.wanted.isEmpty()) {
            long key = work.wanted.pollFirst()[0];
            // One line per 4x4 column of biome cells, so rain meets snow along EZvac's own edge.
            float[] lines = new float[Looks.CELLS * Looks.CELLS];
            int baseX = Looks.chunkX(key) * 16, baseZ = Looks.chunkZ(key) * 16;
            for (int cz = 0; cz < Looks.CELLS; cz++)
                for (int cx = 0; cx < Looks.CELLS; cx++)
                    lines[cz * Looks.CELLS + cx] = (float) ezvac.snowLineAt(world, baseX + Looks.cellMiddle(cx),
                            baseZ + Looks.cellMiddle(cz));
            look.lines.put(key, lines);
            work.at.put(key, tick);
            budget--;
        }
        return budget;
    }

    /** Looks round the world's players for chunks without snow lines, or with old ones, and forgets far ones. */
    private void wanted(World world, Looks.WorldLook look, LineWork work) {
        work.scannedAt = tick;
        int radius = world.getViewDistance() + 1;
        LongOpenHashSet near = new LongOpenHashSet();
        List<long[]> wanted = new ArrayList<>();
        for (Player player : world.getPlayers()) {
            int px = player.getLocation().getBlockX() >> 4, pz = player.getLocation().getBlockZ() >> 4;
            for (int dx = -radius; dx <= radius; dx++)
                for (int dz = -radius; dz <= radius; dz++) {
                    long key = Looks.chunkKey(px + dx, pz + dz);
                    if (!near.add(key)) continue;
                    long at = work.at.get(key);
                    boolean never = at == Long.MIN_VALUE;
                    if (!never && tick - at < LINE_TICKS) continue;
                    // Missing ones first, then by distance.
                    wanted.add(new long[]{key, (never ? 0 : 1_000_000L) + (long) dx * dx + (long) dz * dz});
                }
        }
        wanted.sort(Comparator.comparingLong(entry -> entry[1]));
        work.wanted.clear();
        work.wanted.addAll(wanted);
        // Forget chunks no one is near.
        if (tick - work.purgedAt >= 200) {
            work.purgedAt = tick;
            for (LongIterator known = work.at.keySet().iterator(); known.hasNext(); )
                if (!near.contains(known.nextLong())) known.remove();
            look.lines.keySet().removeIf(key -> !near.contains(key));
        }
    }

    /**
     * Resends a player's chunks whose look changed, nearest first. Their
     * chunks are looked over twice a second, each player on their own tick;
     * in between, the ones found out of date are sent, a few a tick.
     */
    private void resend(Player player, World world, Looks.WorldLook look) {
        LongOpenHashSet chunks = loaded.get(player.getUniqueId());
        if (chunks == null || chunks.isEmpty()) return;
        Pending pending = this.pending.computeIfAbsent(player.getUniqueId(),
                ignored -> new Pending(tick - ThreadLocalRandom.current().nextLong(SCAN_TICKS)));
        ConcurrentHashMap<Long, Looks.Look> sent = looks.sent.computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>());
        // Switched off, every chunk should show as the world has it: summer, with each biome's own weather.
        Stage stage = showing ? look.stage : Stage.SUMMER;
        if (pending.stale.isEmpty()) {
            if (tick - pending.scannedAt < SCAN_TICKS) return;
            pending.scannedAt = tick;
            int px = player.getLocation().getBlockX() >> 4, pz = player.getLocation().getBlockZ() >> 4;
            List<long[]> stale = new ArrayList<>();
            for (long key : chunks) {
                if (!stale(sent.get(key), stage, showing ? look.lines.get(key) : null, look)) continue;
                long dx = Looks.chunkX(key) - px, dz = Looks.chunkZ(key) - pz;
                stale.add(new long[]{key, dx * dx + dz * dz});
            }
            if (stale.isEmpty()) return;
            stale.sort(Comparator.comparingLong(entry -> entry[1]));
            pending.stale.addAll(stale);
        }
        User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        IdMap map = user == null ? null : rewriter.ids(user);
        if (map == null || map.isEmpty()) {
            pending.stale.clear();
            return;
        }
        Map<Vector2i, WrapperPlayServerChunkBiomes.ChunkBiomeData> batch = new LinkedHashMap<>();
        while (!pending.stale.isEmpty() && batch.size() < settings.chunksPerTick()) {
            long key = pending.stale.pollFirst()[0];
            int cx = Looks.chunkX(key), cz = Looks.chunkZ(key);
            // Since it was found: the player may have left it behind, or been sent it afresh.
            if (!chunks.contains(key) || !world.isChunkLoaded(cx, cz)) continue;
            float[] lines = showing ? look.lines.get(key) : null;
            if (!stale(sent.get(key), stage, lines, look)) continue;
            batch.put(new Vector2i(cx, cz), ChunkRewriter.data(sections(world.getChunkAt(cx, cz), look, map, stage, lines)));
            sent.put(key, new Looks.Look(stage, lines));
        }
        if (batch.isEmpty()) return;
        // Silently: these already show the copies, so the rewriter leaves them alone.
        PacketEvents.getAPI().getPlayerManager().sendPacketSilently(player, new WrapperPlayServerChunkBiomes(batch));
        resentTotal += batch.size();
    }

    /** Whether what a player was sent for a chunk no longer shows what it should now. */
    private static boolean stale(Looks.Look before, Stage stage, float[] lines, Looks.WorldLook look) {
        if (before == null) before = Looks.Look.ORIGINAL;
        if (before.stage() == stage && before.lines() == lines) return false;
        return !Looks.same(before, new Looks.Look(stage, lines), look.minY, look.height);
    }

    /** A loaded chunk's biomes as the player should see them, section by section. */
    static List<DataPalette> sections(Chunk chunk, Looks.WorldLook look, IdMap map, Stage stage, float[] lines) {
        ChunkSnapshot snapshot = chunk.getChunkSnapshot(false, true, false);
        Map<Biome, Integer> numbers = new HashMap<>();
        List<DataPalette> sections = new ArrayList<>(look.height / 16);
        for (int section = 0; section < look.height / 16; section++) {
            int baseY = look.minY + section * 16;
            int[] cells = new int[64];
            int i = 0;
            for (int y = 0; y < 4; y++)
                for (int z = 0; z < 4; z++)
                    for (int x = 0; x < 4; x++) {
                        Biome biome = snapshot.getBiome(x * 4, baseY + y * 4, z * 4);
                        int original = numbers.computeIfAbsent(biome, b -> map.id(b.getKey().toString()));
                        cells[i++] = original < 0 ? 0 : map.map(original, stage, Looks.kind(lines, x, z, baseY + y * 4 + 2));
                    }
            sections.add(palette(cells, map.bits()));
        }
        return sections;
    }

    /**
     * A section's 64 biome cells (in the order y, z, x) as the network writes
     * them: one biome, a list of up to eight, or the registry's own numbers.
     */
    static DataPalette palette(int[] cells, int registryBits) {
        int[] distinct = java.util.Arrays.stream(cells).distinct().toArray();
        if (distinct.length == 1)
            return new DataPalette(new SingletonPalette(distinct[0]), null, PaletteType.BIOME);
        if (distinct.length <= 8) {
            int bits = distinct.length <= 2 ? 1 : distinct.length <= 4 ? 2 : 3;
            ListPalette list = new ListPalette(bits);
            BitStorage storage = new BitStorage(bits, cells.length);
            for (int i = 0; i < cells.length; i++) storage.set(i, list.stateToId(cells[i]));
            return new DataPalette(list, storage, PaletteType.BIOME);
        }
        BitStorage storage = new BitStorage(registryBits, cells.length);
        for (int i = 0; i < cells.length; i++) storage.set(i, cells[i]);
        return new DataPalette(GlobalPalette.INSTANCE, storage, PaletteType.BIOME);
    }

    /**
     * What a player's client was last sent where they stand, and what it
     * should show now: for /ezclimates here, to set beside F3's biome.
     */
    public List<String> shownTo(Player player) {
        List<String> lines = new ArrayList<>();
        World world = player.getWorld();
        Looks.WorldLook look = looks.worlds.get(world.getUID());
        if (look == null) {
            lines.add("No seasons are shown in this world.");
            return lines;
        }
        User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        IdMap map = user == null ? null : rewriter.ids(user);
        if (map == null || map.isEmpty()) {
            lines.add("Your client has no seasonal biomes (it joined before they loaded?): log in again.");
            return lines;
        }
        int x = player.getLocation().getBlockX(), y = player.getLocation().getBlockY(), z = player.getLocation().getBlockZ();
        long key = Looks.chunkKey(x >> 4, z >> 4);
        int cellMiddle = Math.floorDiv(y, 4) * 4 + 2, cellX = (x & 15) >> 2, cellZ = (z & 15) >> 2;
        String biome = world.getBiome(x, y, z).getKey().toString();
        int original = map.id(biome);
        Map<Long, Looks.Look> sentTo = looks.sent.get(player.getUniqueId());
        Looks.Look sent = sentTo == null ? null : sentTo.get(key);
        Looks.Look now = showing ? new Looks.Look(look.stage, look.lines.get(key)) : Looks.Look.ORIGINAL;
        float[] known = look.lines.get(key);
        lines.add("The world's biome here: " + biome + ". Stage " + look.stage.key() + "; the snow line over you "
                + (known == null ? "not worked out yet" : String.valueOf(known[cellZ * Looks.CELLS + cellX])) + ".");
        if (sent == null) lines.add("Your client was sent this chunk as it is (" + biome + ").");
        else lines.add("Your client was sent: " + map.name(map.map(original, sent.stage(),
                Looks.kind(sent.lines(), cellX, cellZ, cellMiddle))) + (Looks.same(sent, now, look.minY, look.height)
                ? "" : " (out of date: a resend is on its way)") + ". F3 should show the same.");
        return lines;
    }

    public int resentTotal() { return resentTotal; }

    /** How many chunks' snow lines are known now, over every world. */
    public int linesKnown() {
        int count = 0;
        for (Looks.WorldLook look : looks.worlds.values()) count += look.lines.size();
        return count;
    }
}
