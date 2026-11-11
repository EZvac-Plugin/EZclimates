package com.github.blade.ezclimates.body;

import com.github.blade.hvac.api.EzvacApi;
import com.github.blade.hvac.api.hud.HudContext;
import com.github.blade.hvac.api.hud.HudSection;
import com.github.blade.ezclimates.body.model.BodyMeter;
import com.github.blade.ezclimates.body.model.ExposureSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * The body temperature on EZvac's climate HUD: a reading and a meter under
 * it, drawn with EZvac's own bar so it matches the feels-like one.
 */
final class BodyHud implements HudSection {
    static final String KEY = "body";
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final BodyTemperature plugin;

    private BodyHud(BodyTemperature plugin) {
        this.plugin = plugin;
    }

    /** True when this EZvac's API has HUD sections: 1.1 or newer. */
    static boolean supported(EzvacApi ezvac) {
        String[] parts = ezvac.apiVersion().split("\\.");
        try {
            return parts.length >= 2 && Integer.parseInt(parts[0]) == 1 && Integer.parseInt(parts[1]) >= 1;
        } catch (NumberFormatException unknown) {
            return false;
        }
    }

    /** Adds the section to EZvac's HUD where it can; returns its id, or null. */
    static String register(BodyTemperature plugin) {
        if (!supported(plugin.ezvac())) return null;
        return plugin.ezvac().hud().register(plugin.plugin(), KEY, new BodyHud(plugin));
    }

    @Override
    public List<Component> lines(HudContext context) {
        ExposureSettings.Hud hud = plugin.settings().hud();
        Player player = context.player();
        if (!hud.enabled() || !plugin.affected(player)) return List.of();
        PlayerState state = plugin.state(player);
        String status = status(state);
        // A line that only holds the status goes while there is nothing to say.
        return hud.lines().stream()
                .filter(line -> !(status.isEmpty() && line.contains("{status}") && blank(line.replace("{status}", ""))))
                .map(line -> MINI.deserialize(fill(line, context, state, hud))).toList();
    }

    /** A colour by level: {@code low} below {@code mid}, {@code middle} below {@code high}, then {@code top}. */
    static String shade(double value, double mid, double high, String low, String middle, String top) {
        return value >= high ? top : value >= mid ? middle : low;
    }

    /** True for a line of nothing but tags and spaces. */
    private static boolean blank(String line) {
        return MINI.stripTags(line).isBlank();
    }

    /**
     * What is happening to the body besides its temperature (EZclimates
     * 0.2.0), a few short words in colour: a fever, wet, by a fire, a warm
     * meal or a cool one, hot armor, working hard. The first three that apply.
     */
    String status(PlayerState state) {
        java.util.List<String> tags = new java.util.ArrayList<>();
        // Each icon deepens in colour with its level.
        if (state.illness != null && plugin.sicknessOn())
            tags.add("<" + switch (state.illness.severity()) {
                case MILD -> "#ffd966";
                case MODERATE -> "#ffa94d";
                case SEVERE -> "#ff5c5c";
            } + ">✚");
        if (state.wetness >= 0.05) tags.add("<" + shade(state.wetness, 0.35, 0.75, "#a8d4ff", "#6aa8ff", "#3d7bff")
                + ">☂" + Math.round(state.wetness * 100) + "%");
        if (state.fireF >= 1.0) tags.add("<" + shade(state.fireF, 20, 45, "#ffcc80", "#ff9d4d", "#ff6a2b")
                + ">♨+" + Math.round(plugin.degreesAmount(state.fireF)) + "°");
        long mealLeft = state.mealUntil - org.bukkit.Bukkit.getCurrentTick();
        if (mealLeft > 0 && plugin.surroundings().food().enabled()) {
            boolean fading = mealLeft < 1200;
            String color = state.mealF > 0 ? (fading ? "#ffd9a0" : "#ffb347") : (fading ? "#cdeeff" : "#8fd3ff");
            tags.add("<" + color + ">" + (state.mealF > 0 ? "☕" : "❄") + Math.max(1, Math.round(mealLeft / 1200.0)) + "m");
        }
        if (state.hungerFactor >= 1.05)
            tags.add("<" + (state.hungry ? "#ff5c5c" : shade(state.hungerFactor, 1.3, 1.6, "#f2d16b", "#ffa94d", "#ff5c5c"))
                    + ">⚡" + String.format(java.util.Locale.ROOT, "%.1f", state.hungerFactor) + "×");
        else if (state.hungry) tags.add("<#ff5c5c>⚡");
        if (state.armorF >= 1.0) tags.add("<" + shade(state.armorF, 5.1, 9.9, "#ffe08a", "#ffb84d", "#ff8a3d")
                + ">☀+" + Math.round(plugin.degreesAmount(state.armorF)) + "°");
        if (state.exertion >= 0.15) tags.add("<" + shade(state.exertion, 0.5, 0.85, "#cfcfcf", "#ffd27a", "#ffa94d")
                + ">⚒+" + Math.round(plugin.degreesAmount(state.exertion * plugin.surroundings().exertion().mostF())) + "°");
        // Icons and numbers, three at most, so the sidebar keeps its width.
        return String.join(" ", tags.subList(0, Math.min(3, tags.size())));
    }

