package com.github.blade.ezclimates;

import com.github.blade.hvac.api.ClimateReading;
import com.github.blade.hvac.api.EzvacApi;
import com.github.blade.hvac.api.SeasonView;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Plants that follow EZvac's climate. Crops and saplings grow by the air
 * where they stand: not at all in the cold, slowly in cool air, at their
 * usual pace when it's warm, faster in warm humid air and slower again in
 * great heat. The air is EZvac's own reading at the plant, so a greenhouse
 * a system keeps warm grows crops through winter while the field outside
 * waits for spring.
 *
 * <p>Flowers come up on open grass in spring and mushrooms in shade in
 * autumn, round players, and go again later in the year. Each is recorded
 * in its chunk like snow and ice, and only recorded ones are ever taken
 * away, so a player's own planting is never touched. Main thread only.
 */
public final class Plants implements Listener {
    /** Flowers come up in air this warm, and die back in a hard frost. */
    static final double FLOWERS_FROM_F = 45.0, FROST_F = 28.0;
    /** Mushrooms come up in air this warm. */
    static final double MUSHROOMS_FROM_F = 36.0;
    /** A chunk gets one try for a new plant about this often, in ticks, at rate 1. */
    static final double TICKS_PER_TRY = 1_200.0;
    private static final Material[] FLOWERS = {Material.DANDELION, Material.POPPY, Material.AZURE_BLUET,
            Material.OXEYE_DAISY, Material.CORNFLOWER, Material.RED_TULIP, Material.ORANGE_TULIP, Material.WHITE_TULIP,
            Material.PINK_TULIP, Material.ALLIUM};

    private final Plugin plugin;
    private final EzvacApi ezvac;
    private final PlantSettings settings;
    private final SnowAndIce records;
    private boolean on = true;
    private double owed;
    private int slowed, sped, placed;

    public Plants(Plugin plugin, EzvacApi ezvac, PlantSettings settings, SnowAndIce records) {
        this.plugin = plugin;
        this.ezvac = ezvac;
        this.settings = settings;
        this.records = records;
        records.setPlantRule(Plants::ours, this::goes);
    }

    /** Switched in EZvac's Addons menu: off, crops grow as in vanilla and no flowers or mushrooms come up. */
    public void setOn(boolean on) { this.on = on; }

    /** What grows by the air: field crops, stems, bushes, cocoa, cane and saplings. Not kelp, bamboo, cactus or the Nether's own. */
    static boolean follows(Material type) {
        if (Tag.CROPS.isTagged(type) || Tag.SAPLINGS.isTagged(type)) return true;
        return switch (type) {
            case SWEET_BERRY_BUSH, COCOA, SUGAR_CANE, MELON, PUMPKIN, MELON_STEM, PUMPKIN_STEM, ATTACHED_MELON_STEM,
                 ATTACHED_PUMPKIN_STEM, TORCHFLOWER, PITCHER_PLANT, PITCHER_CROP, TORCHFLOWER_CROP, BROWN_MUSHROOM,
                 RED_MUSHROOM -> true;
            default -> false;
        };
    }

