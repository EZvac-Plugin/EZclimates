package com.github.blade.ezclimates.body.model;

import com.github.blade.hvac.api.SeasonView;

import java.util.Locale;
import java.util.random.RandomGenerator;

/**
 * The optional sickness system's arithmetic: the daily chance, how bad an
 * illness is and how long it lasts, and how water shortens it. Pure; the
 * random source is passed in so tests can fix it.
 */
public final class SicknessModel {
    private SicknessModel() {}

    public enum Severity {
        MILD, MODERATE, SEVERE;

        public String displayName() { return name().toLowerCase(Locale.ROOT); }

        public static Severity parse(String text) {
            if (text == null) return null;
            return switch (text.trim().toLowerCase(Locale.ROOT)) {
                case "mild" -> MILD;
                case "moderate" -> MODERATE;
                case "severe" -> SEVERE;
                default -> null;
            };
        }
    }

    /**
     * An illness in progress.
     *
     * @param daysLeft    calendar days until it's over; it ends when this reaches 0
     * @param waterSaved  days water has taken off it so far
     */
    public record Illness(Severity severity, double daysLeft, double waterSaved) {}

    /**
     * Tomorrow's chance of falling ill, from the day just ended.
     *
     * @param season     the season it was, or null where there are none
     * @param coldShare  share of the day spent cold, 0 to 1
     * @param contact    true when the player spent enough time near someone sick
     */
    public static double chance(SeasonView.Season season, double coldShare, boolean contact,
                                ExposureSettings.Sickness settings) {
        double base = settings.chances()[season == null ? 4 : season.ordinal()];
        double share = Double.isFinite(coldShare) ? Math.max(0.0, Math.min(1.0, coldShare)) : 0.0;
        double chance = base * (1.0 + (settings.coldMultiplier() - 1.0) * share);
        if (contact) chance += settings.contagionBonus();
        return Math.min(settings.maximumChance(), chance);
    }

    public static boolean falls(double chance, RandomGenerator random) {
        return random.nextDouble() < chance;
    }

    public static Severity severity(RandomGenerator random, ExposureSettings.Sickness settings) {
        double[] weights = settings.severityWeights();
        double pick = random.nextDouble() * (weights[0] + weights[1] + weights[2]);
        if (pick < weights[0]) return Severity.MILD;
        if (pick < weights[0] + weights[1]) return Severity.MODERATE;
        return Severity.SEVERE;
    }

    /** A new illness: its severity rolled or given, and 2 to 3 days by default. */
    public static Illness begin(Severity severity, RandomGenerator random, ExposureSettings.Sickness settings) {
        int days = settings.minimumDays() + random.nextInt(settings.maximumDays() - settings.minimumDays() + 1);
        return new Illness(severity, days, 0.0);
    }

    /** A new day: one fewer left. */
    public static Illness dayPassed(Illness illness) {
        return new Illness(illness.severity(), Math.max(0.0, illness.daysLeft() - 1.0), illness.waterSaved());
    }

    /** A water bottle: a little shorter, until water has taken off all it may. */
    public static Illness drank(Illness illness, ExposureSettings.Sickness settings) {
        double room = Math.max(0.0, settings.waterMaximumDays() - illness.waterSaved());
        double saved = Math.min(room, settings.waterDaysPerBottle());
        return new Illness(illness.severity(), Math.max(0.0, illness.daysLeft() - saved), illness.waterSaved() + saved);
    }

    public static boolean over(Illness illness) {
        return illness.daysLeft() <= 1e-9;
    }

    public static double feverF(Severity severity, ExposureSettings.Sickness settings) {
        return settings.feverF()[severity.ordinal()];
    }

    public static double heatMultiplier(Severity severity, ExposureSettings.Sickness settings) {
        return settings.heatMultipliers()[severity.ordinal()];
    }

    public static double nauseaEveryMinutes(Severity severity, ExposureSettings.Sickness settings) {
        return settings.nauseaMinutes()[severity.ordinal()];
    }
}
