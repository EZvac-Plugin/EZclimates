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
     * @param extras           what it meets besides the air: wetness, fires, food, armor, work
     */
    public record Conditions(double feelsLikeF, boolean inWater, double coldResistanceF, boolean fireResistant,
                             double restF, double heatMultiplier, Extras extras) {
        public Conditions(double feelsLikeF, boolean inWater, double coldResistanceF, boolean fireResistant,
                          double restF, double heatMultiplier) {
            this(feelsLikeF, inWater, coldResistanceF, fireResistant, restF, heatMultiplier, Extras.NONE);
        }

        public Conditions {
            if (extras == null) extras = Extras.NONE;
        }

        public static Conditions healthy(double feelsLikeF, boolean inWater, double coldResistanceF,
                                         boolean fireResistant) {
            return new Conditions(feelsLikeF, inWater, coldResistanceF, fireResistant,
                    ExposureSettings.Thresholds.REST_F, 1.0);
        }
    }

    /**
     * What a body meets besides the air, in degrees (EZclimates 0.2.0).
     *
     * @param aroundF       warmth from work: warmer either way
     * @param heatOnlyF     warmth that only adds to heat: metal armor in the sun
     * @param wetColderF    how much colder the cold feels for being wet
     * @param wetMilderF    how much milder the heat feels for being wet
     * @param coldRateFactor how much faster the cold takes heat away (1 dry)
     * @param warmthF       held against the cold, like leather: a warm meal
     * @param coolingF      held against the heat: a cool drink or fruit
     * @param rewarmFactor  how much faster a chilled body warms back up: by a fire, after a warm meal (1 none)
     * @param recoolFactor  how much faster an overheated one cools back down: in water, after a cool drink
     * @param fireF         warmth from fires near it: up to comfortable against the cold, a quarter of it in the heat
     */
    public record Extras(double aroundF, double heatOnlyF, double wetColderF, double wetMilderF, double coldRateFactor,
                         double warmthF, double coolingF, double rewarmFactor, double recoolFactor, double fireF) {
        public static final Extras NONE = new Extras(0, 0, 0, 0, 1, 0, 0, 1, 1, 0);

        public Extras(double aroundF, double heatOnlyF, double wetColderF, double wetMilderF, double coldRateFactor,
                      double warmthF, double coolingF) {
            this(aroundF, heatOnlyF, wetColderF, wetMilderF, coldRateFactor, warmthF, coolingF, 1, 1, 0);
        }
    }

    /**
     * Air this warm by a fire this strong: against the cold the fire warms
     * it fully up to comfortable (the middle of the comfort band), and past
     * that, as in the heat, by a quarter of its warmth, so a fire is a
     * refuge in a blizzard and only a nuisance in a desert.
     */
    public static double byFireF(double feelsF, double fireF, ExposureSettings.Comfort comfort) {
        if (!(fireF > 0.0)) return feelsF;
        double comfortable = (comfort.lowF() + comfort.highF()) / 2.0;
        if (feelsF >= comfortable) return feelsF + fireF / 4.0;
        double warmed = feelsF + fireF;
        return warmed <= comfortable ? warmed : comfortable + (warmed - comfortable) / 4.0;
    }

    /**
     * What the weather and the surroundings make it feel like, before
     * anything worn or eaten: the air, fires and work, metal armor's heat,
     * and wetness. What counts as "a cold day" for sickness.
     */
    public static double surroundingsF(Conditions conditions, ExposureSettings.Comfort comfort) {
        Extras extras = conditions.extras();
        double feels = byFireF(conditions.feelsLikeF() + extras.aroundF(), extras.fireF(), comfort);
        if (feels >= comfort.lowF()) feels += extras.heatOnlyF();
        if (conditions.inWater()) return feels;   // water's own pull is the water factor
        if (feels < comfort.lowF()) return feels - extras.wetColderF();
        if (feels > comfort.highF()) return Math.max(comfort.highF(), feels - extras.wetMilderF());
        return feels;
    }

    /**
     * The temperature that counts against the comfort band: the surroundings,
     * with leather and a warm meal against the cold (up to comfortable) and a
     * cool one against the heat (down to comfortable).
     */
    public static double effectiveF(Conditions conditions, ExposureSettings.Comfort comfort) {
        double feels = surroundingsF(conditions, comfort);
        Extras extras = conditions.extras();
        if (feels < comfort.lowF())
            return Math.min(comfort.lowF(), feels + Math.max(0.0, conditions.coldResistanceF()) + extras.warmthF());
        if (feels > comfort.highF()) return Math.max(comfort.highF(), feels - extras.coolingF());
        return feels;
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
                    : rates.recoveryFPerMinute() * Math.max(1.0, conditions.extras().rewarmFactor());
            return clamp(Math.min(balance, bodyF + perMinute * minutes));
        }
        if (bodyF > balance) {
            double perMinute = cold > 0 ? rates.coldFPerMinute() * cold * water * conditions.extras().coldRateFactor()
                    : rates.recoveryFPerMinute() * Math.max(1.0, conditions.extras().recoolFactor());
            return clamp(Math.max(balance, bodyF - perMinute * minutes));
        }
        return clamp(bodyF);
    }

    public static double clamp(double bodyF) {
        return Math.max(ExposureSettings.Thresholds.MINIMUM_F, Math.min(ExposureSettings.Thresholds.MAXIMUM_F, bodyF));
    }
}
