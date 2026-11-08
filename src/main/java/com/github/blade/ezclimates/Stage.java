package com.github.blade.ezclimates;

import java.util.Locale;

/**
 * The five looks of the year: leaves and grass fresh in spring, as vanilla in
 * summer, yellowing in early autumn, orange-red in late autumn, and dull in
 * winter. Read from EZvac's season progress.
 */
public enum Stage {
    SPRING, SUMMER, AUTUMN_EARLY, AUTUMN_LATE, WINTER;

    /** Its part of a copy's name: {@code autumn_early}. */
    public String key() { return name().toLowerCase(Locale.ROOT); }

    /**
     * The stage at a point of the year, 0 at the start of spring to 1 at the
     * end of winter: spring and summer a quarter each, autumn's two halves an
     * eighth each, winter a quarter.
     */
    public static Stage at(double progress) {
        double p = Double.isFinite(progress) ? progress - Math.floor(progress) : 0.375;
        if (p < 0.25) return SPRING;
        if (p < 0.5) return SUMMER;
        if (p < 0.625) return AUTUMN_EARLY;
        if (p < 0.75) return AUTUMN_LATE;
        return WINTER;
    }
}
