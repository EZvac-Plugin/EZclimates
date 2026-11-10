package com.github.blade.ezclimates.body;

import com.github.blade.hvac.api.SeasonView;
import com.github.blade.ezclimates.body.model.DayClock;
import com.github.blade.ezclimates.body.model.ExposureSettings;
import com.github.blade.ezclimates.body.model.SicknessModel;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The optional sickness system: watches each world's day (EZvac's calendar,
 * or the world's own day count without seasons, and a day of game time where
 * the clock stands still), rolls every player once a day, counts time near
 * sick players, and lets water help. Does nothing while
 * {@code sickness.enabled} is false. Main thread only.
 */
final class SicknessService {
    /** How often days and contagion are checked. */
    private static final long PERIOD_TICKS = 100L;
    private static final double PERIOD_SECONDS = PERIOD_TICKS / 20.0;

    /** Which day a world was on, and its season then. */
    private record Day(long key, SeasonView.Season season) {}

    private final BodyTemperature plugin;
    private final Map<UUID, Day> days = new HashMap<>();
    private final Map<UUID, DayClock> clocks = new HashMap<>();
    private BukkitTask task;

    SicknessService(BodyTemperature plugin) {
        this.plugin = plugin;
    }

    void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin.plugin(), this::tick, PERIOD_TICKS, PERIOD_TICKS);
    }

    void stop() {
        if (task != null) task.cancel();
    }

    private void tick() {
        if (!plugin.sicknessOn()) return;
        for (World world : plugin.getServer().getWorlds()) {
            Day today = today(world);
            Day before = days.put(world.getUID(), today);
            DayClock clock = clocks.computeIfAbsent(world.getUID(), ignored -> new DayClock());
            if (clock.turned(today.key(), world.getGameTime()))
                newDay(world, before != null ? before.season() : today.season());
        }
        spread();
    }

    private Day today(World world) {
        Optional<SeasonView> season = plugin.ezvac().season(world);
        if (season.isPresent()) {
            SeasonView view = season.get();
            return new Day((long) view.season().ordinal() * view.daysPerSeason() + view.dayOfSeason(), view.season());
        }
        return new Day(Math.floorDiv(world.getFullTime(), 24_000L), null);
    }

    /** Players spending time near a sick player count it toward catching it. */
    private void spread() {
        ExposureSettings.Sickness settings = plugin.settings().sickness();
        List<Player> sick = new ArrayList<>();
        for (Player player : plugin.getServer().getOnlinePlayers())
            if (plugin.affected(player) && plugin.state(player).illness != null) sick.add(player);
        if (sick.isEmpty()) return;
        double range = settings.contagionRange();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            PlayerState state = plugin.state(player);
            if (state.illness != null || !plugin.affected(player)) continue;
            for (Player carrier : sick) {
                if (carrier.getWorld().equals(player.getWorld())
                        && carrier.getLocation().distanceSquared(player.getLocation()) <= range * range) {
                    state.contactSeconds += PERIOD_SECONDS;
                    break;
                }
            }
        }
    }

    /** A world's day turned: illnesses move on a day, and everyone else rolls for the day that ended. */
    private void newDay(World world, SeasonView.Season season) {
        for (Player player : world.getPlayers()) {
            PlayerState state = plugin.state(player);
            if (!plugin.affected(player)) {
                state.resetDay();
                continue;
            }
            if (state.illness != null) {
                state.illness = SicknessModel.dayPassed(state.illness);
                if (SicknessModel.over(state.illness)) recover(player, state);
            } else {
                roll(player, state, season, false);
            }
            state.resetDay();
        }
    }

    /**
     * Rolls today's chance for a player.
     *
     * @return the chance that was rolled
     */
    double roll(Player player, PlayerState state, SeasonView.Season season, boolean announceChance) {
        ExposureSettings.Sickness settings = plugin.settings().sickness();
        double coldShare = state.daySeconds > 0 ? state.coldSeconds / state.daySeconds : 0.0;
        boolean contact = state.contactSeconds >= settings.contagionSeconds();
        double chance = SicknessModel.chance(season, coldShare, contact, settings);
        if (SicknessModel.falls(chance, plugin.random())) {
            fallIll(player, state, SicknessModel.severity(plugin.random(), settings));
        }
        if (announceChance || plugin.getConfig().getBoolean("sickness.log-rolls", false))
            plugin.getLogger().info(String.format(Locale.US, "%s: sickness chance %.1f%% (cold %.0f%% of the day%s)%s",
                    player.getName(), chance * 100, coldShare * 100, contact ? ", near someone sick" : "",
                    state.illness != null ? ": fell ill" : ""));
        return chance;
    }

    void fallIll(Player player, PlayerState state, SicknessModel.Severity severity) {
        state.illness = SicknessModel.begin(severity, plugin.random(), plugin.settings().sickness());
        state.nauseaTimer = Double.NaN;
        player.sendMessage(Component.text("You're coming down with something (" + severity.displayName()
                + " fever). Rest, keep warm, and drink water.", NamedTextColor.YELLOW));
    }

    void recover(Player player, PlayerState state) {
        state.illness = null;
        state.nauseaTimer = Double.NaN;
        player.sendMessage(Component.text("You feel better.", NamedTextColor.GREEN));
    }

    /** A water bottle drunk while sick: a little shorter, up to a day in all. */
    void drank(Player player) {
        if (!plugin.sicknessOn() || !plugin.affected(player)) return;
        PlayerState state = plugin.state(player);
        if (state.illness == null) return;
        SicknessModel.Illness before = state.illness;
        state.illness = SicknessModel.drank(before, plugin.settings().sickness());
        if (SicknessModel.over(state.illness)) {
            recover(player, state);
        } else if (state.illness.waterSaved() > before.waterSaved()) {
            player.sendMessage(Component.text("That helps.", NamedTextColor.AQUA));
        }
    }

    /** The season a world is in now, for a roll made on request. */
    SeasonView.Season seasonNow(World world) {
        return plugin.ezvac().season(world).map(SeasonView::season).orElse(null);
    }
}
