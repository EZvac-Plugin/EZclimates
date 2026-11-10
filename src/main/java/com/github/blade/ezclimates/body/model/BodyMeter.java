package com.github.blade.ezclimates.body.model;

/** The body-temperature meter on EZvac's HUD: how full it is and its colour. Pure. */
public final class BodyMeter {
    private BodyMeter() {}

    /** 0 at the meter's cold end, 1 at its hot end, clamped. */
    public static double fraction(double bodyF, ExposureSettings.Hud hud) {
        double value = (bodyF - hud.meterColdF()) / (hud.meterHotF() - hud.meterColdF());
        if (Double.isNaN(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }

    /** The stage's colour: normal, chilled, colder, overheating, or hotter. */
    public static String color(Stage stage, ExposureSettings.Hud hud) {
        return switch (stage) {
            case NORMAL -> hud.normalColor();
            case CHILLED -> hud.chilledColor();
            case HYPOTHERMIA, SEVERE_HYPOTHERMIA, FREEZING -> hud.coldColor();
            case OVERHEATING -> hud.warmColor();
            case HEAT_EXHAUSTION, SEVERE_HEAT, HEATSTROKE -> hud.hotColor();
        };
    }
}
