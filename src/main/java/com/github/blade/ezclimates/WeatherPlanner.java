package com.github.blade.ezclimates;

import com.github.blade.hvac.api.SeasonView;
import com.github.blade.hvac.api.WeatherEvent;
import com.github.blade.hvac.api.WeatherPlan;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.function.IntFunction;

/**
 * One world's weather, planned days ahead: fronts (rain or snow, some with
 * thunder, some in winter snowstorms) between dry spells, and heat waves and
 * cold snaps, each season by its own odds. Times are the world's full time in
 * ticks. Pure: the plugin drives Minecraft's weather from it and saves it.
 */
public final class WeatherPlanner {
    public static final long HOUR = 1_000L;
    public static final long DAY = 24_000L;
    /** How far ahead the plan reaches: a station's seven days, and a day to spare. */
    public static final int HORIZON_DAYS = 8;
    /** A day counts as wet when a front covers this much of it. */
    static final long WET_TICKS = 1_500L;

    /**
     * A front: rain or snow from {@code start} to {@code end}, thunder from
     * {@code thunderFrom} (-1 for none), and in a snowstorm, heavy snow and a gale.
     */
    public record Front(long start, long end, long thunderFrom, boolean snowstorm) {
        boolean covers(long time) { return time >= start && time < end; }

        boolean thunders(long time) { return thunderFrom >= 0 && time >= thunderFrom && time < end && !snowstorm; }
    }

    /** A heat wave (positive {@code peakF}) or a cold snap, easing in and out around its middle. */
    public record Swing(long start, long end, double peakF) {
        boolean covers(long time) { return time >= start && time < end; }

        /** Its shift at a time: none at its ends, all of it through its middle half. */
        double shiftAt(long time) {
            if (!covers(time)) return 0.0;
            double t = (time - start) / (double) (end - start);
            double ease = t < 0.25 ? smooth(t / 0.25) : t > 0.75 ? smooth((1.0 - t) / 0.25) : 1.0;
            return peakF * ease;
        }

        WeatherEvent event() { return peakF >= 0 ? WeatherEvent.HEAT_WAVE : WeatherEvent.COLD_SNAP; }

        private static double smooth(double t) { return t * t * (3.0 - 2.0 * t); }
    }

    /** The plan: its fronts and swings in order, and how far ahead it reaches. */
    public static final class Plan {
        final List<Front> fronts = new ArrayList<>();
        final List<Swing> swings = new ArrayList<>();
        /** Planned up to here. */
        long until;
        /** How many times it has been planned afresh, so a new plan isn't the old one again. */
        int revision;
        /** The world day it was last looked over for revisions. */
        long revisedDay = Long.MIN_VALUE;

        public List<Front> fronts() { return List.copyOf(fronts); }

        public List<Swing> swings() { return List.copyOf(swings); }

        public Front frontAt(long time) {
            for (Front front : fronts) if (front.covers(time)) return front;
            return null;
        }

        /** The next front starting after {@code time}, or null. */
        public Front nextFront(long time) {
            for (Front front : fronts) if (front.start() > time) return front;
            return null;
        }

        public Swing swingAt(long time) {
            for (Swing swing : swings) if (swing.covers(time)) return swing;
            return null;
        }

        public double shiftAt(long time) {
            Swing swing = swingAt(time);
            return swing == null ? 0.0 : swing.shiftAt(time);
        }

        /** What is going on: a storm first, then a heat wave or cold snap. */
        public Optional<WeatherEvent> eventAt(long time) {
            Front front = frontAt(time);
            if (front != null && front.snowstorm()) return Optional.of(WeatherEvent.SNOWSTORM);
            if (front != null && front.thunders(time)) return Optional.of(WeatherEvent.THUNDERSTORM);
            Swing swing = swingAt(time);
            return swing == null ? Optional.empty() : Optional.of(swing.event());
        }

        /** Drops everything that starts at or after {@code time}, and cuts what runs past it. */
        void cut(long time) {
            fronts.removeIf(front -> front.start() >= time);
            swings.removeIf(swing -> swing.start() >= time);
            until = Math.min(until, time);
        }

        /** Puts a front in now, replacing those it overlaps (an admin's /weather, or an event started by hand). */
        void insert(Front front) {
            fronts.removeIf(other -> other.start() < front.end() && other.end() > front.start());
            fronts.add(front);
            fronts.sort(java.util.Comparator.comparingLong(Front::start));
        }

        void insert(Swing swing) {
            swings.removeIf(other -> other.start() < swing.end() && other.end() > swing.start());
            swings.add(swing);
            swings.sort(java.util.Comparator.comparingLong(Swing::start));
        }

