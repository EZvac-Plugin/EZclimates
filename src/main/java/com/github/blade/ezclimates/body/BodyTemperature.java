package com.github.blade.ezclimates.body;

import com.github.blade.ezclimates.body.model.ConfigUpgrade;
import com.github.blade.ezclimates.body.model.ExposureSettings;
import com.github.blade.hvac.api.EzvacApi;
import com.github.blade.hvac.api.TemperatureUnit;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Body temperature: heat and cold that wear down survival players caught in
 * the elements, and an optional sickness system. This was the LiveExposure
 * addon until it became a part of EZclimates (0.1.0); its settings are in
 * {@code body.yml}, and a server that had LiveExposure gets its settings and
 * its players' body temperatures brought over.
 *
 * <p>It is a part EZvac's Addons menu switches, off until a server switches
 * it on (it can kill), and it stands down while the LiveExposure plugin
 * itself is still installed. Main thread only.
 */
public final class BodyTemperature {
    /** In body.yml: whether it runs on a server that has never switched it in EZvac's Addons menu. */
    static final String ON_BY_DEFAULT = "on-by-default";

    private final JavaPlugin plugin;
    private final EzvacApi ezvac;
    private final File file;
    private YamlConfiguration config = new YamlConfiguration();
    private ExposureSettings settings = ExposureSettings.defaults();
    private PlayerState.Keys keys;
    private final Map<UUID, PlayerState> states = new HashMap<>();
    private final Random random = new Random();
    private final Commands commands = new Commands(this);
    private SicknessService sickness;
    private ExposureListener listener;
    private BukkitTask saveTask;
    private boolean running;

