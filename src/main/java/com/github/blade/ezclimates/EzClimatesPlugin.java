package com.github.blade.ezclimates;

import com.github.blade.hvac.api.EzvacApi;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

/**
 * EZclimates: seasons you can see, from EZvac's climate. Rain and snow fall
 * where EZvac says, and leaves and grass change colour through the year, in
 * what each player is sent; the world keeps its own biomes.
 */
public final class EzClimatesPlugin extends JavaPlugin {
    private Looks looks;
    private ChunkRewriter rewriter;
    private LookUpdater updater;
    private PacketListenerCommon listener;
    private BukkitTask task;
    private Settings settings;
    private SnowAndIce snow;
    private java.io.File state;

    private void saveClearing(boolean clearing) {
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        yaml.set("clearing", clearing);
        try {
            yaml.save(state);
        } catch (java.io.IOException failure) {
            getLogger().warning("Could not save state.yml: " + failure.getMessage());
        }
    }

    /** API 1.9 or newer, within major version 1. */
    static boolean supported(String apiVersion) {
        String[] parts = apiVersion.split("\\.");
        try {
            return Integer.parseInt(parts[0]) == 1 && parts.length > 1 && Integer.parseInt(parts[1]) >= 9;
        } catch (NumberFormatException notANumber) {
            return false;
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        EzvacApi ezvac;
        try {
            ezvac = EzvacApi.get();
        } catch (IllegalStateException missing) {
            getLogger().severe(missing.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (!supported(ezvac.apiVersion())) {
            getLogger().severe("EZclimates needs EZvac's API 1.9 or newer (EZvac BETA 1.0 Pilot 4); EZvac "
                    + ezvac.pluginVersion() + " offers API " + ezvac.apiVersion() + ". Update EZvac.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (EzClimatesBootstrap.copies <= 0) {
            getLogger().severe("The seasonal biomes weren't loaded (" + EzClimatesBootstrap.problem
                    + "), so EZclimates shows nothing. It needs Paper or Purpur 26.1 or newer, and a restart"
                    + " (not /reload) after it is installed.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        settings = Settings.load(getConfig(), getLogger()::warning);
        looks = new Looks();
        rewriter = new ChunkRewriter(looks, getLogger()::warning);
        updater = new LookUpdater(ezvac, looks, rewriter, settings);
        listener = PacketEvents.getAPI().getEventManager().registerListener(rewriter, PacketListenerPriority.HIGH);
        getServer().getPluginManager().registerEvents(updater, this);
        updater.start(getServer().getOnlinePlayers());
        state = new java.io.File(getDataFolder(), "state.yml");
        boolean clearing = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(state).getBoolean("clearing");
        snow = new SnowAndIce(ezvac, settings, new org.bukkit.NamespacedKey(this, "placed"),
                new org.bukkit.NamespacedKey(this, "covered"), clearing, looks);
        getServer().getPluginManager().registerEvents(snow, this);
        snow.start(getServer().getWorlds());
        if (clearing) getLogger().info("Still clearing snow and ice (/ezclimates resume to stop).");
        // The weather engine: plans each seasonal world's weather and drives Minecraft's to match.
        WeatherSettings weatherSettings = WeatherSettings.load(getConfig().getConfigurationSection("weather"),
                getLogger()::warning);
        weather = new WeatherDriver(this, ezvac, weatherSettings, new java.io.File(getDataFolder(), "weather.yml"),
                getLogger()::warning);
        if (weather.enabled()) {
            getServer().getPluginManager().registerEvents(weather, this);
            ezvac.setWeatherPlan(this, weather);
            snow.setBoost(weather::snowRate);
        }
        task = getServer().getScheduler().runTaskTimer(this, () -> {
            long start = System.nanoTime();
            weather.tick(getServer().getWorlds());
            long planned = System.nanoTime();
            updater.tick(getServer().getWorlds());
            long looked = System.nanoTime();
            snow.tick(getServer().getWorlds());
            long done = System.nanoTime();
            timing[0] += planned - start;
            timing[1] += looked - planned;
            timing[2] += done - looked;
            if (++timedTicks >= 1_200) {
                lastTiming = new double[]{timing[0] / 1e6 / timedTicks, timing[1] / 1e6 / timedTicks, timing[2] / 1e6 / timedTicks};
                java.util.Arrays.fill(timing, 0);
                timedTicks = 0;
            }
        }, 1L, 1L);
        registerCommand("ezclimates", "EZclimates' status", new Commands());
        getLogger().info("EZclimates is showing EZvac's seasons: " + EzClimatesBootstrap.copies
                + " seasonal biomes, rain and snow " + (settings.rainAndSnow() ? "on" : "off")
                + ", colours " + (settings.colours() ? "on" : "off") + ".");
    }

    @Override
    public void onDisable() {
        if (task != null) task.cancel();
        if (listener != null) PacketEvents.getAPI().getEventManager().unregisterListener(listener);
        if (weather != null && weather.enabled()) weather.save();
        if (snow != null) snow.flush();
    }

    private WeatherDriver weather;
    /** Time spent each tick on the weather, the looks and snow and ice, over the last minute. */
    private final long[] timing = new long[3];
    private int timedTicks;
    private double[] lastTiming;

    /** /ezclimates, for admins (ezclimates.admin). */
    private final class Commands implements io.papermc.paper.command.brigadier.BasicCommand {
        @Override
        public void execute(io.papermc.paper.command.brigadier.CommandSourceStack source, String[] args) {
            run(source.getSender(), "ezclimates", args);
        }

        @Override
        public java.util.Collection<String> suggest(io.papermc.paper.command.brigadier.CommandSourceStack source,
                                                    String[] args) {
            if (args.length <= 1) return List.of("status", "here", "weather", "clear", "resume");
            if (args[0].equalsIgnoreCase("weather") && args.length == 2) return List.of("event", "replan");
            if (args[0].equalsIgnoreCase("weather") && args.length == 3 && args[1].equalsIgnoreCase("event"))
                return List.of("snowstorm", "thunderstorm", "heatwave", "coldsnap");
            return List.of();
        }

        @Override
        public String permission() { return "ezclimates.admin"; }
    }

    private void run(CommandSender sender, String label, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(java.util.Locale.ROOT);
        switch (sub) {
            case "status" -> {
                sender.sendMessage("EZclimates " + getPluginMeta().getVersion() + ": " + EzClimatesBootstrap.copies
                        + " seasonal biomes; rain and snow " + (settings.rainAndSnow() ? "on" : "off") + ", colours "
                        + (settings.colours() ? "on" : "off") + ".");
                for (var world : getServer().getWorlds()) {
                    Looks.WorldLook look = looks.worlds.get(world.getUID());
                    if (look != null) sender.sendMessage("  " + world.getName() + ": " + look.stage.key().replace('_', ' ')
                            + ", " + look.lines.size() + " chunks' snow lines known.");
                }
                sender.sendMessage("  Chunks resent since start: " + updater.resentTotal() + ".");
                if (lastTiming != null) sender.sendMessage(String.format(java.util.Locale.ROOT,
                        "  Time per tick over the last minute: weather %.3f ms, looks %.3f ms, snow and ice %.3f ms.",
                        lastTiming[0], lastTiming[1], lastTiming[2]));
                int[] recorded = snow.recorded();
                sender.sendMessage("  Snow and ice in loaded chunks: " + recorded[0] + " snow, " + recorded[1]
                        + " ice; " + snow.placedTotal() + " placed and " + snow.meltedTotal() + " melted since start"
                        + (snow.clearing() ? ". Clearing: nothing new is placed (/ezclimates resume)." : "."));
            }
            case "debug" -> {
                if (args.length >= 3 && args[1].equalsIgnoreCase("rewrite")) {
                    rewriter.enabled = !args[2].equalsIgnoreCase("off");
                    sender.sendMessage("Chunk rewriting " + (rewriter.enabled ? "on" : "off") + ". Chunks already sent"
                            + " stay as they were until they're sent again (go far away and back).");
                } else if (args.length >= 3 && args[1].equalsIgnoreCase("cover")) {
                    org.bukkit.entity.Player player = getServer().getPlayerExact(args[2]);
                    if (player == null) {
                        sender.sendMessage("No such player online.");
                        return;
                    }
                    int radius = args.length >= 4 ? Math.max(4, Math.min(64, Integer.parseInt(args[3]))) : 24;
                    for (String line : snow.cover(player.getWorld(), player.getLocation().getBlockX(),
                            player.getLocation().getBlockZ(), radius)) sender.sendMessage(line);
                } else if (args.length >= 3 && args[1].equalsIgnoreCase("chunks")) {
                    org.bukkit.entity.Player player = getServer().getPlayerExact(args[2]);
                    if (player == null) {
                        sender.sendMessage("No such player online.");
                        return;
                    }
                    rewriter.describe.add(player.getUniqueId());
                    sender.sendMessage("The next chunk sent to " + player.getName() + " is described in the log.");
                } else {
                    sender.sendMessage("Usage: /" + label + " debug rewrite <on|off> | debug chunks <player> | debug cover <player> [radius]");
                }
            }
            case "weather" -> {
                org.bukkit.World world = sender instanceof org.bukkit.entity.Player player ? player.getWorld()
                        : getServer().getWorlds().get(0);
                if (args.length >= 3 && args[1].equalsIgnoreCase("event")) {
                    double hours = 12.0;
                    if (args.length >= 4) {
                        try {
                            hours = Math.max(1.0, Math.min(240.0, Double.parseDouble(args[3])));
                        } catch (NumberFormatException notANumber) {
                            sender.sendMessage("Hours must be a number.");
                            return;
                        }
                    }
                    sender.sendMessage(weather.start(world, args[2], hours));
                } else if (args.length >= 2 && args[1].equalsIgnoreCase("replan")) {
                    sender.sendMessage(weather.replan(world));
                } else {
                    for (String line : weather.describe(world)) sender.sendMessage(line);
                }
            }
            case "here" -> {
                org.bukkit.Location at;
                // From the console, a player's name looks where they stand, and at what their client was sent.
                org.bukkit.entity.Player named = args.length >= 2 ? getServer().getPlayerExact(args[1]) : null;
                if (named != null) {
                    at = named.getLocation();
                    for (String line : snow.describe(at.getWorld(), at.getBlockX(), at.getBlockZ())) sender.sendMessage(line);
                    for (String line : updater.shownTo(named)) sender.sendMessage(line);
                    return;
                }
                if (sender instanceof org.bukkit.entity.Player player) at = player.getLocation();
                else if (args.length >= 3) {
                    try {
                        at = new org.bukkit.Location(getServer().getWorlds().get(0), Integer.parseInt(args[1]), 0,
                                Integer.parseInt(args[2]));
                    } catch (NumberFormatException notANumber) {
                        sender.sendMessage("Usage: /" + label + " here <x> <z> (from the console)");
                        return;
                    }
                } else {
                    sender.sendMessage("Usage: /" + label + " here <x> <z> (from the console)");
                    return;
                }
                for (String line : snow.describe(at.getWorld(), at.getBlockX(), at.getBlockZ())) sender.sendMessage(line);
                if (sender instanceof org.bukkit.entity.Player player)
                    for (String line : updater.shownTo(player)) sender.sendMessage(line);
            }
            case "clear" -> {
                snow.setClearing(true);
                saveClearing(true);
                sender.sendMessage("Clearing: every recorded block of snow and ice melts, in loaded chunks now and in"
                        + " others as they load, and nothing new is placed. /ezclimates status shows what's left;"
                        + " /ezclimates resume goes back to normal.");
            }
            case "resume" -> {
                snow.setClearing(false);
                saveClearing(false);
                sender.sendMessage("Snow and ice follow EZvac again.");
            }
            default -> sender.sendMessage("Usage: /" + label + " status | here | weather [event <name> [hours] | replan]"
                    + " | clear | resume");
        }
    }
}
