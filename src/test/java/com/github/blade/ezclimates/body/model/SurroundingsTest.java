package com.github.blade.ezclimates.body.model;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/** EZclimates 0.2.0: what a body meets besides the air. */
class SurroundingsTest {
    private static final Surroundings DEFAULTS = Surroundings.defaults();
    private static final ExposureSettings BODY = ExposureSettings.defaults();
    private static final ExposureSettings.Comfort COMFORT = BODY.comfort();

    @Test
    void theBundledBodyYmlReadsCleanlyAndMatchesTheDefaults() throws Exception {
        YamlConfiguration bundled = new YamlConfiguration();
        try (var reader = new InputStreamReader(Objects.requireNonNull(
                getClass().getClassLoader().getResourceAsStream("body.yml")), StandardCharsets.UTF_8)) {
            bundled.load(reader);
        }
        List<String> warnings = new ArrayList<>();
        Surroundings read = Surroundings.load(bundled, warnings::add);
        assertEquals(List.of(), warnings);
        assertEquals(DEFAULTS, read);
    }

    @Test
    void rainSoaksInTheOpenAndWaterAtOnce() {
        Surroundings.Wetness wet = DEFAULTS.wetness();
        assertEquals(1.0, Surroundings.wetness(0.0, true, 0.0, 70.0, false, false, 0.5, wet));
        // Three minutes of rain in the open: soaked.
        double after = Surroundings.wetness(0.0, false, 1.0, 50.0, false, false, 180.0, wet);
        assertEquals(1.0, after, 1e-9);
        assertEquals(0.5, Surroundings.wetness(0.0, false, 1.0, 50.0, false, false, 90.0, wet), 1e-9);
        assertTrue(Surroundings.wetness(0.0, false, Surroundings.rainOn("Snow", true, wet), 20.0, false, false, 90.0, wet)
                < 0.25, "snow soaks slower");
        assertEquals(0.0, Surroundings.rainOn("Rain", false, wet), "under cover, rain doesn't reach");
        assertEquals(1.5, Surroundings.rainOn("Thunder", true, wet));
        assertEquals(0.0, Surroundings.rainOn("Clear", true, wet));
    }

    @Test
    void itDriesFasterWarmByAFireAndInTheSun() {
        Surroundings.Wetness wet = DEFAULTS.wetness();
        // Eight minutes out of the rain at 60 F: dry.
        assertEquals(0.0, Surroundings.wetness(1.0, false, 0.0, 60.0, false, false, 480.0, wet), 1e-9);
        double cold = Surroundings.wetness(1.0, false, 0.0, 20.0, false, false, 120.0, wet);
        double mild = Surroundings.wetness(1.0, false, 0.0, 60.0, false, false, 120.0, wet);
        double fire = Surroundings.wetness(1.0, false, 0.0, 20.0, true, false, 120.0, wet);
        assertTrue(cold > mild, "slower in the cold: " + cold + " vs " + mild);
        assertTrue(fire < cold, "a fire dries it: " + fire);
        assertTrue(Surroundings.wetness(1.0, false, 0.0, 60.0, false, true, 120.0, wet) < mild, "the sun dries it");
        assertEquals("Soaked", Surroundings.wetWord(0.9));
        assertEquals("Wet", Surroundings.wetWord(0.5));
        assertEquals("Damp", Surroundings.wetWord(0.2));
        assertEquals("", Surroundings.wetWord(0.05));
    }

    @Test
    void firesWarmMostBesideThemAndNothingPastTheRadius() {
        assertEquals(60.0, Surroundings.fireWarmthF(60.0, 1.0, 4), 1e-9);
        assertEquals(30.0, Surroundings.fireWarmthF(60.0, 3.0, 4), 1e-9);
        assertEquals(0.0, Surroundings.fireWarmthF(60.0, 5.0, 4), 1e-9);
        assertTrue(DEFAULTS.fires().warmthF().get("campfire") > DEFAULTS.fires().warmthF().get("magma_block"));
    }

