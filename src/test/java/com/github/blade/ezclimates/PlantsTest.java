package com.github.blade.ezclimates;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Crops grow by the air where they stand. */
class PlantsTest {
    private final PlantSettings stock = PlantSettings.defaults();

    @Test
    void growthFollowsTheAir() {
        assertEquals(0.0, stock.growth(20, 50), "a winter field doesn't grow");
        assertEquals(0.0, stock.growth(36, 50), "nor at the edge of frost");
        assertEquals(0.5, stock.growth(48, 50), 1e-9, "a cool spring day: half pace");
        assertEquals(1.0, stock.growth(60, 50), 1e-9);
        assertEquals(1.0, stock.growth(72, 45), 1e-9, "a heated greenhouse grows as in vanilla");
        assertEquals(1.5, stock.growth(80, 70), 1e-9, "warm and humid: faster");
        assertEquals(0.75, stock.growth(80, 20), 1e-9, "dry air: slower");
        assertEquals(1.0, stock.growth(80, Double.NaN), 1e-9, "no humidity to read counts as ordinary");
        assertEquals(0.5, stock.growth(105, 50), 1e-9, "great heat slows it again");
        assertEquals(0.0, stock.growth(120, 50));
        assertEquals(1.0, stock.growth(Double.NaN, 50), "no reading: as vanilla");
        assertEquals(0.5, stock.growth(48, 80), 1e-9, "humidity only helps once it is warm");
        double before = -1;
        for (double f = 30; f <= 60; f += 0.5) {
            double now = stock.growth(f, 50);
            assertTrue(now >= before, "never slower as it warms to the best band: " + f);
            before = now;
        }
        assertEquals("too cold to grow", stock.reason(20, 50));
        assertEquals("cool, so slow", stock.reason(48, 50));
        assertEquals("warm and humid, so faster", stock.reason(80, 70));
        assertEquals("comfortable", stock.reason(70, 45));
    }

    @Test
    void settingsReadLeniently() {
        YamlConfiguration config = new YamlConfiguration();
        List<String> warnings = new ArrayList<>();
        assertEquals(stock, PlantSettings.load(config, warnings::add));
        assertEquals(stock, PlantSettings.load(null, warnings::add));
        config.set("crops.stops-below-f", 70.0);   // above full-from-f
        config.set("crops.humid-boost", 9.0);
        config.set("max-per-chunk", 2);
        config.set("flowers", false);
        PlantSettings read = PlantSettings.load(config, warnings::add);
        assertEquals(36.0, read.stopsBelowF(), "an order that makes no sense keeps the stock one");
        assertEquals(1.5, read.humidBoost());
        assertEquals(2, read.maxPerChunk());
        assertFalse(read.flowers());
        assertEquals(2, warnings.size(), warnings::toString);
        assertTrue(stock.leafLitter() && stock.petals(), "leaf litter and petals come with the stock settings");
        assertEquals(24, stock.maxCoverPerChunk(), "ground cover lies thicker than flowers stand");
    }

    @Test
    void onlyBroadleafTreesShedAndGroundCoverIsToldApart() {
        assertTrue(Plants.shedsLeaves(org.bukkit.Material.OAK_LEAVES));
        assertTrue(Plants.shedsLeaves(org.bukkit.Material.BIRCH_LEAVES));
        assertFalse(Plants.shedsLeaves(org.bukkit.Material.SPRUCE_LEAVES), "evergreens keep theirs");
        assertFalse(Plants.shedsLeaves(org.bukkit.Material.JUNGLE_LEAVES));
        assertFalse(Plants.shedsLeaves(org.bukkit.Material.CHERRY_LEAVES), "cherries drop petals in spring instead");
        assertTrue(Plants.cover(org.bukkit.Material.LEAF_LITTER) && Plants.cover(org.bukkit.Material.PINK_PETALS));
        assertFalse(Plants.cover(org.bukkit.Material.POPPY) || Plants.cover(org.bukkit.Material.BROWN_MUSHROOM)
                || Plants.cover(org.bukkit.Material.WILDFLOWERS));
    }
}
