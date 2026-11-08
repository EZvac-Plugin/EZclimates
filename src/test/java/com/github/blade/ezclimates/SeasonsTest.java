package com.github.blade.ezclimates;

import com.github.retrooper.packetevents.protocol.world.chunk.palette.DataPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.GlobalPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.ListPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.SingletonPalette;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The stages, the biome copies, and the numbers players are sent. */
class SeasonsTest {
    private static JsonObject biome(double temperature, double downfall, boolean precipitation, String effects) {
        JsonObject biome = JsonParser.parseString("{\"attributes\":{\"minecraft:visual/sky_color\":\"#78a7ff\"},"
                + "\"carvers\":[\"minecraft:cave\"],\"features\":[[],[\"minecraft:lake_lava_surface\"],[]],"
                + "\"effects\":" + effects + "}").getAsJsonObject();
        biome.addProperty("temperature", temperature);
        biome.addProperty("downfall", downfall);
        biome.addProperty("has_precipitation", precipitation);
        return biome;
    }

    private static final JsonObject PLAINS = biome(0.8, 0.4, true, "{\"water_color\":\"#3f76e4\"}");

    @Test
    void theYearHasFiveLooks() {
        assertEquals(Stage.SPRING, Stage.at(0.0));
        assertEquals(Stage.SUMMER, Stage.at(0.3));
        assertEquals(Stage.AUTUMN_EARLY, Stage.at(0.55));
        assertEquals(Stage.AUTUMN_LATE, Stage.at(0.7));
        assertEquals(Stage.WINTER, Stage.at(0.9));
        assertEquals(Stage.SPRING, Stage.at(1.1), "a new year");
        assertEquals("autumn_late", Stage.AUTUMN_LATE.key());
    }

    @Test
    void theSnowLineDecidesEachSection() {
        assertEquals(Kind.RAIN, Kind.at(100, 64));
        assertEquals(Kind.SNOW, Kind.at(100, 120));
        assertEquals(Kind.SNOW, Kind.at(Double.NEGATIVE_INFINITY, -60), "snow at every height");
        assertEquals(Kind.RAIN, Kind.at(Double.POSITIVE_INFINITY, 300));
        assertEquals(Kind.DRY, Kind.at(Double.NaN, 64), "where rain doesn't reach");
        assertEquals(Kind.RAIN, Kind.vanilla(PLAINS));
        assertEquals(Kind.SNOW, Kind.vanilla(biome(0.0, 0.5, true, "{}")));
        assertEquals(Kind.DRY, Kind.vanilla(biome(2.0, 0.0, false, "{}")));
    }

    @Test
    void vanillaColoursComeOutWithinAFewShades() {
        assertClose(0x91BD59, Colours.grass(0.8, 0.4), "plains grass");
        assertClose(0x77AB2F, Colours.foliage(0.8, 0.4), "plains leaves");
        assertClose(0x79C05A, Colours.grass(0.7, 0.8), "forest grass");
        assertClose(0xBFB755, Colours.grass(2.0, 0.0), "desert grass, its temperature clamped");
        assertClose(0x80B497, Colours.grass(0.0, 0.5), "snowy plains");
        assertEquals("#3f76e4", Colours.format(Colours.parse("#3F76E4")));
        assertThrows(IllegalArgumentException.class, () -> Colours.parse("#fff"));
    }

    private static void assertClose(int expected, int actual, String what) {
        for (int shift : new int[]{16, 8, 0})
            assertTrue(Math.abs(((expected >> shift) & 255) - ((actual >> shift) & 255)) <= 18,
                    what + ": " + Colours.format(expected) + " vs " + Colours.format(actual));
    }

    @Test
    void aCopyChangesOnlyItsWeatherAndColours() {
        Map<String, JsonObject> copies = BiomeCopies.of("plains", PLAINS, Palette.defaults());
        assertEquals(14, copies.size(), "five stages by three weathers, less the original itself");
        assertFalse(copies.containsKey("plains/summer_rain"), "summer with its own rain is plains");
        JsonObject snow = copies.get("plains/winter_snow");
        assertEquals(0.0, snow.get("temperature").getAsDouble(), "cold enough to snow at any height");
        assertTrue(snow.get("has_precipitation").getAsBoolean());
        JsonObject dry = copies.get("plains/summer_dry");
        assertFalse(dry.get("has_precipitation").getAsBoolean());
        assertEquals("#78a7ff", dry.getAsJsonObject("attributes").get("minecraft:visual/sky_color").getAsString(),
                "its sky and spawns stay");
        assertEquals("#3f76e4", dry.getAsJsonObject("effects").get("water_color").getAsString(), "and its water");
        assertEquals(0, dry.getAsJsonArray("carvers").size(), "never generated: nothing to carve");
        JsonArray features = dry.getAsJsonArray("features");
        assertEquals(3, features.size(), "the same steps, all empty");
        assertEquals(0, features.get(1).getAsJsonArray().size());
        // Summer's colours are vanilla's, set outright so the copy's own temperature can't change them.
        assertEquals(Colours.format(Colours.grass(0.8, 0.4)), dry.getAsJsonObject("effects").get("grass_color").getAsString());
        int summer = Colours.parse(dry.getAsJsonObject("effects").get("foliage_color").getAsString());
        int late = Colours.parse(copies.get("plains/autumn_late_rain").getAsJsonObject("effects").get("foliage_color").getAsString());
        assertTrue(((late >> 16) & 255) > ((summer >> 16) & 255) + 40, "late autumn leaves are redder");
        assertEquals(0.8, copies.get("plains/winter_rain").get("temperature").getAsDouble());
        assertFalse(PLAINS.has("grass_color"), "the original is left as it was");
    }