    @Test
    void workBuildsUpAndFades() {
        Surroundings.Exertion work = DEFAULTS.exertion();
        assertEquals(0.5, Surroundings.exertion(0.0, true, 15.0, work), 1e-9);
        assertEquals(1.0, Surroundings.exertion(0.9, true, 30.0, work), 1e-9);
        assertEquals(0.0, Surroundings.exertion(1.0, false, 45.0, work), 1e-9);
    }

    @Test
    void wetMakesTheColdColderAndFasterButNotTheWater() {
        BodyModel.Extras soaked = new BodyModel.Extras(0, 0, 12, 4, 2, 0, 0);
        BodyModel.Conditions dry = new BodyModel.Conditions(30, false, 0, false, 98.6, 1.0);
        BodyModel.Conditions wet = new BodyModel.Conditions(30, false, 0, false, 98.6, 1.0, soaked);
        assertEquals(18.0, BodyModel.effectiveF(wet, COMFORT), 1e-9);
        double dryAfter = BodyModel.step(98.6, dry, 60, BODY), wetAfter = BodyModel.step(98.6, wet, 60, BODY);
        assertTrue(98.6 - wetAfter > 2.0 * (98.6 - dryAfter), "colder and twice as fast: " + wetAfter + " vs " + dryAfter);
        BodyModel.Conditions swimming = new BodyModel.Conditions(30, true, 0, false, 98.6, 1.0, soaked);
        assertEquals(30.0, BodyModel.effectiveF(swimming, COMFORT), 1e-9, "in water, the water factor does it");
        BodyModel.Conditions hot = new BodyModel.Conditions(100, false, 0, false, 98.6, 1.0, soaked);
        assertEquals(96.0, BodyModel.effectiveF(hot, COMFORT), 1e-9, "a little milder in the heat");
    }

    @Test
    void aWarmMealHoldsTheColdOffLikeLeatherAndACoolOneTheHeat() {
        BodyModel.Conditions stew = new BodyModel.Conditions(30, false, 5, false, 98.6, 1.0,
                new BodyModel.Extras(0, 0, 0, 0, 1, 12, 0));
        assertEquals(47.0, BodyModel.effectiveF(stew, COMFORT), 1e-9, "leather and a stew add up");
        BodyModel.Conditions mild = new BodyModel.Conditions(45, false, 5, false, 98.6, 1.0,
                new BodyModel.Extras(0, 0, 0, 0, 1, 12, 0));
        assertEquals(COMFORT.lowF(), BodyModel.effectiveF(mild, COMFORT), 1e-9, "never past comfortable");
        BodyModel.Conditions melon = new BodyModel.Conditions(95, false, 0, false, 98.6, 1.0,
                new BodyModel.Extras(0, 0, 0, 0, 1, 0, 6));
        assertEquals(89.0, BodyModel.effectiveF(melon, COMFORT), 1e-9);
    }

