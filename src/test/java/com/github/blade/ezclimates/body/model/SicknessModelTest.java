package com.github.blade.ezclimates.body.model;

import com.github.blade.hvac.api.SeasonView;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** The optional sickness system's arithmetic. */
class SicknessModelTest {
    private final ExposureSettings.Sickness settings = ExposureSettings.defaults().sickness();

    @Test
    void theChanceFollowsTheSeasonTheColdAndContact() {
        assertEquals(0.01, SicknessModel.chance(SeasonView.Season.SUMMER, 0, false, settings), 1e-12);
        assertEquals(0.08, SicknessModel.chance(SeasonView.Season.WINTER, 0, false, settings), 1e-12);
        assertEquals(0.03, SicknessModel.chance(null, 0, false, settings), 1e-12, "no seasons");
        assertEquals(0.32, SicknessModel.chance(SeasonView.Season.WINTER, 1.0, false, settings), 1e-12,
                "a whole day cold: four times");
        assertEquals(0.20, SicknessModel.chance(SeasonView.Season.WINTER, 0.5, false, settings), 1e-12);
        assertEquals(0.39, SicknessModel.chance(SeasonView.Season.AUTUMN, 0, true, settings), 1e-12,
                "35 points for time near someone sick");
        assertEquals(0.67, SicknessModel.chance(SeasonView.Season.WINTER, 1.0, true, settings), 1e-12);
        assertEquals(0.03, SicknessModel.chance(SeasonView.Season.SPRING, Double.NaN, false, settings), 1e-12);
    }

    @Test
    void theChanceIsCapped() {
        ExposureSettings.Sickness eager = new ExposureSettings.Sickness(true, new double[] {0.9, 0.9, 0.9, 0.9, 0.9},
                4.0, 10, 30, 0.35, 0.95, new double[] {0.6, 0.3, 0.1}, new double[] {99.4, 100, 100.6},
                new double[] {1.5, 2, 2.5}, new double[] {5, 3, 2}, 2, 3, 0.25, 1.0);
        assertEquals(0.95, SicknessModel.chance(SeasonView.Season.WINTER, 1.0, true, eager), 1e-12);
    }

    @Test
    void severityAndDurationFollowTheirOdds() {
        Random random = new Random(42);
        Map<SicknessModel.Severity, Integer> counts = new EnumMap<>(SicknessModel.Severity.class);
        int twos = 0, threes = 0;
        for (int i = 0; i < 20_000; i++) {
            counts.merge(SicknessModel.severity(random, settings), 1, Integer::sum);
            double days = SicknessModel.begin(SicknessModel.Severity.MILD, random, settings).daysLeft();
            if (days == 2.0) twos++;
            else if (days == 3.0) threes++;
            else fail("illnesses last 2 or 3 days, not " + days);
        }
        assertEquals(0.6, counts.get(SicknessModel.Severity.MILD) / 20_000.0, 0.02);
        assertEquals(0.3, counts.get(SicknessModel.Severity.MODERATE) / 20_000.0, 0.02);
        assertEquals(0.1, counts.get(SicknessModel.Severity.SEVERE) / 20_000.0, 0.02);
        assertEquals(0.5, twos / 20_000.0, 0.02);
        assertEquals(0.5, threes / 20_000.0, 0.02);
    }

    @Test
    void daysPassAndWaterHelpsUpToADay() {
        SicknessModel.Illness illness = new SicknessModel.Illness(SicknessModel.Severity.MODERATE, 3, 0);
        for (int bottle = 0; bottle < 10; bottle++) illness = SicknessModel.drank(illness, settings);
        assertEquals(2.0, illness.daysLeft(), 1e-12, "ten bottles still take off only a day");
        assertEquals(1.0, illness.waterSaved(), 1e-12);
        illness = SicknessModel.dayPassed(illness);
        assertFalse(SicknessModel.over(illness));
        illness = SicknessModel.dayPassed(illness);
        assertTrue(SicknessModel.over(illness));

        SicknessModel.Illness two = new SicknessModel.Illness(SicknessModel.Severity.MILD, 2, 0);
        two = SicknessModel.dayPassed(two);
        for (int bottle = 0; bottle < 4; bottle++) two = SicknessModel.drank(two, settings);
        assertTrue(SicknessModel.over(two), "a 2-day illness with a full day of water is over after one day");
    }

    @Test
    void aFeverHasItsOwnRestingPointHeatAndNausea() {
        assertEquals(99.4, SicknessModel.feverF(SicknessModel.Severity.MILD, settings));
        assertEquals(100.6, SicknessModel.feverF(SicknessModel.Severity.SEVERE, settings));
        assertEquals(2.0, SicknessModel.heatMultiplier(SicknessModel.Severity.MODERATE, settings));
        assertEquals(2.0, SicknessModel.nauseaEveryMinutes(SicknessModel.Severity.SEVERE, settings));
        assertEquals(SicknessModel.Severity.SEVERE, SicknessModel.Severity.parse(" Severe "));
        assertNull(SicknessModel.Severity.parse("deadly"));
    }
}
