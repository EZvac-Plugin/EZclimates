package com.github.blade.ezclimates.body;

import com.github.blade.ezclimates.body.model.BodyModel;
import com.github.blade.ezclimates.body.model.SicknessModel;
import com.github.blade.ezclimates.body.model.Stage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * {@code /bodytemp} for everyone, and {@code /ezclimates body} for admins
 * ({@code /liveexposure} before it became part of EZclimates): reload, set a
 * body temperature, make someone sick, cure them, or roll now.
 */
final class Commands {
    private final BodyTemperature plugin;

    Commands(BodyTemperature plugin) {
        this.plugin = plugin;
    }

    /** {@code /ezclimates body ...}: {@code args} are what follows {@code body}. */
    boolean admin(CommandSender sender, String[] args) {
        if (args.length == 0) {
            usage(sender);
            return true;
        }
        try {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "reload" -> {
                    plugin.loadSettings();
                    sender.sendMessage(Component.text("Body temperature settings reloaded (sickness "
                            + (plugin.sicknessOn() ? "on" : "off") + ").", NamedTextColor.GREEN));
                }
                case "set" -> {
                    Player target = player(args, 1);
                    if (args.length < 3) throw new IllegalArgumentException("Usage: /ezclimates body set <player> <°F>");
                    double value;
                    try {
                        value = Double.parseDouble(args[2]);
                    } catch (NumberFormatException notNumber) {
                        throw new IllegalArgumentException("'" + args[2] + "' is not a temperature in °F.");
                    }
                    PlayerState state = plugin.state(target);
                    state.bodyF = BodyModel.clamp(value);
                    state.stage = Stage.of(state.bodyF, plugin.settings().thresholds());
                    sender.sendMessage(Component.text(target.getName() + "'s body is now "
                            + plugin.degrees(state.bodyF) + ".", NamedTextColor.GREEN));
                }
                case "sick" -> {
                    requireSickness();
                    Player target = player(args, 1);
                    SicknessModel.Severity severity = args.length < 3 ? null : SicknessModel.Severity.parse(args[2]);
                    if (severity == null)
                        throw new IllegalArgumentException("Usage: /ezclimates body sick <player> <mild|moderate|severe>");
                    plugin.sickness().fallIll(target, plugin.state(target), severity);
                    sender.sendMessage(Component.text(target.getName() + " now has a " + severity.displayName()
                            + " fever for " + days(plugin.state(target).illness.daysLeft()) + ".", NamedTextColor.GREEN));
                }
                case "cure" -> {
                    requireSickness();
                    Player target = player(args, 1);
                    PlayerState state = plugin.state(target);
                    if (state.illness == null) {
                        sender.sendMessage(Component.text(target.getName() + " isn't sick.", NamedTextColor.YELLOW));
                        return true;
                    }
                    plugin.sickness().recover(target, state);
                    sender.sendMessage(Component.text(target.getName() + " is cured.", NamedTextColor.GREEN));
                }
                case "roll" -> {
                    requireSickness();
                    Player target = player(args, 1);
                    PlayerState state = plugin.state(target);
                    if (state.illness != null) {
                        sender.sendMessage(Component.text(target.getName() + " is already sick.", NamedTextColor.YELLOW));
                        return true;
                    }
                    double chance = plugin.sickness().roll(target, state, plugin.sickness().seasonNow(target.getWorld()), true);
                    sender.sendMessage(Component.text(String.format(Locale.US, "Rolled %.1f%% for %s: %s.", chance * 100,
                            target.getName(), state.illness == null ? "still healthy"
                                    : "fell ill (" + state.illness.severity().displayName() + ")"), NamedTextColor.GREEN));
                }
                default -> usage(sender);
            }
        } catch (IllegalArgumentException invalid) {
            sender.sendMessage(Component.text(invalid.getMessage(), NamedTextColor.RED));
        }
        return true;
    }

    boolean bodyTemp(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players have a body temperature.", NamedTextColor.RED));
            return true;
        }
        PlayerState state = plugin.state(player);
        Stage stage = Stage.of(state.bodyF, plugin.settings().thresholds());
        NamedTextColor color = stage.cold() ? NamedTextColor.AQUA : stage.hot() ? NamedTextColor.GOLD : NamedTextColor.GREEN;
        player.sendMessage(Component.text("Body temperature: ", NamedTextColor.GRAY)
                .append(Component.text(plugin.degrees(state.bodyF) + " (" + stage.displayName() + ")", color)));
        if (plugin.sicknessOn() && state.illness != null)
            player.sendMessage(Component.text("Sick: " + state.illness.severity().displayName() + " fever, about "
                    + days(state.illness.daysLeft()) + " left. Water helps.", NamedTextColor.YELLOW));
        if (!plugin.affected(player)) {
            player.sendMessage(Component.text("Heat and cold don't affect you here.", NamedTextColor.GRAY));
            return true;
        }
        // What is warming or chilling it besides the air (EZclimates 0.2.0).
        java.util.List<String> factors = new java.util.ArrayList<>();
        String wet = com.github.blade.ezclimates.body.model.Surroundings.wetWord(state.wetness);
        if (!wet.isEmpty()) factors.add(wet + ": the cold bites harder until you dry off");
        if (state.fireF >= 1.0) factors.add("A fire nearby: " + plugin.degreesDelta(state.fireF) + " warmer");
        if (state.armorF >= 1.0) factors.add("Metal armor in the sun: " + plugin.degreesDelta(state.armorF) + " warmer");
        if (state.exertion >= 0.15) factors.add("Working hard: up to " + plugin.degreesDelta(
                state.exertion * plugin.surroundings().exertion().mostF()) + " warmer");
        long left = state.mealUntil - org.bukkit.Bukkit.getCurrentTick();
        if (left > 0 && plugin.surroundings().food().enabled())
            factors.add((state.mealF > 0 ? "Warmed by " : "Cooled by ") + state.mealName + ": "
                    + plugin.degreesDelta(Math.abs(state.mealF)) + " for " + Math.max(1, Math.round(left / 1200.0))
                    + " more minute(s)");
        for (String factor : factors) player.sendMessage(Component.text("  " + factor, NamedTextColor.GRAY));
        return true;
    }

    private void requireSickness() {
        if (!plugin.sicknessOn())
            throw new IllegalArgumentException("Sickness is switched off (sickness.enabled in body.yml).");
    }

    private Player player(String[] args, int index) {
        if (args.length <= index) throw new IllegalArgumentException("Name a player.");
        Player player = plugin.getServer().getPlayerExact(args[index]);
        if (player == null) throw new IllegalArgumentException("'" + args[index] + "' isn't online.");
        return player;
    }

    private static String days(double days) {
        return String.format(Locale.US, days == Math.floor(days) ? "%.0f day%s" : "%.2f day%s", days, days == 1.0 ? "" : "s");
    }

    private static void usage(CommandSender sender) {
        sender.sendMessage(Component.text("/ezclimates body reload | set <player> <°F> | sick <player> <mild|moderate|severe>"
                + " | cure <player> | roll <player>", NamedTextColor.GRAY));
    }

    /** What can follow {@code /ezclimates body}. */
    List<String> complete(String[] args) {
        if (args.length == 1) return filter(args[0], "reload", "set", "sick", "cure", "roll");
        if (args.length == 2 && !args[0].equalsIgnoreCase("reload"))
            return filter(args[1], plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toArray(String[]::new));
        if (args.length == 3 && args[0].equalsIgnoreCase("sick")) return filter(args[2], "mild", "moderate", "severe");
        return List.of();
    }

    private static List<String> filter(String prefix, String... options) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return Arrays.stream(options).filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
