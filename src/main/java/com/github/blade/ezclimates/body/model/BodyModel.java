package com.github.blade.ezclimates.body.model;

/**
 * A player's body temperature, one step at a time. Every climate has a
 * balance point the body heads for and settles at: its resting point (98.6 F,
 * higher with a fever) inside the comfort band, warmer the hotter it feels
 * above it, colder the colder below. Pure.
 */
public final class BodyModel {
    private BodyModel() {}

    /**
     * What the body is exposed to for one step.
     *
     * @param feelsLikeF       EZvac's feels-like, or the water's temperature in water
     * @param inWater          water moves body heat faster
     * @param coldResistanceF  leather worn: counts only against cold
     * @param fireResistant    Fire Resistance stops any heating
     * @param restF            where the body settles when comfortable (a fever raises it)
     * @param heatMultiplier   how much faster heat warms the body (a fever raises it)
     */
    public record Conditions(double feelsLikeF, boolean inWater, double coldResistanceF, boolean fireResistant,
                             double restF, double heatMultiplier) {
        public static Conditions healthy(double feelsLikeF, boolean inWater, double coldResistanceF,
                                         boolean fireResistant) {
            return new Conditions(feelsLikeF, inWater, coldResistanceF, fireResistant,
                    ExposureSettings.Thresholds.REST_F, 1.0);
        }
    }

    /** The temperature that counts against the comfort band: leather only helps against cold, up to comfortable. */
    public static double effectiveF(Conditions conditions, ExposureSettings.Comfort comfort) {
        double feels = conditions.feelsLikeF();
        if (feels >= comfort.lowF()) return feels;
        return Math.min(comfort.lowF(), feels + Math.max(0.0, conditions.coldResistanceF()));
    }

    /** True when the body is losing heat to the cold, before leather: what counts as "a cold day" for sickness. */
    public static boolean exposedToCold(double feelsLikeF, ExposureSettings.Comfort comfort) {
        return feelsLikeF < comfort.lowF();
    }

    /**
     * Where the body settles in these conditions: its resting point, moved up by
     * every degree it feels above comfort (more so with a fever) and down by
     * every degree below. Fire Resistance keeps the heat out.
     */
    public static double balanceF(Conditions conditions, ExposureSettings settings) {
        ExposureSettings.Comfort comfort = settings.comfort();
        double effective = effectiveF(conditions, comfort);
        double hot = conditions.fireResistant() ? 0.0 : Math.max(0.0, effective - comfort.highF());
        double cold = Math.max(0.0, comfort.lowF() - effective);
        return clamp(conditions.restF() + hot * settings.balance().heatFPerF() * conditions.heatMultiplier()
                - cold * settings.balance().coldFPerF());
    }

    /**
     * The body temperature after {@code seconds} in these conditions. It heads
     * for its balance point and stops there: driven by the heat or the cold at
     * the configured rates (faster in water), or at the recovery rate when it
     * is returning from further out.
     */
    public static double step(double bodyF, Conditions conditions, double seconds, ExposureSettings settings) {
        if (!Double.isFinite(bodyF)) bodyF = ExposureSettings.Thresholds.REST_F;
        if (!(seconds > 0)) return clamp(bodyF);
        ExposureSettings.Comfort comfort = settings.comfort();
        ExposureSettings.Rates rates = settings.rates();
        double effective = effectiveF(conditions, comfort);
        double hot = conditions.fireResistant() ? 0.0 : Math.max(0.0, effective - comfort.highF());
        double cold = Math.max(0.0, comfort.lowF() - effective);
        double water = conditions.inWater() ? rates.waterFactor() : 1.0;
        double balance = balanceF(conditions, settings);
        double minutes = seconds / 60.0;
        if (bodyF < balance) {
            double perMinute = hot > 0 ? rates.heatFPerMinute() * conditions.heatMultiplier() * hot * water
                    : rates.recoveryFPerMinute();
            return clamp(Math.min(balance, bodyF + perMinute * minutes));
        }
        if (bodyF > balance) {
            double perMinute = cold > 0 ? rates.coldFPerMinute() * cold * water : rates.recoveryFPerMinute();
            return clamp(Math.max(balance, bodyF - perMinute * minutes));
        }
        return clamp(bodyF);
    }

    public static double clamp(double bodyF) {
        return Math.max(ExposureSettings.Thresholds.MINIMUM_F, Math.min(ExposureSettings.Thresholds.MAXIMUM_F, bodyF));
    }
}
