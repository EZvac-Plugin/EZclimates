package com.github.blade.ezclimates;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.mapper.MappedEntity;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.world.biome.Biomes;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.protocol.world.chunk.impl.v_1_18.Chunk_v1_18;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.DataPalette;
import com.github.retrooper.packetevents.util.Vector2i;
import com.github.retrooper.packetevents.util.mappings.IRegistry;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkBiomes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Rewrites the biomes in the chunks each player is sent, so they see the
 * seasonal copies while the world keeps its own biomes. Runs on network
 * threads: it reads {@link Looks} and the player's biome numbers, nothing
 * else. A chunk whose snow line isn't worked out yet shows each biome's own
 * weather at the stage's colours, and is put right straight after.
 */
public final class ChunkRewriter implements PacketListener {
    private final Looks looks;
    private final Consumer<String> warn;
    private final ConcurrentHashMap<UUID, IdMap> ids = new ConcurrentHashMap<>();
    private volatile boolean warned;

    public ChunkRewriter(Looks looks, Consumer<String> warn) {
        this.looks = looks;
        this.warn = warn;
    }

    /** A player's biome numbers, from the registry their client received; empty without copies. */
    public IdMap ids(User user) {
        UUID player = user.getUUID();
        if (player == null) return null;
        IdMap known = ids.get(player);
        if (known != null) return known;
        IRegistry<?> registry = user.getRegistry(Biomes.getRegistry().getRegistryKey(), user.getClientVersion());
        Map<String, Integer> numbers = new HashMap<>();
        if (registry != null)
            for (Object entry : registry.getEntries())
                if (entry instanceof MappedEntity mapped)
                    numbers.put(mapped.getName().toString(), mapped.getId(user.getClientVersion()));
        IdMap built = IdMap.of(numbers);
        // Only kept once the client has its registry: before that there is nothing to map.
        if (!built.isEmpty()) ids.put(player, built);
        return built;
    }

    /** Forgets a player's numbers: they left, or were sent a new registry. */
    public void forget(UUID player) {
        ids.remove(player);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        boolean data = event.getPacketType() == PacketType.Play.Server.CHUNK_DATA;
        if (!data && event.getPacketType() != PacketType.Play.Server.CHUNK_BIOMES) return;
        try {
            User user = event.getUser();
            UUID player = user == null ? null : user.getUUID();
            if (player == null) return;
            UUID world = looks.playerWorld.get(player);
            Looks.WorldLook look = world == null ? null : looks.worlds.get(world);
            if (look == null) return;
            IdMap map = ids(user);
            if (map == null || map.isEmpty()) return;
            Stage stage = look.stage;
            ConcurrentHashMap<Long, Looks.Look> sent = looks.sent.computeIfAbsent(player, ignored -> new ConcurrentHashMap<>());
            if (data) {
                WrapperPlayServerChunkData wrapper = new WrapperPlayServerChunkData(event);
                Column column = wrapper.getColumn();
                long key = Looks.chunkKey(column.getX(), column.getZ());
                float[] lines = look.lines.get(key);
                BaseChunk[] sections = column.getChunks();
                for (int i = 0; i < sections.length; i++)
                    if (sections[i] instanceof Chunk_v1_18 section && section.getBiomeData() != null)
                        rewrite(section.getBiomeData(), map, stage, lines, look.minY + 16 * i);
                event.markForReEncode(true);
                sent.put(key, new Looks.Look(stage, lines));
            } else {
                WrapperPlayServerChunkBiomes wrapper = new WrapperPlayServerChunkBiomes(event);
                for (Map.Entry<Vector2i, WrapperPlayServerChunkBiomes.ChunkBiomeData> chunk : wrapper.getChunks().entrySet()) {
                    long key = Looks.chunkKey(chunk.getKey().getX(), chunk.getKey().getZ());
                    float[] lines = look.lines.get(key);
                    List<DataPalette> sections = sections(chunk.getValue());
                    for (int i = 0; i < sections.size(); i++)
                        rewrite(sections.get(i), map, stage, lines, look.minY + 16 * i);
                    sent.put(key, new Looks.Look(stage, lines));
                }
                event.markForReEncode(true);
            }
        } catch (RuntimeException failure) {
            // Never break a player's chunks: send them as they are, and say so once.
            if (!warned) {
                warned = true;
                warn.accept("Could not rewrite a chunk's biomes (" + failure + "); it was sent as it is."
                        + " This is said once.");
            }
        }
    }

    /**
     * Swaps each of a section's 64 biome cells for its copy, each cell by its
     * own column's snow line ({@code lines}, null for each biome's own
     * weather) at the cell's middle height; the section starts at {@code baseY}.
     */
    static void rewrite(DataPalette biomes, IdMap map, Stage stage, float[] lines, int baseY) {
        for (int y = 0; y < 4; y++)
            for (int z = 0; z < 4; z++)
                for (int x = 0; x < 4; x++) {
                    int from = biomes.get(x, y, z);
                    int to = map.map(from, stage, Looks.kind(lines, x, z, baseY + y * 4 + 2));
                    if (to != from) biomes.set(x, y, z, to);
                }
    }

    private static final Field SECTIONS;

    static {
        Field field;
        try {
            field = WrapperPlayServerChunkBiomes.ChunkBiomeData.class.getDeclaredField("sections");
            field.setAccessible(true);
        } catch (ReflectiveOperationException | RuntimeException missing) {
            field = null;
        }
        SECTIONS = field;
    }

    /** A chunks-biomes entry's sections: packetevents keeps them private, so they are read directly. */
    @SuppressWarnings("unchecked")
    static List<DataPalette> sections(WrapperPlayServerChunkBiomes.ChunkBiomeData data) {
        if (SECTIONS == null) throw new IllegalStateException("this packetevents keeps a chunk's biomes elsewhere");
        try {
            return (List<DataPalette>) SECTIONS.get(data);
        } catch (IllegalAccessException denied) {
            throw new IllegalStateException(denied);
        }
    }

    /** A chunks-biomes entry holding these sections. */
    static WrapperPlayServerChunkBiomes.ChunkBiomeData data(List<DataPalette> palettes) {
        // createWithSingleBiome can't be used: it makes a storage of 0 bits, which packetevents refuses.
        WrapperPlayServerChunkBiomes.ChunkBiomeData data =
                WrapperPlayServerChunkBiomes.ChunkBiomeData.createWithRepeatingPalette(null, 0);
        List<DataPalette> sections = sections(data);
        sections.clear();
        sections.addAll(palettes);
        return data;
    }
}