    @Override
    public Component inline(HudContext context) {
        ExposureSettings.Hud hud = plugin.settings().hud();
        Player player = context.player();
        if (!hud.enabled() || !plugin.affected(player)) return Component.empty();
        return MINI.deserialize(fill(hud.inline(), context, plugin.state(player), hud));
    }

    /** EZvac's HudContext.gauge (API 1.11), looked up once: a meter exactly like the feels-like gauge. */
    private static final java.lang.reflect.Method GAUGE = gaugeMethod();

    private static java.lang.reflect.Method gaugeMethod() {
        try {
            return HudContext.class.getMethod("gauge", double.class, java.util.function.DoubleFunction.class);
        } catch (NoSuchMethodException older) {
            return null;
        }
    }

    /**
     * The body meter in the HUD's own style: EZvac's gauge, each segment in
     * the colour of the stage it stands for from cold to hot and the marker
     * at the body's temperature, where EZvac has it (API 1.11); else its bar
     * filled in the stage's colour.
     */
    private Component meter(HudContext context, PlayerState state, ExposureSettings.Hud hud) {
        double fraction = BodyMeter.fraction(state.bodyF, hud);
        ExposureSettings.Thresholds thresholds = plugin.settings().thresholds();
        if (GAUGE != null) {
            java.util.function.DoubleFunction<TextColor> colorAt =
                    at -> TextColor.fromHexString(BodyMeter.colorAlong(at, thresholds, hud));
            try {
                return (Component) GAUGE.invoke(context, fraction, colorAt);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failed) {
                // Fall back to the bar below.
            }
        }
        return context.bar(fraction, TextColor.fromHexString(BodyMeter.color(state.stage, hud)));
    }

    /** " · Wet" in blue while damp or wetter (EZclimates 0.2.0), else nothing. */
    private static String wet(PlayerState state) {
        String word = com.github.blade.ezclimates.body.model.Surroundings.wetWord(state.wetness);
        return word.isEmpty() ? "" : " <gray>· <#6aa8ff>" + word;
    }

    private String fill(String template, HudContext context, PlayerState state, ExposureSettings.Hud hud) {
        String color = BodyMeter.color(state.stage, hud);
        String meter = template.contains("{meter}") ? MINI.serialize(meter(context, state, hud)) : "";
        boolean feverish = state.illness != null && plugin.sicknessOn();
        return template
                .replace("{body}", MINI.escapeTags(context.temperature(state.bodyF)))
                .replace("{stage}", "<color:" + color + ">" + MINI.escapeTags(state.stage.displayName()) + "</color>")
                .replace("{fever}", feverish ? hud.feverText() : "")
                .replace("{wet}", wet(state))
                .replace("{status}", template.contains("{status}") ? status(state) : "")
                .replace("{meter}", meter);
    }
}
