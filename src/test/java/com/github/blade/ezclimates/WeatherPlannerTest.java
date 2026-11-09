package com.github.blade.ezclimates;

import com.github.blade.hvac.api.SeasonView;
import com.github.blade.hvac.api.WeatherEvent;
import com.github.blade.hvac.api.WeatherPlan;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.*;

/** The weather planned days ahead: fronts, snowstorms, thunder, heat waves and cold snaps, and forecasts. */
class WeatherPlannerTest {
    private static final long DAY = WeatherPlanner.DAY, HOUR = WeatherPlanner.HOUR;
    private static final IntFunction<SeasonView.Season> WINTER = days -> SeasonView.Season.WINTER;
    private static final IntFunction<SeasonView.Season> SUMMER = days -> SeasonView.Season.SUMMER;

    private static WeatherSettings settings(int exactDays, double revise) {
        WeatherSettings d = WeatherSettings.defaults();
        return new WeatherSettings(true, d.seasons(), d.snowstormChance(), d.heatWave(), d.coldSnap(), exactDays,
                revise, d.certaintyLossPerDay(), d.snowstormSnowRate(), d.snowstormSky());
    }

    /** Lives through {@code days} days, a look each morning, and keeps every front and swing ever planned. */
    private static Set<Object> live(WeatherPlanner planner, WeatherPlanner.Plan plan, int days, long seed,
                                    IntFunction<SeasonView.Season> season) {
        Set<Object> seen = new HashSet<>();
        for (int day = 0; day < days; day++) {
            long now = day * DAY + 100;
            planner.revise(plan, now, seed, season);
            for (WeatherPlanner.Front front : plan.fronts()) if (front.start() < now + DAY) seen.add(front);
            for (WeatherPlanner.Swing swing : plan.swings()) if (swing.start() < now + DAY) seen.add(swing);
        }
        return seen;
    }

    @Test
    void theSameWorldPlansTheSameWeather() {
        WeatherPlanner planner = new WeatherPlanner(WeatherSettings.defaults());
        WeatherPlanner.Plan a = new WeatherPlanner.Plan(), b = new WeatherPlanner.Plan();
        planner.extend(a, 5_000, 42L, WINTER);
        planner.extend(b, 5_000, 42L, WINTER);
        assertEquals(a.fronts(), b.fronts());
        assertEquals(a.swings(), b.swings());
        WeatherPlanner.Plan other = new WeatherPlanner.Plan();
        planner.extend(other, 5_000, 43L, WINTER);
        assertNotEquals(a.fronts(), other.fronts(), "another world, other weather");
        assertTrue(a.until >= WeatherPlanner.dayStart(5_000) + WeatherPlanner.HORIZON_DAYS * DAY, "eight days ahead");
    }

    @Test
    void winterBringsLongFrontsAndAThirdOfThemSnowstorms() {
        WeatherSettings settings = settings(2, 0.0);
        WeatherPlanner planner = new WeatherPlanner(settings);
        Set<Object> seen = live(planner, new WeatherPlanner.Plan(), 2_000, 7L, WINTER);
        long fronts = seen.stream().filter(o -> o instanceof WeatherPlanner.Front).count();
        long storms = seen.stream().filter(o -> o instanceof WeatherPlanner.Front f && f.snowstorm()).count();
        long thunder = seen.stream().filter(o -> o instanceof WeatherPlanner.Front f && f.thunderFrom() >= 0).count();
        double share = storms / (double) fronts;
        assertTrue(fronts > 400, "a front every few days: " + fronts);
        assertTrue(Math.abs(share - 0.35) < 0.06, "about 35% snowstorms: " + share);
        assertTrue(thunder < fronts * 0.06, "winter thunder is rare: " + thunder + " of " + fronts);
        for (Object o : seen)
            if (o instanceof WeatherPlanner.Front f) {
                long hours = (f.end() - f.start()) / HOUR;
                assertTrue(hours >= 8 && hours <= 30, "8 to 30 hours: " + hours);
                assertFalse(f.snowstorm() && f.thunderFrom() >= 0, "a snowstorm never thunders");
            }
        long colds = seen.stream().filter(o -> o instanceof WeatherPlanner.Swing s && s.peakF() < 0).count();
        long heats = seen.stream().filter(o -> o instanceof WeatherPlanner.Swing s && s.peakF() > 0).count();
        assertTrue(colds > 20, "cold snaps in winter: " + colds);
        assertEquals(0, heats, "never a heat wave in winter");
    }

    @Test
    void summerThundersAndHasHeatWaves() {
        WeatherPlanner planner = new WeatherPlanner(settings(2, 0.0));
        Set<Object> seen = live(planner, new WeatherPlanner.Plan(), 2_000, 9L, SUMMER);
        long fronts = seen.stream().filter(o -> o instanceof WeatherPlanner.Front).count();
        long thunder = seen.stream().filter(o -> o instanceof WeatherPlanner.Front f && f.thunderFrom() >= 0).count();
        assertTrue(Math.abs(thunder / (double) fronts - 0.55) < 0.07, "about 55% bring thunder: " + thunder + "/" + fronts);
        assertTrue(seen.stream().noneMatch(o -> o instanceof WeatherPlanner.Front f && f.snowstorm()), "no summer snowstorms");
        assertTrue(seen.stream().anyMatch(o -> o instanceof WeatherPlanner.Swing s && s.peakF() >= 8), "heat waves");
    }