    public BodyTemperature(JavaPlugin plugin, EzvacApi ezvac) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.ezvac = Objects.requireNonNull(ezvac, "ezvac");
        this.file = new File(plugin.getDataFolder(), "body.yml");
    }

    /**
     * Makes body.yml where there is none (from LiveExposure's config.yml
     * when that is there), adds settings new since it was written, and reads
     * it. Called once at start-up, before anything runs.
     */
    public void prepare() {
        if (!file.exists()) {
            File old = new File(plugin.getDataFolder().getParentFile(), "LiveExposure" + File.separator + "config.yml");
            if (!old.isFile() || !bringOver(old)) plugin.saveResource("body.yml", false);
        }
        upgrade();
        loadSettings();
    }

    /** LiveExposure's settings become body.yml: without its shade (EZvac's sun does that now), and on from the start. */
    private boolean bringOver(File old) {
        YamlConfiguration theirs = new YamlConfiguration();
        try {
            theirs.load(old);
            theirs.set("shade", null);
            theirs.set(ON_BY_DEFAULT, true);
            theirs.save(file);
        } catch (IOException | InvalidConfigurationException failure) {
            plugin.getLogger().warning("LiveExposure's config.yml couldn't be brought over (" + failure.getMessage()
                    + "); body temperature starts with its stock settings.");
            return false;
        }
        plugin.getLogger().info("Brought LiveExposure's settings over to body.yml: body temperature is part of"
                + " EZclimates now. It stays on, as it was; its shade setting is gone, since EZvac's own sun and"
                + " shade count. The LiveExposure jar and folder can be removed.");
        return true;
    }

    private void upgrade() {
        YamlConfiguration current = new YamlConfiguration();
        YamlConfiguration bundled = new YamlConfiguration();
        try (InputStream input = Objects.requireNonNull(plugin.getResource("body.yml"), "bundled body.yml")) {
            current.load(file);
            bundled.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (IOException | InvalidConfigurationException unreadable) {
            // Never rewrite a file that couldn't be read: loadSettings says what's wrong with it.
            return;
        }
        List<String> changed = ConfigUpgrade.apply(current, bundled);
        if (changed.isEmpty()) return;
        File backup = new File(plugin.getDataFolder(), "body.yml.before-" + plugin.getPluginMeta().getVersion() + ".bak");
        try {
            if (!backup.exists()) Files.copy(file.toPath(), backup.toPath());
            current.save(file);
            plugin.getLogger().info("Updated body.yml: " + String.join(", ", changed) + ". The previous file was kept as "
                    + backup.getName() + ".");
        } catch (IOException failure) {
            plugin.getLogger().warning("Could not update body.yml (" + failure.getMessage() + "); it was left as it was.");
        }
    }

    /** Reads body.yml again; a bad value warns and keeps its section's defaults. */
    void loadSettings() {
        YamlConfiguration read = new YamlConfiguration();
        try {
            read.load(file);
        } catch (IOException | InvalidConfigurationException failure) {
            plugin.getLogger().warning("body.yml couldn't be read (" + failure.getMessage() + "); body temperature"
                    + " keeps the settings it had.");
            return;
        }
        config = read;
        settings = ExposureSettings.load(config, plugin.getLogger()::warning);
    }

    /** Whether it runs on a server that never switched it in EZvac's Addons menu (body.yml, on-by-default). */
    public boolean onByDefault() {
        return config.getBoolean(ON_BY_DEFAULT, false);
    }

    /** True while the LiveExposure plugin itself is installed: body temperature is left to it. */
    public boolean leftToLiveExposure() {
        return plugin.getServer().getPluginManager().getPlugin("LiveExposure") != null;
    }

    public boolean running() { return running; }

    /** Starts or stops everything it does; it never starts beside the LiveExposure plugin. */
    public void setRunning(boolean on) {
        on = on && !leftToLiveExposure();
        if (on == running) return;
        running = on;
        if (on) start();
        else stop();
    }

    private void start() {
        keys = PlayerState.Keys.of(plugin);
        for (Player player : plugin.getServer().getOnlinePlayers()) state(player);
        sickness = new SicknessService(this);
        listener = new ExposureListener(this);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        sickness.start();
        BodyHud.register(this);
        // Saved now and then as well as on leaving, so a crash costs at most a minute.
        saveTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::saveAll, 1200L, 1200L);
        plugin.getLogger().info("Body temperature is on: sickness " + (settings.sickness().enabled() ? "on" : "off")
                + ", " + (settings.damage().lethal() ? "deadly" : "never deadly") + ".");
    }

    private void stop() {
        if (saveTask != null) saveTask.cancel();
        saveTask = null;
        if (sickness != null) sickness.stop();
        if (listener != null) HandlerList.unregisterAll(listener);
        listener = null;
        try {
            ezvac.hud().unregister(plugin, BodyHud.KEY);
        } catch (RuntimeException gone) {
            // EZvac may already have stopped.
        }
        saveAll();
        states.clear();
    }

    /** Saves and stops, as EZclimates disables. */
    public void shutdown() {
        if (running) stop();
        running = false;
    }

    /** {@code /bodytemp}. */
    public boolean bodyTemp(CommandSender sender) {
        if (!running) {
            sender.sendMessage(net.kyori.adventure.text.Component.text(leftToLiveExposure()
                    ? "Body temperature is LiveExposure's on this server: use its own /bodytemp."
                    : "Body temperature is off on this server.", net.kyori.adventure.text.format.NamedTextColor.GRAY));
            return true;
        }
        return commands.bodyTemp(sender);
    }

    /** {@code /ezclimates body ...}: {@code args} are what follows {@code body}. */
    public boolean admin(CommandSender sender, String[] args) {
        if (!running && !(args.length > 0 && args[0].equalsIgnoreCase("reload"))) {
            sender.sendMessage(net.kyori.adventure.text.Component.text(leftToLiveExposure()
                    ? "The LiveExposure plugin is installed, so body temperature is left to it. Remove its jar to use"
                    + " EZclimates' own."
                    : "Body temperature is off. Switch it on in /hvac addons (EZclimates, Body temperature).",
                    net.kyori.adventure.text.format.NamedTextColor.YELLOW));
            return true;
        }
        return commands.admin(sender, args);
    }

    public List<String> complete(String[] args) {
        return commands.complete(args);
    }

    void saveAll() {
        if (keys == null) return;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            PlayerState state = states.get(player.getUniqueId());
            if (state != null) state.save(player, keys);
        }
    }

    PlayerState state(Player player) {
        return states.computeIfAbsent(player.getUniqueId(), ignored -> PlayerState.loadCarryingOver(player, keys));
    }

    void forget(Player player) {
        PlayerState state = states.remove(player.getUniqueId());
        if (state != null) state.save(player, keys);
    }

    /** True when heat, cold and sickness apply to this player where they are now. */
    boolean affected(Player player) {
        return running && !player.hasPermission("liveexposure.bypass")
                && settings.scope().appliesIn(player.getWorld().getName(), player.getGameMode().name());
    }

    boolean sicknessOn() { return settings.sickness().enabled(); }

    /** {@code 95.8 °F}, or °C when EZvac shows Celsius. */
    String degrees(double fahrenheit) {
        TemperatureUnit units = ezvac.units();
        return String.format(Locale.US, "%.1f °%s", units.fromFahrenheit(fahrenheit), units.name());
    }

    JavaPlugin plugin() { return plugin; }
    Server getServer() { return plugin.getServer(); }
    Logger getLogger() { return plugin.getLogger(); }
    YamlConfiguration getConfig() { return config; }
    EzvacApi ezvac() { return ezvac; }
    ExposureSettings settings() { return settings; }
    Random random() { return random; }
    SicknessService sickness() { return sickness; }
}