        /** Ends the front going on now (sleeping through a storm, or /weather clear). */
        void endFrontAt(long time) {
            for (int i = 0; i < fronts.size(); i++) {
                Front front = fronts.get(i);
                if (front.covers(time)) fronts.set(i, new Front(front.start(), time,
                        front.thunderFrom() >= time ? -1 : front.thunderFrom(), front.snowstorm()));
            }
            fronts.removeIf(front -> front.end() <= front.start());
        }
    }

    private final WeatherSettings settings;

    public WeatherPlanner(WeatherSettings settings) {
        this.settings = settings;
    }

    /** The start of the Minecraft day a time falls in: days begin at sunrise, as the world counts them. */
    public static long dayStart(long time) {
        return time - Math.floorMod(time, DAY);
    }

    /**
     * Plans ahead to {@link #HORIZON_DAYS} days from now, continuing from
     * where the plan reaches. {@code seasonIn} gives the season that many
     * days from today.
     */
    public void extend(Plan plan, long now, long seed, IntFunction<SeasonView.Season> seasonIn) {
        long today = dayStart(now);
        long horizon = today + HORIZON_DAYS * DAY;
        if (plan.until < now) {
            plan.until = now;
        }
        if (plan.until >= horizon) return;
        Random random = new Random(seed ^ (plan.until * 0x9E3779B97F4A7C15L) ^ ((long) plan.revision << 48));
        // Fronts: continue after the last one with a dry spell.
        long time = plan.until;
        Front last = plan.fronts.isEmpty() ? null : plan.fronts.get(plan.fronts.size() - 1);
        if (last != null && last.end() > time) time = last.end();
        boolean firstAfterNothing = last == null || last.end() < plan.until - DAY;
        while (time < horizon) {
            SeasonView.Season season = seasonIn.apply((int) Math.floorDiv(time - today, DAY));
            WeatherSettings.Season odds = settings.season(season);
            double dryDays = between(random, odds.dryMinDays(), odds.dryMaxDays());
            // A plan started from nothing may begin partway through a dry spell.
            if (firstAfterNothing) {
                dryDays *= random.nextDouble();
                firstAfterNothing = false;
            }
            long start = time + Math.round(dryDays * DAY);
            long length = Math.round(between(random, odds.frontMinHours(), odds.frontMaxHours()) * HOUR);
            SeasonView.Season at = seasonIn.apply((int) Math.floorDiv(start - today, DAY));
            boolean snowstorm = at == SeasonView.Season.WINTER && random.nextDouble() < settings.snowstormChance();
            long thunderFrom = !snowstorm && random.nextDouble() < settings.season(at).thunder()
                    ? start + Math.round(random.nextDouble() * 0.6 * length) : -1L;
            if (start < horizon + DAY) plan.fronts.add(new Front(start, start + length, thunderFrom, snowstorm));
            time = start + length;
        }
        // Heat waves and cold snaps: a chance each day, one at a time.
        Swing lastSwing = plan.swings.isEmpty() ? null : plan.swings.get(plan.swings.size() - 1);
        // From the first day not yet planned: the plan reaches up to, not including, the day {@code until} starts.
        long day = Math.max(dayStart(plan.until), lastSwing == null ? Long.MIN_VALUE : dayStart(lastSwing.end()) + DAY);
        for (; day < horizon; day += DAY) {
            SeasonView.Season season = seasonIn.apply((int) Math.floorDiv(day - today, DAY));
            WeatherSettings.Swing kind = null;
            double roll = random.nextDouble();
            if (roll < settings.heatWave().chance(season)) kind = settings.heatWave();
            else if (roll < settings.heatWave().chance(season) + settings.coldSnap().chance(season)) kind = settings.coldSnap();
            if (kind == null) continue;
            long length = Math.round(between(random, kind.minDays(), kind.maxDays()) * DAY);
            double peak = between(random, kind.minF(), kind.maxF()) * (kind == settings.heatWave() ? 1 : -1);
            long start = day + Math.round(random.nextDouble() * DAY / 2);
            plan.swings.add(new Swing(start, start + length, peak));
            day = dayStart(start + length);
        }
        plan.until = horizon;
    }

    /**
     * Once a day: drops what has passed, and maybe plans a later day afresh.
     * Days within {@code exact-days} of today never change; each one after
     * gets {@code revise-chance} of being planned again, from it onward.
     */
    public void revise(Plan plan, long now, long seed, IntFunction<SeasonView.Season> seasonIn) {
        long today = dayStart(now);
        plan.fronts.removeIf(front -> front.end() < now - DAY);
        plan.swings.removeIf(swing -> swing.end() < now - DAY);
        long worldDay = Math.floorDiv(now, DAY);
        if (plan.revisedDay != worldDay) {
            plan.revisedDay = worldDay;
            Random random = new Random(seed ^ worldDay * 0xC2B2AE3D27D4EB4FL ^ plan.revision);
            for (int d = settings.exactDays(); d < HORIZON_DAYS; d++) {
                if (random.nextDouble() >= settings.reviseChance()) continue;
                plan.revision++;
                plan.cut(today + d * DAY);
                break;
            }
        }
        extend(plan, now, seed, seasonIn);
    }