    @Test
    void theSureDaysNeverChangeAndLaterOnesMay() {
        // revise-chance 1: every morning the first day past the sure ones is planned afresh.
        WeatherPlanner planner = new WeatherPlanner(settings(2, 1.0));
        final WeatherPlanner.Plan plan = new WeatherPlanner.Plan();
        boolean changed = false;
        for (int day = 0; day < 30; day++) {
            long now = day * DAY + 100;
            planner.revise(plan, now, 3L, WINTER);
            // Tomorrow is still sure tomorrow morning: what starts on it must survive the next revision.
            final long from = (day + 1) * DAY, to = (day + 2) * DAY, beyond = (day + 2) * DAY;
            List<WeatherPlanner.Front> sure = plan.fronts().stream().filter(f -> f.start() >= from && f.start() < to).toList();
            List<WeatherPlanner.Front> later = plan.fronts().stream().filter(f -> f.start() >= beyond + DAY).toList();
            planner.revise(plan, (day + 1) * DAY + 100, 3L, WINTER);
            List<WeatherPlanner.Front> after = plan.fronts();
            assertTrue(after.containsAll(sure), "day " + day + ": the sure days kept " + sure + " in " + after);
            changed |= !after.containsAll(later);
        }
        assertTrue(changed, "later days were planned afresh");
    }

    @Test
    void forecastsAreSureNearAndLeanToTheUsualOddsFarOut() {
        WeatherSettings settings = settings(2, 0.0);
        WeatherPlanner planner = new WeatherPlanner(settings);
        WeatherPlanner.Plan plan = new WeatherPlanner.Plan();
        plan.until = 9 * DAY;
        // Rain every day but the last few, in winter.
        for (int d = 0; d < 6; d++) plan.fronts.add(new WeatherPlanner.Front(d * DAY + 2 * HOUR, d * DAY + 10 * HOUR, -1, false));
        List<WeatherPlan.Outlook> outlook = planner.outlook(plan, 100, 7, WINTER);
        assertEquals(1.0, outlook.get(0).rainChance(), "today: sure");
        assertEquals(1.0, outlook.get(1).rainChance(), "tomorrow: sure");
        assertTrue(outlook.get(2).rainChance() < 1.0 && outlook.get(2).rainChance() > outlook.get(4).rainChance(),
                "less sure each day out: " + outlook);
        double usual = settings.season(SeasonView.Season.WINTER).rainChance();
        assertTrue(outlook.get(6).rainChance() > 0.0 && outlook.get(6).rainChance() < usual,
                "a dry day far out still has some chance: " + outlook.get(6));
        WeatherPlanner exact = new WeatherPlanner(settings(7, 0.0));
        assertTrue(exact.outlook(plan, 100, 7, WINTER).stream().allMatch(day -> day.rainChance() == 0.0 || day.rainChance() == 1.0),
                "with exact-days 7, every day is sure");
    }

    @Test
    void eventsAreNamedAndTimed() {
        WeatherPlanner planner = new WeatherPlanner(settings(2, 0.0));
        WeatherPlanner.Plan plan = new WeatherPlanner.Plan();
        plan.until = 9 * DAY;
        plan.fronts.add(new WeatherPlanner.Front(8 * HOUR, 20 * HOUR, -1, true));            // today 14:00
        plan.fronts.add(new WeatherPlanner.Front(DAY + 4 * HOUR, DAY + 12 * HOUR, DAY + 6 * HOUR, false));
        plan.swings.add(new WeatherPlanner.Swing(2 * DAY, 6 * DAY, -12));
        List<WeatherPlan.Outlook> outlook = planner.outlook(plan, 100, 7, WINTER);
        assertEquals(Optional.of(WeatherEvent.SNOWSTORM), outlook.get(0).event());
        assertEquals("Snowstorm from 14:00", outlook.get(0).note());
        assertEquals("Thunder from 12:00", outlook.get(1).note());
        assertEquals(Optional.of(WeatherEvent.COLD_SNAP), outlook.get(3).event());
        assertEquals("Cold snap likely", outlook.get(3).note(), "on a less sure day");
        assertTrue(outlook.get(3).shiftF() < -5, "a few days into it, much colder: " + outlook.get(3).shiftF());
        assertEquals(Optional.of(WeatherEvent.SNOWSTORM), plan.eventAt(10 * HOUR));
        assertEquals(Optional.of(WeatherEvent.THUNDERSTORM), plan.eventAt(DAY + 7 * HOUR));
        assertEquals(Optional.empty(), plan.eventAt(DAY + 5 * HOUR), "rain, before its thunder");
        WeatherPlanner.Swing snap = plan.swings().get(0);
        assertEquals(0.0, snap.shiftAt(2 * DAY), 1e-9, "eases in");
        assertEquals(-12.0, snap.shiftAt(4 * DAY), 1e-9, "full in its middle");
        assertTrue(snap.shiftAt(6 * DAY - 1) > -1.0, "eases out");
    }

    @Test
    void anAdminsWeatherAndSleepingAreTakenIn() {
        WeatherPlanner.Plan plan = new WeatherPlanner.Plan();
        plan.fronts.add(new WeatherPlanner.Front(10 * HOUR, 20 * HOUR, 15 * HOUR, false));
        plan.fronts.add(new WeatherPlanner.Front(30 * HOUR, 40 * HOUR, -1, false));
        plan.endFrontAt(12 * HOUR);
        assertEquals(new WeatherPlanner.Front(10 * HOUR, 12 * HOUR, -1, false), plan.fronts().get(0),
                "sleeping through it ends it, thunder and all");
        plan.insert(new WeatherPlanner.Front(25 * HOUR, 35 * HOUR, 25 * HOUR, false));
        assertEquals(2, plan.fronts().size(), "/weather thunder replaces the front it overlaps");
        assertEquals(25 * HOUR, plan.fronts().get(1).start());
        plan.cut(26 * HOUR);
        assertEquals(2, plan.fronts().size(), "a cut keeps what began before it");
    }
}
