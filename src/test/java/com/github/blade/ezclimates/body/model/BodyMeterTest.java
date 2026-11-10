package com.github.blade.ezclimates.body.model;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The body-temperature meter on EZvac's HUD. */
class BodyMeterTest {
    private final ExposureSettings.Hud hud = ExposureSettings.defaults().hud();

    @Test
    void normalSitsMidwayAndTheEndsClamp() {
        assertEquals(0.5, BodyMeter.fraction(98.5, hud), 1e-9);
        assertEquals(0.0, BodyMeter.fraction(85.0, hud), 1e-9, "freezing and below: empty");
        assertEquals(1.0, BodyMeter.fraction(108.0, hud), 1e-9);
        assertEquals(0.0, BodyMeter.fraction(Double.NaN, hud), 1e-9);
        assertTrue(BodyMeter.fraction(104.1, hud) > 0.85, "heatstroke reads nearly full");
    }

    @Test
    void stagesUseEzvacsComfortColours() {
        assertEquals("#6ee07a", BodyMeter.color(Stage.NORMAL, hud));
        assertEquals("#8fd3ff", BodyMeter.color(Stage.CHILLED, hud));
        assertEquals("#5ab0ff", BodyMeter.color(Stage.HYPOTHERMIA, hud));
        assertEquals("#5ab0ff", BodyMeter.color(Stage.FREEZING, hud));
        assertEquals("#ffb347", BodyMeter.color(Stage.OVERHEATING, hud));
        assertEquals("#ff5c5c", BodyMeter.color(Stage.HEAT_EXHAUSTION, hud));
        assertEquals("#ff5c5c", BodyMeter.color(Stage.HEATSTROKE, hud));
    }

    @Test
    void badHudSettingsFallBackWithAWarning() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("hud.colors.hot", "red");
        config.set("hud.enabled", false);
        List<String> warnings = new ArrayList<>();
        ExposureSettings.Hud loaded = ExposureSettings.load(config, warnings::add).hud();
        assertEquals(1, warnings.size(), warnings::toString);
        assertEquals("#ff5c5c", loaded.hotColor());
        assertFalse(loaded.enabled(), "switched off stays off");

        YamlConfiguration backwards = new YamlConfiguration();
        backwards.set("hud.meter.cold-f", 106.0);
        backwards.set("hud.meter.hot-f", 91.0);
        List<String> more = new ArrayList<>();
        assertEquals(91.0, ExposureSettings.load(backwards, more::add).hud().meterColdF());
        assertEquals(1, more.size());
    }
}
