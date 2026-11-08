package com.github.blade.ezclimates;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Runs before the worlds load: writes the datapack of seasonal biome copies
 * from the server's own vanilla biomes, and hands it to Paper, so the copies
 * are in the server's biome registry from the first start. The world's own
 * biomes are never changed; only what players are sent uses the copies.
 */
public final class EzClimatesBootstrap implements PluginBootstrap {
    /** How many copies this start wrote, for the plugin to report; -1 when none could be. */
    static volatile int copies = -1;
    static volatile String problem = "";

    @Override
    public void bootstrap(BootstrapContext context) {
        context.getLifecycleManager().registerEventHandler(LifecycleEvents.DATAPACK_DISCOVERY.newHandler(event -> {
            try {
                Palette palette = Palette.load(config(context).getConfigurationSection("colours"),
                        warning -> context.getLogger().warn(warning));
                SeasonPack.Vanilla vanilla = SeasonPack.read(EzClimatesBootstrap.class.getClassLoader());
                Path folder = context.getDataDirectory().resolve("pack");
                copies = SeasonPack.write(folder, vanilla, palette);
                event.registrar().discoverPack(folder, SeasonPack.ID, configurer -> configurer.autoEnableOnServerStart(true));
                context.getLogger().info("Wrote " + copies + " seasonal biome copies of " + vanilla.biomes().size()
                        + " overworld biomes (data pack format " + vanilla.format() + ").");
            } catch (IOException | RuntimeException failure) {
                copies = -1;
                problem = String.valueOf(failure.getMessage());
                context.getLogger().error("Could not write the seasonal biomes, so EZclimates shows no seasons: "
                        + failure.getMessage());
            }
        }));
    }

    /** config.yml as the server has it, or the bundled one before the first start. */
    private static YamlConfiguration config(BootstrapContext context) throws IOException {
        Path file = context.getDataDirectory().resolve("config.yml");
        YamlConfiguration config = new YamlConfiguration();
        try {
            if (Files.isRegularFile(file)) {
                config.loadFromString(Files.readString(file, StandardCharsets.UTF_8));
                return config;
            }
            try (InputStream bundled = EzClimatesBootstrap.class.getResourceAsStream("/config.yml")) {
                if (bundled != null) config.load(new InputStreamReader(bundled, StandardCharsets.UTF_8));
            }
        } catch (org.bukkit.configuration.InvalidConfigurationException unreadable) {
            context.getLogger().warn("config.yml could not be read (" + unreadable.getMessage()
                    + "), so the seasonal colours are the defaults.");
        }
        return config;
    }
}
