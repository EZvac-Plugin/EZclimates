package com.github.blade.ezclimates.body.model;

/**
 * How cold or hot a body is, from its temperature. Ordered coldest first, so
 * {@link #ordinal()} comparisons read as "colder" and "hotter".
 */
public enum Stage {
    FREEZING("Freezing", true),
    SEVERE_HYPOTHERMIA("Severe hypothermia", true),
    HYPOTHERMIA("Hypothermia", true),
    CHILLED("Chilled", true),
    NORMAL("Normal", false),
    OVERHEATING("Overheating", false),
    HEAT_EXHAUSTION("Heat exhaustion", false),
    SEVERE_HEAT("Severe heat", false),
    HEATSTROKE("Heatstroke", false);

    private final String displayName;
    private final boolean cold;

    Stage(String displayName, boolean cold) {
        this.displayName = displayName;
        this.cold = cold;
    }

    public String displayName() { return displayName; }

    public boolean cold() { return cold; }

    public boolean hot() { return ordinal() > NORMAL.ordinal(); }

    /** How far from normal: 0 for normal, up to 4 at freezing or heatstroke. */
    public int severity() { return Math.abs(ordinal() - NORMAL.ordinal()); }

    /** True when this stage is further from normal than {@code other}. */
    public boolean worseThan(Stage other) {
        return severity() > other.severity();
    }

    public static Stage of(double bodyF, ExposureSettings.Thresholds t) {
        if (bodyF < t.freezingF()) return FREEZING;
        if (bodyF < t.severeHypothermiaF()) return SEVERE_HYPOTHERMIA;
        if (bodyF < t.hypothermiaF()) return HYPOTHERMIA;
        if (bodyF < t.chilledF()) return CHILLED;
        if (bodyF > t.heatstrokeF()) return HEATSTROKE;
        if (bodyF > t.severeHeatF()) return SEVERE_HEAT;
        if (bodyF > t.heatExhaustionF()) return HEAT_EXHAUSTION;
        if (bodyF > t.overheatingF()) return OVERHEATING;
        return NORMAL;
    }
}
