package com.github.blade.ezclimates;

import com.github.blade.hvac.api.EzvacApi;
import com.github.blade.hvac.api.SeasonView;
import com.github.blade.hvac.api.WeatherEvent;
import com.github.blade.hvac.api.WeatherPlan;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChangeGameState;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/**
 * Drives each seasonal world's weather from its plan, and is the plan EZvac
 * reads (forecasts, heat waves and cold snaps, storms' wind). Minecraft's own
 * random weather changes are stopped; an admin's /weather and sleeping
 * through a storm still work, and the plan takes them in. A snowstorm
 * darkens players' sky without thunder, so there is no lightning.
 * Main thread only.
 */
public final class WeatherDriver implements WeatherPlan, Listener {
    private final Plugin plugin;
    private final EzvacApi ezvac;
    private final WeatherSettings settings;
    private final WeatherPlanner planner;
    private final File file;
    private final Consumer<String> warn;
    private final Map<UUID, WeatherPlanner.Plan> plans = new HashMap<>();
    /** Worlds whose players were sent a snowstorm's dark sky, to set right when it ends. */
    private final Set<UUID> darkened = new HashSet<>();
    /** True while EZclimates itself changes the weather, so its own changes go through. */
    private boolean driving;
    private boolean dirty;
    private long ticks;

    public WeatherDriver(Plugin plugin, EzvacApi ezvac, WeatherSettings settings, File file, Consumer<String> warn) {
        this.plugin = plugin;
        this.ezvac = ezvac;
        this.settings = settings;
        this.planner = new WeatherPlanner(settings);
        this.file = file;
        this.warn = warn;
        load();
    }

    public boolean enabled() { return settings.enabled(); }

    private boolean drives(World world) {
        return settings.enabled() && world != null && LookUpdater.seasonal(world);
    }

    /** The season that many days from today, from EZvac's calendar; spring where there are no seasons. */
    IntFunction<SeasonView.Season> seasons(World world) {
        SeasonView view = ezvac.season(world).orElse(null);
        if (view == null) return days -> SeasonView.Season.SPRING;
        int length = Math.max(1, view.daysPerSeason());
        int today = view.season().ordinal() * length + view.dayOfSeason() - 1;
        return days -> SeasonView.Season.values()[Math.floorMod(Math.floorDiv(today + days, length), 4)];
    }

    private static long seed(World world) {
        return world.getSeed() ^ 0x5EA50E5C11A7E5L;
    }

    /** A world's plan, brought up to date: what has passed dropped, later days maybe planned again. */
    public WeatherPlanner.Plan plan(World world) {
        WeatherPlanner.Plan plan = plans.computeIfAbsent(world.getUID(), ignored -> new WeatherPlanner.Plan());
        int before = plan.fronts.size() + plan.swings.size() + plan.revision;
        long until = plan.until;
        planner.revise(plan, world.getFullTime(), seed(world), seasons(world));
        if (plan.until != until || plan.fronts.size() + plan.swings.size() + plan.revision != before) dirty = true;
        return plan;
    }

    /** Every tick; the weather is set right once a second. */
    public void tick(Iterable<World> worlds) {
        if (!settings.enabled() || ++ticks % 20 != 0) return;
        for (World world : worlds) {
            if (!drives(world)) continue;
            WeatherPlanner.Plan plan = plan(world);
            drive(world, plan);
            sky(world, plan);
        }
        if (dirty && ticks % 1_200 == 0) save();
    }

    /** Sets the world's rain and thunder to the plan, with Minecraft's clocks counting to the next change. */
    private void drive(World world, WeatherPlanner.Plan plan) {
        long now = world.getFullTime();
        WeatherPlanner.Front front = plan.frontAt(now);
        boolean rain = front != null, thunder = front != null && front.thunders(now);
        driving = true;
        try {
            if (world.hasStorm() != rain) world.setStorm(rain);
            WeatherPlanner.Front next = plan.nextFront(now);
            long untilChange = rain ? front.end() - now : next == null ? WeatherPlanner.DAY : next.start() - now;
            world.setClearWeatherDuration(0);
            world.setWeatherDuration(ticks(untilChange));
            if (world.isThundering() != thunder) world.setThundering(thunder);
            long untilThunder = thunder ? front.end() - now
                    : front != null && front.thunderFrom() > now && !front.snowstorm() ? front.thunderFrom() - now
                    : untilChange + WeatherPlanner.HOUR;
            world.setThunderDuration(ticks(untilThunder));
        } finally {
            driving = false;
        }
    }

