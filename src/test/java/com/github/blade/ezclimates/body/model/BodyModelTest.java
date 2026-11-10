package com.github.blade.ezclimates.body.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The body temperature, step by step. */
class BodyModelTest {
    private final ExposureSettings settings = ExposureSettings.defaults();
    private static final double REST = ExposureSettings.Thresholds.REST_F;
    private final double cold = settings.rates().coldFPerMinute();
    private final double heat = settings.rates().heatFPerMinute();
    private final double recovery = settings.rates().recoveryFPerMinute();

    private double minutes(double body, BodyModel.Conditions conditions, double minutes) {
        for (int step = 0; step < minutes * 120; step++) body = BodyModel.step(body, conditions, 0.5, settings);
        return body;
    }

    private static BodyModel.Conditions air(double feelsF) {
        return BodyModel.Conditions.healthy(feelsF, false, 0, false);
    }

    @Test
    void comfortableAirRecoversWithoutOvershooting() {
        assertEquals(REST, minutes(REST, air(70), 10), 1e-9, "stays put");
        assertEquals(95.0 + 2 * recovery, minutes(95.0, air(70), 2), 1e-6, "the recovery rate back up");
        assertEquals(REST, minutes(95.0, air(70), 20), 1e-9, "and stops at 98.6");
        assertEquals(REST, minutes(101.0, air(70), 20), 1e-9, "down from a fever's heat too");
    }

    @Test
    void coldCostsDegreesPerMinuteByHowFarBelowComfort() {
        // 20 F: 30 below comfort x 0.068 = about 2 F a minute.
        assertEquals(0.068, cold, "the shipped rate");
        assertEquals(REST - 30 * cold, minutes(REST, air(20), 1), 1e-6);
        assertEquals(REST - 10 * cold, minutes(REST, air(40), 1), 1e-6);
    }

    @Test
    void heatAddsDegreesPerMinuteByHowFarAboveComfort() {
        // 111 F: 25 above comfort x 0.054 = 1.35 F a minute.
        assertEquals(0.054, heat, "the shipped rate");
        assertEquals(REST + 25 * heat, minutes(REST, air(111), 1), 1e-6);
    }

    @Test
    void waterIsThreeTimesAsFast() {
        BodyModel.Conditions lake = BodyModel.Conditions.healthy(40, true, 0, false);
        assertEquals(REST - 3 * 10 * cold, minutes(REST, lake, 1), 1e-6);
    }

    @Test
    void leatherOnlyHelpsAgainstCold() {
        double full = settings.coldResistance().resistanceF(true, true, true, true);
        assertEquals(15.0, full, 1e-9, "10 points at 1.5 F");
        assertEquals(1.5 * 7, settings.coldResistance().resistanceF(false, true, true, false), 1e-9);
        BodyModel.Conditions dressed = BodyModel.Conditions.healthy(20, false, full, false);
        assertEquals(REST - 15 * cold, minutes(REST, dressed, 1), 1e-6, "feels like 35 F instead of 20 F");
        BodyModel.Conditions mild = BodyModel.Conditions.healthy(45, false, full, false);
        assertEquals(REST, minutes(REST, mild, 5), 1e-9, "only up to comfortable, never warmer");
        BodyModel.Conditions hot = BodyModel.Conditions.healthy(111, false, full, false);
        assertEquals(REST + 25 * heat, minutes(REST, hot, 1), 1e-6, "no help, and no harm, in the heat");
    }

    @Test
    void theBodySettlesInsteadOfClimbingForever() {
        // A warm 91.8 F day: the body runs a little warm and stays there.
        assertEquals(REST + 5.8 * 0.25, minutes(REST, air(91.8), 120), 1e-6);
        assertEquals(Stage.OVERHEATING, Stage.of(minutes(REST, air(91.8), 120), settings.thresholds()));
        // Already overheated there, it cools back to that balance at the recovery rate.
        assertEquals(102.6 - recovery, minutes(102.6, air(91.8), 1), 1e-6);
        assertEquals(REST + 5.8 * 0.25, minutes(102.6, air(91.8), 30), 1e-6);
    }