    @Test
    void aJungleBarelyTurnsAndAForestTurnsMost() {
        Palette palette = Palette.defaults();
        int green = 0x59C93C;
        int jungle = palette.foliage(green, Stage.AUTUMN_LATE, Family.of("minecraft:jungle"));
        int forest = palette.foliage(green, Stage.AUTUMN_LATE, Family.of("dark_forest"));
        assertTrue(Math.abs(((jungle >> 16) & 255) - 0x59) < 20, Colours.format(jungle));
        assertTrue(((forest >> 16) & 255) > 0xA0, Colours.format(forest));
        assertEquals(green, palette.foliage(green, Stage.SUMMER, Family.FOREST), "summer is vanilla");
        assertEquals(Family.COLD, Family.of("snowy_taiga"));
        assertEquals(Family.WATER, Family.of("deep_lukewarm_ocean"));
        assertEquals(Family.MOUNTAIN, Family.of("stony_peaks"));
        assertEquals(Family.COLD, Family.of("jagged_peaks"));
    }

    @Test
    void thePaletteReadsFromConfigAndKeepsWhatItCannotRead() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("colours.foliage.winter.color", "#112233");
        config.set("colours.foliage.winter.share", 0.9);
        config.set("colours.grass.spring.color", "green");
        config.set("colours.strength.jungle", 7);
        List<String> warnings = new ArrayList<>();
        Palette palette = Palette.load(config.getConfigurationSection("colours"), warnings::add);
        assertEquals(new Palette.Target(0x112233, 0.9), palette.foliage().get(Stage.WINTER));
        assertEquals(Palette.defaults().grass().get(Stage.SPRING), palette.grass().get(Stage.SPRING));
        assertEquals(Family.JUNGLE.strength(), palette.strength().get(Family.JUNGLE));
        assertEquals(2, warnings.size(), String.valueOf(warnings));
    }

    @Test
    void eachPlayerSeesTheCopyByItsOwnNumber() {
        Map<String, Integer> registry = new HashMap<>();
        registry.put("minecraft:plains", 40);
        registry.put("minecraft:desert", 12);
        int next = 100;
        for (String path : BiomeCopies.of("plains", PLAINS, Palette.defaults()).keySet())
            registry.put("ezclimates:" + path, next++);
        IdMap map = IdMap.of(registry);
        assertEquals(40, map.map(40, Stage.SUMMER, Kind.RAIN), "summer rain is plains itself");
        assertEquals(40, map.map(40, Stage.SUMMER, null), "and so is its own weather");
        assertEquals(registry.get("ezclimates:plains/winter_snow"), map.map(40, Stage.WINTER, Kind.SNOW));
        assertEquals(registry.get("ezclimates:plains/autumn_late_rain"), map.map(40, Stage.AUTUMN_LATE, null),
                "no snow line yet: its own rain, at the stage's colours");
        assertEquals(12, map.map(12, Stage.WINTER, Kind.SNOW), "a biome without copies stays itself");
        assertEquals(7, map.bits(), "numbers up to 113 take 7 bits");
        assertEquals(-1, map.id("minecraft:nowhere"));
    }

    @Test
    void looksCompareSectionBySection() {
        long a = Looks.look(Stage.WINTER, 100f), b = Looks.look(Stage.WINTER, 103f);
        assertTrue(Looks.same(a, b, -64, 384), "no section's middle lies between 100 and 103");
        assertFalse(Looks.same(a, Looks.look(Stage.WINTER, 110f), -64, 384), "the section at 104 turns");
        assertFalse(Looks.same(a, Looks.look(Stage.SPRING, 100f), -64, 384));
        assertFalse(Looks.same(Looks.ORIGINAL, Looks.look(Stage.SUMMER, Float.NaN), -64, 384));
        assertEquals(Stage.WINTER, Looks.stage(a));
        assertEquals(100f, Looks.line(a));
        assertNull(Looks.line(Looks.ORIGINAL));
        assertEquals(-5, Looks.chunkX(Looks.chunkKey(-5, 7)));
        assertEquals(7, Looks.chunkZ(Looks.chunkKey(-5, 7)));
    }

    @Test
    void sectionsAreWrittenAsTheNetworkDoes() {
        int[] one = new int[64];
        java.util.Arrays.fill(one, 40);
        DataPalette single = LookUpdater.palette(one, 10);
        assertInstanceOf(SingletonPalette.class, single.palette);
        assertEquals(40, single.get(3, 3, 3));
        int[] two = one.clone();
        two[63] = 101;
        DataPalette list = LookUpdater.palette(two, 10);
        assertInstanceOf(ListPalette.class, list.palette);
        assertEquals(101, list.get(3, 3, 3), "the last cell is x 3, z 3, y 3");
        assertEquals(40, list.get(0, 0, 0));
        int[] many = new int[64];
        for (int i = 0; i < 64; i++) many[i] = 500 + i % 12;
        DataPalette global = LookUpdater.palette(many, 10);
        assertInstanceOf(GlobalPalette.class, global.palette);
        assertEquals(500 + 63 % 12, global.get(3, 3, 3));
        DataPalette rewritten = LookUpdater.palette(one, 10);
        Map<String, Integer> registry = new HashMap<>(Map.of("minecraft:plains", 40));
        registry.put("ezclimates:plains/winter_snow", 77);
        registry.put("ezclimates:plains/summer_snow", 78);
        registry.put("ezclimates:plains/summer_dry", 79);
        ChunkRewriter.rewrite(rewritten, IdMap.of(registry), Stage.WINTER, Kind.SNOW);
        assertEquals(77, rewritten.get(2, 1, 0));
    }

    @Test
    void thePackIsWrittenFromTheServersOwnBiomes(@TempDir Path folder) throws Exception {
        Path server = folder.resolve("server");
        Path biomes = server.resolve("data/minecraft/worldgen/biome");
        Files.createDirectories(biomes);
        Files.writeString(biomes.resolve("plains.json"), PLAINS.toString(), StandardCharsets.UTF_8);
        Files.writeString(biomes.resolve("lush_caves.json"), PLAINS.toString(), StandardCharsets.UTF_8);
        Files.writeString(biomes.resolve("nether_wastes.json"), PLAINS.toString(), StandardCharsets.UTF_8);
        Path tags = server.resolve("data/minecraft/tags/worldgen/biome");
        Files.createDirectories(tags);
        Files.writeString(tags.resolve("is_nether.json"), "{\"values\":[\"minecraft:nether_wastes\"]}");
        Files.writeString(server.resolve("version.json"), "{\"pack_version\":{\"resource_major\":97,\"data_major\":121}}");
        SeasonPack.Vanilla vanilla = SeasonPack.read(server);
        assertEquals(List.of("plains"), List.copyOf(vanilla.biomes().keySet()), "no caves and nothing of the Nether");
        assertEquals(121, vanilla.format());
        Path pack = folder.resolve("pack");
        Files.createDirectories(pack.resolve("data/old"));
        assertEquals(14, SeasonPack.write(pack, vanilla, Palette.defaults()));
        assertFalse(Files.exists(pack.resolve("data/old")), "an earlier start's pack is replaced");
        assertTrue(Files.isRegularFile(pack.resolve("data/ezclimates/worldgen/biome/plains/winter_snow.json")));
        JsonObject meta = JsonParser.parseString(Files.readString(pack.resolve("pack.mcmeta"))).getAsJsonObject();
        assertEquals(121, meta.getAsJsonObject("pack").get("min_format").getAsInt());
        assertEquals(121, meta.getAsJsonObject("pack").get("max_format").getAsInt());
    }

    @Test
    void itNeedsApiOnePointNine() {
        assertTrue(EzClimatesPlugin.supported("1.9"));
        assertTrue(EzClimatesPlugin.supported("1.12"));
        assertFalse(EzClimatesPlugin.supported("1.8"));
        assertFalse(EzClimatesPlugin.supported("2.0"));
    }

    @Test
    void eachPlacedBlockIsRecordedInANumber() {
        int snow = Placed.pack(15, -64, 3, Placed.What.SNOW), ice = Placed.pack(0, 319, 15, Placed.What.ICE);
        assertEquals(15, Placed.x(snow));
        assertEquals(-64, Placed.y(snow), "heights below 0 too");
        assertEquals(3, Placed.z(snow));
        assertEquals(Placed.What.SNOW, Placed.what(snow));
        assertEquals(319, Placed.y(ice));
        assertEquals(Placed.What.ICE, Placed.what(ice));
        assertEquals(-2048, Placed.y(Placed.pack(0, -2048, 0, Placed.What.ICE)));
        assertEquals(2047, Placed.y(Placed.pack(0, 2047, 0, Placed.What.ICE)));
        Placed placed = new Placed();
        assertTrue(placed.add(snow));
        assertFalse(placed.add(snow), "once");
        for (int i = 0; i < 20; i++) placed.add(Placed.pack(i & 15, i, 0, Placed.What.SNOW));
        assertTrue(placed.remove(snow));
        assertFalse(placed.contains(snow));
        Placed again = new Placed(placed.toArray());
        assertEquals(placed.size(), again.size(), "as saved in the chunk and read back");
        assertTrue(new Placed(null).isEmpty());
    }
}
