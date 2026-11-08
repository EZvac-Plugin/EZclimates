package com.github.blade.ezclimates;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * The datapack of seasonal biome copies, written at every start from the
 * running server's own vanilla biome files, so nothing of Mojang's ships in
 * EZclimates and the copies always match the server's version.
 */
public final class SeasonPack {
    public static final String ID = "seasons";
    private static final String BIOMES = "data/minecraft/worldgen/biome/";
    /** Underground: rain never reaches them, so they need no copies. */
    static final Set<String> CAVES = Set.of("deep_dark", "dripstone_caves", "lush_caves", "sulfur_caves", "the_void");
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    /** The vanilla biomes and the data pack format, read from the server's jar. */
    public record Vanilla(Map<String, JsonObject> biomes, int format) {}

    private SeasonPack() {}

    /**
     * Reads the overworld's surface biomes from the jar that holds the
     * server's own data, found through {@code loader}.
     */
    public static Vanilla read(ClassLoader loader) throws IOException {
        URL plains = loader.getResource(BIOMES + "plains.json");
        if (plains == null) throw new IOException("the server's vanilla biomes weren't found");
        try {
            URI uri = plains.toURI();
            if ("jar".equals(uri.getScheme())) {
                try (FileSystem jar = FileSystems.newFileSystem(uri, Map.of())) {
                    return read(jar.getPath("/"));
                } catch (java.nio.file.FileSystemAlreadyExistsException open) {
                    return read(FileSystems.getFileSystem(uri).getPath("/"));
                }
            }
            // An unpacked server: data/ beside the classes.
            return read(Path.of(uri).getParent().getParent().getParent().getParent().getParent());
        } catch (URISyntaxException bad) {
            throw new IOException(bad);
        }
    }

    /** Reads the biomes and format under a root that has {@code data/} and {@code version.json}. */
    static Vanilla read(Path root) throws IOException {
        Set<String> elsewhere = new HashSet<>(tagValues(root, "is_nether"));
        elsewhere.addAll(tagValues(root, "is_end"));
        Map<String, JsonObject> biomes = new TreeMap<>();
        Path folder = root.resolve(BIOMES);
        try (Stream<Path> files = Files.list(folder)) {
            for (Path file : (Iterable<Path>) files.sorted()::iterator) {
                String name = file.getFileName().toString();
                if (!name.endsWith(".json")) continue;
                String biome = name.substring(0, name.length() - 5);
                if (CAVES.contains(biome) || elsewhere.contains(biome)) continue;
                biomes.put(biome, parse(file).getAsJsonObject());
            }
        }
        return new Vanilla(biomes, format(root.resolve("version.json")));
    }

    private static Set<String> tagValues(Path root, String tag) throws IOException {
        Path file = root.resolve("data/minecraft/tags/worldgen/biome/" + tag + ".json");
        Set<String> values = new HashSet<>();
        if (!Files.isRegularFile(file)) return values;
        JsonArray list = parse(file).getAsJsonObject().getAsJsonArray("values");
        for (JsonElement value : list) {
            String id = value.isJsonObject() ? value.getAsJsonObject().get("id").getAsString() : value.getAsString();
            if (!id.startsWith("#")) values.add(id.substring(id.indexOf(':') + 1));
        }
        return values;
    }

    /** The data pack format from the server's version.json. */
    static int format(Path versionJson) throws IOException {
        JsonObject version = parse(versionJson).getAsJsonObject();
        JsonElement pack = version.get("pack_version");
        if (pack != null && pack.isJsonObject()) {
            JsonObject formats = pack.getAsJsonObject();
            if (formats.has("data_major")) return formats.get("data_major").getAsInt();
            if (formats.has("data")) return formats.get("data").getAsInt();
        }
        throw new IOException("version.json gives no data pack format");
    }

    private static JsonElement parse(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        }
    }

    /**
     * Writes the pack into {@code folder}, replacing what an earlier start
     * wrote there.
     *
     * @return how many copies it holds
     */
    public static int write(Path folder, Vanilla vanilla, Palette palette) throws IOException {
        if (Files.exists(folder)) {
            try (Stream<Path> old = Files.walk(folder)) {
                for (Path path : (Iterable<Path>) old.sorted(Comparator.reverseOrder())::iterator) Files.delete(path);
            }
        }
        Path biomes = folder.resolve("data/" + BiomeCopies.NAMESPACE + "/worldgen/biome");
        Files.createDirectories(biomes);
        JsonObject meta = new JsonObject();
        JsonObject pack = new JsonObject();
        pack.addProperty("description", "EZclimates: seasonal copies of the overworld's biomes, shown only to players");
        pack.addProperty("min_format", vanilla.format());
        pack.addProperty("max_format", vanilla.format());
        meta.add("pack", pack);
        Files.writeString(folder.resolve("pack.mcmeta"), GSON.toJson(meta), StandardCharsets.UTF_8);
        int count = 0;
        for (Map.Entry<String, JsonObject> biome : vanilla.biomes().entrySet())
            for (Map.Entry<String, JsonObject> copy : BiomeCopies.of(biome.getKey(), biome.getValue(), palette).entrySet()) {
                Path file = biomes.resolve(copy.getKey() + ".json");
                Files.createDirectories(file.getParent());
                Files.writeString(file, GSON.toJson(copy.getValue()), StandardCharsets.UTF_8);
                count++;
            }
        return count;
    }
}
