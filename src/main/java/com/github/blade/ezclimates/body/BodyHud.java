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
        return hud.lines().stream().map(line -> MINI.deserialize(fill(line, context, state, hud))).toList();
    }

    @Override
    public Component inline(HudContext context) {
        ExposureSettings.Hud hud = plugin.settings().hud();
        Player player = context.player();
        if (!hud.enabled() || !plugin.affected(player)) return Component.empty();
        return MINI.deserialize(fill(hud.inline(), context, plugin.state(player), hud));
    }

    private String fill(String template, HudContext context, PlayerState state, ExposureSettings.Hud hud) {
        String color = BodyMeter.color(state.stage, hud);
        String meter = template.contains("{meter}")
                ? MINI.serialize(context.bar(BodyMeter.fraction(state.bodyF, hud), TextColor.fromHexString(color)))
                : "";
        boolean feverish = state.illness != null && plugin.sicknessOn();
        return template
                .replace("{body}", MINI.escapeTags(context.temperature(state.bodyF)))
                .replace("{stage}", "<color:" + color + ">" + MINI.escapeTags(state.stage.displayName()) + "</color>")
                .replace("{fever}", feverish ? hud.feverText() : "")
                .replace("{meter}", meter);
    }
}