    @Test
    void eachStageNeedsItsOwnExtreme() {
        ExposureSettings.Thresholds t = settings.thresholds();
        assertTrue(BodyModel.balanceF(air(107), settings) < t.heatstrokeF(), "no heatstroke below ~108 F");
        assertTrue(BodyModel.balanceF(air(108), settings) > t.heatstrokeF());
        assertTrue(BodyModel.balanceF(air(99), settings) < t.severeHeatF(), "severe heat from ~100 F");
        assertTrue(BodyModel.balanceF(air(94), settings) > t.heatExhaustionF(), "heat exhaustion from ~94 F");
        assertTrue(BodyModel.balanceF(air(21), settings) > t.freezingF(), "no freezing above ~20 F");
        assertTrue(BodyModel.balanceF(air(19), settings) < t.freezingF());
        assertTrue(BodyModel.balanceF(air(35), settings) < t.hypothermiaF(), "hypothermia below ~35.6 F");
        assertTrue(BodyModel.balanceF(air(43), settings) < t.chilledF(), "chilled below ~43.6 F");
        BodyModel.Conditions dressed = BodyModel.Conditions.healthy(10, false, 15, false);
        assertEquals(REST - 25 * 0.25, BodyModel.balanceF(dressed, settings), 1e-9, "leather moves the balance too");
        assertEquals(REST, BodyModel.balanceF(BodyModel.Conditions.healthy(130, false, 0, true), settings), 1e-9,
                "Fire Resistance keeps the heat out entirely");
    }

    @Test
    void fireResistanceStopsHeating() {
        BodyModel.Conditions protectedHeat = BodyModel.Conditions.healthy(120, false, 0, true);
        assertEquals(REST, minutes(REST, protectedHeat, 5), 1e-9);
        assertEquals(REST, minutes(101, protectedHeat, 10), 1e-9, "and lets a hot body recover");
    }

    @Test
    void aFeverRaisesTheRestingPointAndTheHeat() {
        BodyModel.Conditions fever = new BodyModel.Conditions(70, false, 0, false, 100.0, 2.0);
        assertEquals(100.0, minutes(REST, fever, 5), 1e-9, "settles at the fever");
        BodyModel.Conditions feverHeat = new BodyModel.Conditions(111, false, 0, false, 100.0, 2.0);
        assertEquals(REST + 2 * 25 * heat, minutes(REST, feverHeat, 1), 1e-6, "heat twice as fast");
    }

    @Test
    void theBodyStaysWithinLimits() {
        assertEquals(ExposureSettings.Thresholds.MINIMUM_F, minutes(REST, air(-60), 600), 1e-9);
        assertEquals(ExposureSettings.Thresholds.MAXIMUM_F, minutes(REST, air(160), 600), 1e-9);
        assertEquals(REST, BodyModel.step(Double.NaN, air(70), 0.5, settings), 1e-9, "a broken value starts over");
    }

    @Test
    void stagesFollowTheThresholds() {
        ExposureSettings.Thresholds t = settings.thresholds();
        assertEquals(Stage.NORMAL, Stage.of(REST, t));
        assertEquals(Stage.NORMAL, Stage.of(97.0, t), "97.0 is still normal");
        assertEquals(Stage.CHILLED, Stage.of(96.9, t));
        assertEquals(Stage.HYPOTHERMIA, Stage.of(94.9, t));
        assertEquals(Stage.SEVERE_HYPOTHERMIA, Stage.of(92.9, t));
        assertEquals(Stage.FREEZING, Stage.of(90.9, t));
        assertEquals(Stage.OVERHEATING, Stage.of(99.6, t));
        assertEquals(Stage.HEAT_EXHAUSTION, Stage.of(100.6, t));
        assertEquals(Stage.SEVERE_HEAT, Stage.of(102.1, t));
        assertEquals(Stage.HEATSTROKE, Stage.of(104.1, t));
        assertTrue(Stage.FREEZING.worseThan(Stage.HYPOTHERMIA));
        assertFalse(Stage.CHILLED.worseThan(Stage.HYPOTHERMIA));
        assertTrue(Stage.HEATSTROKE.cold() == false && Stage.HEATSTROKE.hot());
    }
}