    @Test
    void firesAndWorkWarmEitherWayArmorOnlyInTheHeat() {
        // Right beside a campfire in a 0 F blizzard: comfortable (68 F), the rest of its warmth a quarter.
        assertEquals(60.0, BodyModel.byFireF(0, 60, COMFORT), 1e-9);
        BodyModel.Conditions campfire = new BodyModel.Conditions(-5, false, 0, false, 98.6, 1.0,
                new BodyModel.Extras(0, 0, 0, 0, 1, 0, 0, 1, 1, 60));
        assertEquals(55.0, BodyModel.effectiveF(campfire, COMFORT), 1e-9, "comfortable beside it at -5 F");
        BodyModel.Conditions twoOff = new BodyModel.Conditions(-5, false, 0, false, 98.6, 1.0,
                new BodyModel.Extras(0, 0, 0, 0, 1, 0, 0, 1, 1, 45));
        assertEquals(40.0, BodyModel.effectiveF(twoOff, COMFORT), 1e-9, "mild two blocks off");
        BodyModel.Conditions desertFire = new BodyModel.Conditions(95, false, 0, false, 98.6, 1.0,
                new BodyModel.Extras(0, 0, 0, 0, 1, 0, 0, 1, 1, 60));
        assertEquals(110.0, BodyModel.effectiveF(desertFire, COMFORT), 1e-9, "a fire in the heat: a quarter, worse");
        assertEquals(76.0, BodyModel.byFireF(40, 60, COMFORT), 1e-9, "a cool room by a fire: comfortable and a bit");
        BodyModel.Conditions coldArmor = new BodyModel.Conditions(20, false, 0, false, 98.6, 1.0,
                new BodyModel.Extras(0, 10, 0, 0, 1, 0, 0));
        assertEquals(20.0, BodyModel.effectiveF(coldArmor, COMFORT), 1e-9, "metal in the cold doesn't warm");
        BodyModel.Conditions sunArmor = new BodyModel.Conditions(85, false, 0, false, 98.6, 1.0,
                new BodyModel.Extras(0, 10, 0, 0, 1, 0, 0));
        assertEquals(95.0, BodyModel.effectiveF(sunArmor, COMFORT), 1e-9, "but bakes in the sun");
    }

    @Test
    void aFireBringsAChilledBodyBackQuickly() {
        // Freezing (91 F) somewhere comfortable: the stock recovery rate, about ten minutes back to 98.6.
        BodyModel.Conditions indoors = new BodyModel.Conditions(65, false, 0, false, 98.6, 1.0);
        assertEquals(91.7, BodyModel.step(91.0, indoors, 60, BODY), 1e-9);
        // Right beside a campfire in a 0 F blizzard (60 F of warmth, 0.07 per degree): 5.2 times as fast.
        double rewarm = 1.0 + 60.0 * DEFAULTS.fires().rewarmPerF();
        BodyModel.Conditions campfire = new BodyModel.Conditions(0, false, 0, false, 98.6, 1.0,
                new BodyModel.Extras(0, 0, 0, 0, 1, 0, 0, rewarm, 1, 60));
        assertEquals(91.0 + 0.7 * 5.2, BodyModel.step(91.0, campfire, 60, BODY), 1e-9);
        assertEquals(98.6, BodyModel.step(91.0, campfire, 130, BODY), 1e-9, "back to normal in about two minutes");
        // An overheated body cools back faster in water.
        BodyModel.Conditions water = new BodyModel.Conditions(70, true, 0, false, 98.6, 1.0,
                new BodyModel.Extras(0, 0, 0, 0, 1, 0, 0, 1, 3, 0));
        assertEquals(102.0, BodyModel.step(104.1, water, 60, BODY), 1e-9);
        assertTrue(DEFAULTS.darkness(), "darkness closes in when dying, by default");
    }

    @Test
    void theColdBurnsFood() {
        Surroundings.Metabolism metabolism = DEFAULTS.metabolism();
        assertEquals(1.0, Surroundings.hungerFactor(0.0, metabolism));
        assertEquals(1.0, Surroundings.hungerFactor(-10.0, metabolism), "never slower than normal");
        assertEquals(1.375, Surroundings.hungerFactor(15.0, metabolism), 1e-9);
        assertEquals(1.75, Surroundings.hungerFactor(60.0, metabolism), 1e-9, "at most 1.75 times");
        assertEquals(6, metabolism.hungryAt());
    }

    @Test
    void badValuesWarnAndFallBackPerSection() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("fires.radius", 40);
        config.set("food.warm.mushroom_stew", "hot");
        List<String> warnings = new ArrayList<>();
        Surroundings read = Surroundings.load(config, warnings::add);
        assertEquals(2, warnings.size(), warnings.toString());
        assertEquals(DEFAULTS.fires(), read.fires());
        assertEquals(DEFAULTS.food(), read.food());
    }
}
