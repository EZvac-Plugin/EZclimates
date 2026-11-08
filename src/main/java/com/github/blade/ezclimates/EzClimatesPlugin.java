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
        task = getServer().getScheduler().runTaskTimer(this, () -> updater.tick(getServer().getWorlds()), 1L, 1L);
        registerCommand("ezclimates", "EZclimates' status", new Commands());
        getLogger().info("EZclimates is showing EZvac's seasons: " + EzClimatesBootstrap.copies
                + " seasonal biomes, rain and snow " + (settings.rainAndSnow() ? "on" : "off")
                + ", colours " + (settings.colours() ? "on" : "off") + ".");
    }

    @Override
    public void onDisable() {
        if (task != null) task.cancel();
        if (listener != null) PacketEvents.getAPI().getEventManager().unregisterListener(listener);
    }

    /** /ezclimates, for admins (ezclimates.admin). */
    private final class Commands implements io.papermc.paper.command.brigadier.BasicCommand {
        @Override
        public void execute(io.papermc.paper.command.brigadier.CommandSourceStack source, String[] args) {
            run(source.getSender(), "ezclimates", args);
        }

        @Override
        public java.util.Collection<String> suggest(io.papermc.paper.command.brigadier.CommandSourceStack source,
                                                    String[] args) {
            return args.length <= 1 ? List.of("status") : List.of();
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
            }
            default -> sender.sendMessage("Usage: /" + label + " status");
        }
    }
}
