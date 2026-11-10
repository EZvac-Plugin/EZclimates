package com.github.blade.ezclimates.body.model;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/** config.yml, read and checked. */
class ExposureSettingsTest {
    private static YamlConfiguration bundled() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        try (var reader = new InputStreamReader(Objects.requireNonNull(
                ExposureSettingsTest.class.getResourceAsStream("/body.yml")), StandardCharsets.UTF_8)) {
            config.load(reader);
        }
        return config;
    }

    @Test
    void theBundledConfigIsTheDefaults() throws Exception {
        List<String> warnings = new ArrayList<>();
        ExposureSettings bundled = ExposureSettings.load(bundled(), warnings::add);
        ExposureSettings defaults = ExposureSettings.defaults();
        assertTrue(warnings.isEmpty(), warnings::toString);
        assertEquals(defaults.comfort(), bundled.comfort());
        assertEquals(defaults.rates(), bundled.rates());
        assertEquals(defaults.thresholds(), bundled.thresholds());
        assertEquals(defaults.damage(), bundled.damage());
        assertEquals(defaults.coldResistance(), bundled.coldResistance());
        assertEquals(defaults.scope(), bundled.scope());
        assertEquals(defaults.hud(), bundled.hud());
        assertTrue(bundled.sickness().enabled());
        assertArrayEquals(defaults.sickness().chances(), bundled.sickness().chances());
        assertArrayEquals(defaults.sickness().feverF(), bundled.sickness().feverF());
        assertEquals(0.35, bundled.sickness().contagionBonus());
        assertTrue(bundled.damage().lethal(), "deadly by default");
    }

    @Test
    void aBadValueOnlyCostsItsOwnSection() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("comfort.low-f", 90.0);            // above high-f
        config.set("rates.water-factor", 0.5);         // below 1
        config.set("lethal", false);
        config.set("sickness.enabled", false);
        List<String> warnings = new ArrayList<>();
        ExposureSettings settings = ExposureSettings.load(config, warnings::add);
        assertEquals(2, warnings.size(), warnings::toString);
        assertEquals(ExposureSettings.defaults().comfort(), settings.comfort());
        assertEquals(3.0, settings.rates().waterFactor());
        assertFalse(settings.damage().lethal(), "every other section loads as written");
        assertFalse(settings.sickness().enabled(), "sickness can be switched off");
    }

    @Test
    void aBrokenSicknessSectionStillKeepsItsSwitch() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("sickness.enabled", false);
        config.set("sickness.cold-multiplier", 0.2);
        List<String> warnings = new ArrayList<>();
        assertFalse(ExposureSettings.load(config, warnings::add).sickness().enabled());
        assertEquals(1, warnings.size());
    }

    @Test
    void whoIsAffected() {
        ExposureSettings.Scope scope = new ExposureSettings.Scope(java.util.Set.of(), java.util.Set.of("lobby"),
                java.util.Set.of("SURVIVAL", "ADVENTURE"));
        assertTrue(scope.appliesIn("world", "SURVIVAL"));
        assertFalse(scope.appliesIn("world", "CREATIVE"));
        assertFalse(scope.appliesIn("lobby", "SURVIVAL"));
        ExposureSettings.Scope only = new ExposureSettings.Scope(java.util.Set.of("survival_world"), java.util.Set.of(),
                java.util.Set.of("SURVIVAL"));
        assertFalse(only.appliesIn("world", "SURVIVAL"));
        assertTrue(only.appliesIn("survival_world", "survival"));
    }
}