    /**
     * The plan as a forecast shows it, {@code days} from today: today and the
     * exact days as planned; later days leaning toward the season's usual
     * odds, the further out the more.
     */
    public List<WeatherPlan.Outlook> outlook(Plan plan, long now, int days, IntFunction<SeasonView.Season> seasonIn) {
        List<WeatherPlan.Outlook> outlook = new ArrayList<>(days);
        long tomorrow = dayStart(now) + DAY;
        for (int ahead = 0; ahead < days; ahead++) {
            long from = ahead == 0 ? now : tomorrow + (ahead - 1) * DAY, to = ahead == 0 ? tomorrow : from + DAY;
            SeasonView.Season season = seasonIn.apply(ahead);
            WeatherSettings.Season odds = settings.season(season);
            long wet = 0, storm = 0;
            Front first = null;
            for (Front front : plan.fronts) {
                long overlap = Math.max(0, Math.min(front.end(), to) - Math.max(front.start(), from));
                if (overlap <= 0) continue;
                wet += overlap;
                if (front.thunderFrom() >= 0 && !front.snowstorm())
                    storm += Math.max(0, Math.min(front.end(), to) - Math.max(Math.max(front.thunderFrom(), front.start()), from));
                if (first == null || front.snowstorm() && !first.snowstorm()) first = front;
            }
            boolean rains = wet >= WET_TICKS, thunders = storm >= WET_TICKS;
            double uncertain = ahead < settings.exactDays() ? 0.0
                    : Math.min(1.0, (ahead - settings.exactDays() + 1) * settings.certaintyLossPerDay());
            double usualRain = odds.rainChance(), usualThunder = usualRain * odds.thunder();
            double rain = rains ? 1.0 - uncertain * (1.0 - usualRain) : uncertain * usualRain;
            double thunder = thunders ? 1.0 - uncertain * (1.0 - usualThunder) : uncertain * usualThunder;
            long middle = (from + to) / 2;
            Swing swing = null;
            for (Swing candidate : plan.swings)
                if (candidate.start() < to && candidate.end() > from) swing = candidate;
            double shift = swing == null ? 0.0 : swing.shiftAt(Math.max(swing.start(), Math.min(swing.end() - 1, middle)))
                    * (1.0 - uncertain);
            Optional<WeatherEvent> event = rains && first != null && first.snowstorm() ? Optional.of(WeatherEvent.SNOWSTORM)
                    : thunders ? Optional.of(WeatherEvent.THUNDERSTORM)
                    : swing != null ? Optional.of(swing.event()) : Optional.empty();
            outlook.add(new WeatherPlan.Outlook(ahead, rain, Math.min(rain, thunder), shift, event,
                    note(event, first, swing, from, uncertain)));
        }
        return outlook;
    }

    /** What a forecast says about a day's event: when it starts on a sure day, "likely" on a fairly sure one. */
    static String note(Optional<WeatherEvent> event, Front front, Swing swing, long from, double uncertain) {
        if (event.isEmpty() || uncertain >= 0.5) return "";
        WeatherEvent what = event.get();
        if (uncertain > 0.0) return what.displayName() + " likely";
        if (what == WeatherEvent.SNOWSTORM && front != null && front.start() > from)
            return "Snowstorm from " + clock(front.start());
        if (what == WeatherEvent.THUNDERSTORM && front != null && front.thunderFrom() > from)
            return "Thunder from " + clock(front.thunderFrom());
        if (what == WeatherEvent.HEAT_WAVE || what == WeatherEvent.COLD_SNAP) {
            long day = Math.floorDiv(from - swing.start(), DAY) + 1;
            long of = Math.max(1, Math.round((swing.end() - swing.start()) / (double) DAY));
            return what.displayName() + (day >= 1 && day <= of ? ", day " + day + " of " + of : "");
        }
        return what.displayName();
    }

    /** A time as a clock: sunrise (0) is 06:00, to the hour, as EZvac's forecasts write it. */
    static String clock(long time) {
        long hour = (Math.floorMod(time, DAY) + 6_000L + 500L) / 1_000L % 24L;
        return String.format(Locale.ROOT, "%02d:00", hour);
    }

    private static double between(Random random, double low, double high) {
        return low + random.nextDouble() * Math.max(0.0, high - low);
    }
}
