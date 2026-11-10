package com.github.blade.ezclimates.body.model;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/** A config.yml from an older release, brought up to date. */
class ConfigUpgradeTest {
    private static YamlConfiguration read(String resource) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        try (var reader = new InputStreamReader(Objects.requireNonNull(
                ConfigUpgradeTest.class.getResourceAsStream(resource)), StandardCharsets.UTF_8)) {
            config.load(reader);
        }
        return config;
    }

    @Test
    void a010ConfigGainsTheHudAndTheNewRates() throws Exception {
        YamlConfiguration old = read("/body-config-0.1.0.yml");
        old.set("lethal", false);
        old.set("sickness.chance.winter", 0.2);
        List<String> changed = ConfigUpgrade.apply(old, read("/body.yml"));
        assertTrue(changed.contains("hud"), changed::toString);
        assertTrue(changed.stream().anyMatch(change -> change.startsWith("rates")), changed::toString);
        assertEquals(0.068, old.getDouble("rates.cold-f-per-minute"));
        assertEquals(0.054, old.getDouble("rates.heat-f-per-minute"));
        assertEquals(0.7, old.getDouble("rates.recovery-f-per-minute"), "recovery is unchanged");
        assertFalse(old.getBoolean("lethal"), "the server's own choices stay");
        assertEquals(0.2, old.getDouble("sickness.chance.winter"));
        assertFalse(old.getComments("hud").isEmpty(), "new settings come with their comments");

        List<String> warnings = new ArrayList<>();
        ExposureSettings upgraded = ExposureSettings.load(old, warnings::add);
        assertTrue(warnings.isEmpty(), warnings::toString);
        assertEquals(ExposureSettings.defaults().hud(), upgraded.hud());
        assertEquals(List.of(), ConfigUpgrade.apply(old, read("/body.yml")), "a second start changes nothing");
    }

    @Test
    void ratesTheServerChoseAreKept() throws Exception {
        YamlConfiguration tuned = read("/body-config-0.1.0.yml");
        tuned.set("rates.cold-f-per-minute", 0.05);
        ConfigUpgrade.apply(tuned, read("/body.yml"));
        assertEquals(0.05, tuned.getDouble("rates.cold-f-per-minute"));
        assertEquals(0.027, tuned.getDouble("rates.heat-f-per-minute"),
                "only a pair that is still both stock values moves");
    }
}