    /** How fast things grow at a block, against vanilla's pace, by EZvac's air there. */
    double growthAt(Block block) {
        ClimateReading reading = ezvac.readingAt(block.getLocation().add(0.5, 0.5, 0.5));
        return settings.growth(reading.temperatureF(), reading.humidity());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGrow(BlockGrowEvent event) {
        if (!on || !settings.crops() || !LookUpdater.seasonal(event.getBlock().getWorld())) return;
        if (!follows(event.getNewState().getType())) return;
        Block block = event.getBlock();
        double growth = growthAt(block);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (growth < 1.0) {
            if (random.nextDouble() >= growth) {
                event.setCancelled(true);
                slowed++;
            }
            return;
        }
        // Faster than vanilla: some steps count twice.
        if (random.nextDouble() < growth - 1.0 && event.getNewState().getBlockData() instanceof Ageable grown
                && grown.getAge() < grown.getMaximumAge()) {
            Material type = event.getNewState().getType();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (block.getType() != type || !(block.getBlockData() instanceof Ageable now)
                        || now.getAge() >= now.getMaximumAge()) return;
                now.setAge(now.getAge() + 1);
                block.setBlockData(now, true);
                sped++;
            });
        }
    }

    /** A sapling growing into a tree on its own (bone meal is the player's doing, and works as ever). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTree(StructureGrowEvent event) {
        if (!on || !settings.crops() || event.isFromBonemeal() || event.getWorld() == null
                || !LookUpdater.seasonal(event.getWorld())) return;
        double growth = growthAt(event.getLocation().getBlock());
        if (growth < 1.0 && ThreadLocalRandom.current().nextDouble() >= growth) {
            event.setCancelled(true);
            slowed++;
        }
    }

    /** Every tick: a few tries for a new flower or mushroom in the chunks round players. */
    public void tick() {
        if (!on || settings.maxPerChunk() <= 0 || (!settings.flowers() && !settings.mushrooms())) return;
        int chunks = records.areaSize();
        if (chunks == 0) return;
        owed = Math.min(8.0, owed + chunks * settings.rate() / TICKS_PER_TRY);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        while (owed >= 1.0) {
            owed -= 1.0;
            Block ground = records.randomGround(random, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            if (ground != null) sprout(ground, random);
        }
    }

    private void sprout(Block ground, ThreadLocalRandom random) {
        World world = ground.getWorld();
        SeasonView.Season season = ezvac.season(world).map(SeasonView::season).orElse(null);
        if (season == null) return;
        boolean flower = season == SeasonView.Season.SPRING && settings.flowers();
        boolean mushroom = season == SeasonView.Season.AUTUMN && settings.mushrooms();
        if (!flower && !mushroom) return;
        Block above = ground.getRelative(BlockFace.UP);
        if (!above.getType().isAir() || !above.getRelative(BlockFace.UP).getType().isAir()) return;
        Material soil = ground.getType();
        if (flower ? soil != Material.GRASS_BLOCK : !mushroomSoil(soil)) return;
        // Mushrooms come up under trees, or on podzol and mycelium anywhere: never out on open grass.
        if (mushroom && soil != Material.PODZOL && soil != Material.MYCELIUM && !underLeaves(above)) return;
        if (records.plantsIn(above.getChunk()) >= settings.maxPerChunk()) return;
        double airF = ezvac.outdoorAt(above.getLocation().add(0.5, 0.5, 0.5)).airF();
        if (airF < (flower ? FLOWERS_FROM_F : MUSHROOMS_FROM_F)) return;
        Material plant = flower ? flowerFor(above, random)
                : random.nextDouble() < 0.7 ? Material.BROWN_MUSHROOM : Material.RED_MUSHROOM;
        BlockData data = plant.createBlockData();
        // A flower stands where vanilla's could. A mushroom under a tree stands in more light than vanilla
        // lets one be planted in, so it is set down as it is; it drops if something beside it changes.
        if (flower && !above.canPlace(data)) return;
        above.setBlockData(data, false);
        records.recordPlant(above);
        placed++;
    }

    /** Whether a tree's leaves are over a spot: the nearest thing above it, within reach of a canopy. */
    private static boolean underLeaves(Block spot) {
        Block top = spot.getWorld().getHighestBlockAt(spot.getX(), spot.getZ(), HeightMap.MOTION_BLOCKING);
        return top.getY() > spot.getY() && top.getY() - spot.getY() <= 24 && Tag.LEAVES.isTagged(top.getType());
    }

    private static boolean mushroomSoil(Material soil) {
        return switch (soil) {
            case GRASS_BLOCK, PODZOL, MYCELIUM, COARSE_DIRT, ROOTED_DIRT, DIRT, MOSS_BLOCK -> true;
            default -> false;
        };
    }

    private static Material flowerFor(Block at, ThreadLocalRandom random) {
        String biome = at.getBiome().getKey().getKey();
        if (biome.contains("swamp")) return Material.BLUE_ORCHID;
        if (biome.contains("forest") && random.nextInt(4) == 0) return Material.LILY_OF_THE_VALLEY;
        return FLOWERS[random.nextInt(FLOWERS.length)];
    }

    /** What EZclimates brings up itself, so a record whose block is anything else is forgotten, not removed. */
    static boolean ours(Material type) {
        return Tag.SMALL_FLOWERS.isTagged(type) || type == Material.BROWN_MUSHROOM || type == Material.RED_MUSHROOM;
    }

    /**
     * Whether a recorded flower or mushroom goes at this look: flowers last
     * through summer and die back in autumn (at once in winter, or in a
     * hard frost), mushrooms go once autumn is over. Gradually, so a field
     * thins rather than vanishing.
     */
    private boolean goes(Block block) {
        SeasonView.Season season = ezvac.season(block.getWorld()).map(SeasonView::season).orElse(null);
        if (season == null || !on) return true;
        double roll = ThreadLocalRandom.current().nextDouble();
        if (Tag.SMALL_FLOWERS.isTagged(block.getType())) {
            if (season == SeasonView.Season.WINTER) return true;
            if (ezvac.outdoorAt(block.getLocation().add(0.5, 0.5, 0.5)).airF() < FROST_F) return true;
            return season == SeasonView.Season.AUTUMN && roll < 0.3;
        }
        return season != SeasonView.Season.AUTUMN && roll < 0.5;
    }

    /** What plants make of a spot, for /ezclimates here. */
    public List<String> describe(Location at) {
        List<String> lines = new ArrayList<>();
        if (!on) {
            lines.add("Plants: switched off in EZvac's Addons menu, so crops grow as in vanilla.");
            return lines;
        }
        ClimateReading reading = ezvac.readingAt(at);
        double growth = settings.growth(reading.temperatureF(), reading.humidity());
        lines.add(settings.crops() ? String.format(Locale.ROOT, "Crops here grow at %.0f%% of their usual pace: the air is"
                        + " %.1f F%s (%s).", growth * 100.0, reading.temperatureF(),
                Double.isFinite(reading.humidity()) ? String.format(Locale.ROOT, " at %.0f%% humidity", reading.humidity()) : "",
                settings.reason(reading.temperatureF(), reading.humidity()))
                : "Crops grow as in vanilla (plants.crops.enabled is false).");
        return lines;
    }

    /** Growth steps held back and added, and flowers and mushrooms brought up, since start. */
    public int[] counts() { return new int[] {slowed, sped, placed}; }
}
