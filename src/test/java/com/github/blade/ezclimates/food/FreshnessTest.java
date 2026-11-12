package com.github.blade.ezclimates.food;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/** EZclimates 0.3.0: how fresh food is, kept lazily on each stack. */
class FreshnessTest {
    private static final double BEEF_LIFE = 4 * Freshness.DAY;

    @Test
    void theRateCurve() {
        assertEquals(1.0, Freshness.rate(70, 4, 20), 1e-9);
        assertEquals(2.0, Freshness.rate(88, 4, 20), 1e-9, "twice as fast every 18 F warmer");
        assertEquals(0.25, Freshness.rate(38, 4, 20), 1e-9, "a fridge keeps beef four times longer");
        assertEquals(0.05, Freshness.rate(28, 4, 20), 1e-9, "frozen, twenty times");
        assertEquals(0.05, Freshness.rate(0, 4, 20), 1e-9);
        assertTrue(Freshness.rate(54, 4, 20) < 1.0 && Freshness.rate(54, 4, 20) > 0.25, "a cool cellar in between");
        assertTrue(Freshness.rate(33, 4, 20) < 0.25 && Freshness.rate(33, 4, 20) > 0.05);
    }

    @Test
    void freshBeefOnATableSpoilsInFourDaysAndInAFridgeLastsFourTimesAsLong() {
        long now = 1_000_000L;
        Freshness.Kept table = Freshness.fresh(BEEF_LIFE, now, 1.0);
        assertEquals(Freshness.Stage.FRESH, Freshness.stage(Freshness.leftRoomTicks(table, now), BEEF_LIFE));
        long threeDays = now + 3 * Freshness.DAY;
        assertEquals(Freshness.Stage.STALE, Freshness.stage(Freshness.leftRoomTicks(table, threeDays), BEEF_LIFE));
        long fiveDays = now + 5 * Freshness.DAY;
        assertEquals(Freshness.Stage.SPOILED, Freshness.stage(Freshness.leftRoomTicks(table, fiveDays), BEEF_LIFE));
        assertEquals(Freshness.Stage.GONE, Freshness.stage(Freshness.leftRoomTicks(table, now + 7 * Freshness.DAY), BEEF_LIFE));

        Freshness.Kept fridge = Freshness.fresh(BEEF_LIFE, now, Freshness.rate(37, 4, 20));
        assertEquals(Freshness.Stage.FRESH, Freshness.stage(Freshness.leftRoomTicks(fridge, fiveDays), BEEF_LIFE),
                "five days in a fridge, still fresh");
        assertTrue(Freshness.daysLeft(fridge, now) > 14.0, "about 16 days in the fridge: " + Freshness.daysLeft(fridge, now));
    }

    @Test
    void reanchoringCarriesTheLifeLeft() {
        long now = 0;
        Freshness.Kept table = Freshness.fresh(BEEF_LIFE, now, 1.0);
        long twoDays = 2 * Freshness.DAY;
        // Two days on the table (half its life), then into a freezer.
        Freshness.Kept frozen = Freshness.reanchor(table, BEEF_LIFE, twoDays, Freshness.rate(0, 4, 20));
        double left = Freshness.leftRoomTicks(frozen, twoDays);
        assertEquals(2 * Freshness.DAY, left, BEEF_LIFE / 20 + 1, "half its life left, give or take the rounding");
        // Twenty days frozen use about one day of its life (the rate kept in quarter-octave steps: 0.0526, not 0.05).
        double stepped = Freshness.rateOf(Freshness.step(Freshness.rate(0, 4, 20)));
        double later = Freshness.leftRoomTicks(frozen, twoDays + 20 * Freshness.DAY);
        assertEquals(left - 20 * Freshness.DAY * stepped, later, 1.0);
        assertEquals(1.0, 20 * stepped, 0.1, "about a day");
    }

    @Test
    void foodOfAboutTheSameAgeInTheSamePlaceStacks() {
        double rate = Freshness.rate(37, 4, 20);
        Freshness.Kept first = Freshness.fresh(BEEF_LIFE, 100_000L, rate);
        Freshness.Kept second = Freshness.fresh(BEEF_LIFE, 100_400L, rate);   // twenty seconds later
        assertEquals(first, second);
        Freshness.Kept muchLater = Freshness.fresh(BEEF_LIFE, 100_000L + 2 * Freshness.DAY, rate);
        assertNotEquals(first, muchLater);
        // Rounding is never generous.
        assertTrue(Freshness.leftRoomTicks(first, 100_000L) <= BEEF_LIFE);
    }

    @Test
    void theBundledFoodYmlReadsCleanly() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        try (var reader = new InputStreamReader(Objects.requireNonNull(
                getClass().getClassLoader().getResourceAsStream("food.yml")), StandardCharsets.UTF_8)) {
            config.load(reader);
        }
        List<String> warnings = new ArrayList<>();
        FoodSettings settings = FoodSettings.load(config, warnings::add);
        assertEquals(List.of(), warnings);
        assertFalse(settings.onByDefault(), "it starts off: spoiled food is gone");
        assertEquals(4.0, settings.food("beef").days());
        assertEquals("rotten_flesh", settings.food("beef").into());
        assertEquals("bowl", settings.food("mushroom_stew").into());
        assertEquals("bucket", settings.food("milk_bucket").into());
        assertNull(settings.food("baked_potato").into(), "a baked potato just goes");
        for (String never : List.of("golden_apple", "golden_carrot", "honey_bottle", "dried_kelp", "rotten_flesh"))
            assertNull(settings.food(never), never + " never spoils");
        assertNull(settings.food("stone"), "not food");
    }

    @Test
    void aBadEntryIsLeftOutWithAWarning() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("foods.beef.days", 0);
        config.set("foods.apple.days", 5);
        config.set("pace", -1);
        List<String> warnings = new ArrayList<>();
        FoodSettings settings = FoodSettings.load(config, warnings::add);
        assertEquals(2, warnings.size(), warnings.toString());
        assertNull(settings.food("beef"));
        assertEquals(5.0, settings.food("apple").days());
        assertEquals(1.0, settings.pace());
    }
}