    private static int ticks(long ticks) {
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, ticks));
    }

    /** A snowstorm's dark sky, sent to each player, without thunder; set right as it ends. */
    private void sky(World world, WeatherPlanner.Plan plan) {
        boolean storm = world.hasStorm() && plan.eventAt(world.getFullTime()).orElse(null) == WeatherEvent.SNOWSTORM;
        if (!storm && !darkened.remove(world.getUID())) return;
        if (storm) darkened.add(world.getUID());
        float level = storm ? (float) settings.snowstormSky() : world.isThundering() ? 1.0f : 0.0f;
        for (Player player : world.getPlayers())
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerChangeGameState(
                    WrapperPlayServerChangeGameState.Reason.THUNDER_LEVEL_CHANGE, level));
    }

    /** How much faster snow settles in a world right now: a snowstorm's rate, or 1. */
    public double snowRate(World world) {
        if (!drives(world) || !world.hasStorm()) return 1.0;
        WeatherPlanner.Plan plan = plans.get(world.getUID());
        return plan != null && plan.eventAt(world.getFullTime()).orElse(null) == WeatherEvent.SNOWSTORM
                ? settings.snowstormSnowRate() : 1.0;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onWeather(WeatherChangeEvent event) {
        World world = event.getWorld();
        if (driving || !drives(world)) return;
        switch (event.getCause()) {
            case NATURAL, UNKNOWN -> event.setCancelled(true);
            case COMMAND, PLUGIN -> adoptNextTick(world);
            case SLEEP -> {
                // Sleeping through a storm ends it.
                plan(world).endFrontAt(world.getFullTime());
                dirty = true;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onThunder(ThunderChangeEvent event) {
        World world = event.getWorld();
        if (driving || !drives(world)) return;
        switch (event.getCause()) {
            case NATURAL, UNKNOWN -> event.setCancelled(true);
            case COMMAND, PLUGIN -> adoptNextTick(world);
            default -> { }
        }
    }

    /** /weather wins: once Minecraft has set its clocks, the plan takes it in for as long as it chose. */
    private void adoptNextTick(World world) {
        plugin.getServer().getScheduler().runTask(plugin, () -> adopt(world));
    }

    void adopt(World world) {
        WeatherPlanner.Plan plan = plan(world);
        long now = world.getFullTime();
        if (world.hasStorm()) {
            long length = Math.max(WeatherPlanner.HOUR, world.getWeatherDuration());
            plan.insert(new WeatherPlanner.Front(now, now + length, world.isThundering() ? now : -1L, false));
        } else {
            long clear = Math.max(world.getClearWeatherDuration(), world.getWeatherDuration());
            plan.endFrontAt(now);
            plan.fronts.removeIf(front -> front.start() >= now && front.start() < now + clear);
        }
        plan.until = Math.max(plan.until, now);
        dirty = true;
        drive(world, plan);
    }

    // ----- What EZvac reads: the plan as an API WeatherPlan -----

    @Override
    public List<Outlook> outlook(World world, int days) {
        if (!drives(world)) return List.of();
        return planner.outlook(plan(world), world.getFullTime(), days, seasons(world));
    }

    @Override
    public double shiftNowF(World world) {
        if (!drives(world)) return 0.0;
        WeatherPlanner.Plan plan = plans.get(world.getUID());
        return plan == null ? 0.0 : plan.shiftAt(world.getFullTime());
    }

    @Override
    public Optional<WeatherEvent> eventNow(World world) {
        if (!drives(world)) return Optional.empty();
        WeatherPlanner.Plan plan = plans.get(world.getUID());
        if (plan == null) return Optional.empty();
        Optional<WeatherEvent> event = plan.eventAt(world.getFullTime());
        // A storm only while it really rains (an admin may have cleared it).
        if (event.isPresent() && (event.get() == WeatherEvent.SNOWSTORM || event.get() == WeatherEvent.THUNDERSTORM)
                && !world.hasStorm()) {
            WeatherPlanner.Swing swing = plan.swingAt(world.getFullTime());
            return swing == null ? Optional.empty() : Optional.of(swing.event());
        }
        return event;
    }

    // ----- Commands -----

    /** The plan for the days ahead, for /ezclimates weather. */
    public List<String> describe(World world) {
        List<String> lines = new ArrayList<>();
        if (!drives(world)) {
            lines.add(settings.enabled() ? "EZclimates doesn't plan the weather in " + world.getName() + "."
                    : "EZclimates' weather is off (weather.enabled); Minecraft's own weather runs.");
            return lines;
        }
        WeatherPlanner.Plan plan = plan(world);
        long now = world.getFullTime();
        lines.add("Weather plan for " + world.getName() + " (sure for " + settings.exactDays() + " day(s); after that"
                + " it can change). Now: " + plan.eventAt(now).map(WeatherEvent::displayName)
                .orElse(plan.frontAt(now) != null ? "rain or snow" : "dry") + ".");
        List<Outlook> days = planner.outlook(plan, now, WeatherPlanner.HORIZON_DAYS - 1, seasons(world));
        String[] names = {"Today", "Tomorrow"};
        for (Outlook day : days)
            lines.add(String.format(Locale.ROOT, "  %s: rain %d%%, thunder %d%%%s%s",
                    day.ahead() < 2 ? names[day.ahead()] : "In " + day.ahead() + " days",
                    Math.round(day.rainChance() * 100), Math.round(day.thunderChance() * 100),
                    Math.abs(day.shiftF()) >= 0.5 ? String.format(Locale.ROOT, ", %+.0f F", day.shiftF()) : "",
                    day.note().isEmpty() ? day.event().map(e -> ", " + e.displayName()).orElse("") : ", " + day.note()));
        return lines;
    }

    /** Starts an event now, for {@code hours} in-game hours. */
    public String start(World world, String name, double hours) {
        if (!drives(world)) return "EZclimates doesn't plan the weather in " + world.getName() + ".";
        WeatherPlanner.Plan plan = plan(world);
        long now = world.getFullTime(), length = Math.max(WeatherPlanner.HOUR, Math.round(hours * WeatherPlanner.HOUR));
        switch (name.toLowerCase(Locale.ROOT)) {
            case "snowstorm" -> plan.insert(new WeatherPlanner.Front(now, now + length, -1L, true));
            case "thunderstorm" -> plan.insert(new WeatherPlanner.Front(now, now + length, now, false));
            case "heatwave" -> plan.insert(new WeatherPlanner.Swing(now, now + length, settings.heatWave().maxF()));
            case "coldsnap" -> plan.insert(new WeatherPlanner.Swing(now, now + length, -settings.coldSnap().maxF()));
            default -> {
                return "Events: snowstorm, thunderstorm, heatwave, coldsnap.";
            }
        }
        dirty = true;
        drive(world, plan);
        return "Started a " + name.toLowerCase(Locale.ROOT) + " in " + world.getName() + " for "
                + String.format(Locale.ROOT, "%.0f", hours) + " in-game hour(s).";
    }

    /** Plans a world's weather afresh from now. */
    public String replan(World world) {
        if (!drives(world)) return "EZclimates doesn't plan the weather in " + world.getName() + ".";
        WeatherPlanner.Plan plan = plan(world);
        plan.revision++;
        plan.cut(world.getFullTime());
        plan(world);
        dirty = true;
        return "Planned the weather in " + world.getName() + " afresh.";
    }

    // ----- weather.yml -----

    private void load() {
        if (!file.isFile()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection worlds = yaml.getConfigurationSection("worlds");
        if (worlds == null) return;
        for (String id : worlds.getKeys(false)) {
            try {
                ConfigurationSection entry = worlds.getConfigurationSection(id);
                if (entry == null) continue;
                WeatherPlanner.Plan plan = new WeatherPlanner.Plan();
                plan.until = entry.getLong("until");
                plan.revision = entry.getInt("revision");
                plan.revisedDay = entry.getLong("revised-day", Long.MIN_VALUE);
                for (String text : entry.getStringList("fronts")) {
                    String[] part = text.split(",");
                    plan.fronts.add(new WeatherPlanner.Front(Long.parseLong(part[0]), Long.parseLong(part[1]),
                            Long.parseLong(part[2]), Boolean.parseBoolean(part[3])));
                }
                for (String text : entry.getStringList("swings")) {
                    String[] part = text.split(",");
                    plan.swings.add(new WeatherPlanner.Swing(Long.parseLong(part[0]), Long.parseLong(part[1]),
                            Double.parseDouble(part[2])));
                }
                plans.put(UUID.fromString(id), plan);
            } catch (RuntimeException unreadable) {
                warn.accept("weather.yml has a world's plan that can't be read (" + unreadable.getMessage()
                        + "); it is planned afresh.");
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of("EZclimates' weather plan for each world. Use /ezclimates weather to see it;",
                "edit only while the server is stopped. Times are each world's full time in ticks."));
        for (Map.Entry<UUID, WeatherPlanner.Plan> entry : plans.entrySet()) {
            String path = "worlds." + entry.getKey();
            WeatherPlanner.Plan plan = entry.getValue();
            yaml.set(path + ".until", plan.until);
            yaml.set(path + ".revision", plan.revision);
            yaml.set(path + ".revised-day", plan.revisedDay);
            yaml.set(path + ".fronts", plan.fronts.stream().map(f -> f.start() + "," + f.end() + "," + f.thunderFrom()
                    + "," + f.snowstorm()).toList());
            yaml.set(path + ".swings", plan.swings.stream().map(s -> s.start() + "," + s.end() + "," + s.peakF()).toList());
        }
        try {
            yaml.save(file);
            dirty = false;
        } catch (IOException failure) {
            warn.accept("Could not save weather.yml (" + failure.getMessage() + "); it will be tried again.");
        }
    }
}
