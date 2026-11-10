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
 * <p>Flowers come up on open grass in spring, with petals under cherry
 * trees; in autumn leaf litter and mushrooms come up under trees. All round
 * players, and all go again later in the year. Each is recorded
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
        if (!on || !(settings.flowers() || settings.mushrooms() || settings.leafLitter() || settings.petals())) return;
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
        if (season != SeasonView.Season.SPRING && season != SeasonView.Season.AUTUMN) return;
        Block above = ground.getRelative(BlockFace.UP);
        if (!above.getType().isAir() || !above.getRelative(BlockFace.UP).getType().isAir()) return;
        Material soil = ground.getType();
        Material canopy = canopyOver(above);
        Material plant = season == SeasonView.Season.SPRING ? inSpring(above, soil, canopy, random)
                : inAutumn(soil, canopy, random);
        if (plant == null) return;
        // Ground cover (leaf litter, petals) lies thicker than flowers and mushrooms stand.
        boolean cover = cover(plant);
        if (records.plantsIn(above.getChunk(), type -> cover(type) == cover)
                >= (cover ? settings.maxCoverPerChunk() : settings.maxPerChunk())) return;
        double airF = ezvac.outdoorAt(above.getLocation().add(0.5, 0.5, 0.5)).airF();
        if (airF < (season == SeasonView.Season.SPRING ? FLOWERS_FROM_F : MUSHROOMS_FROM_F)) return;
        BlockData data = dataFor(plant, random);
        // Everything stands where vanilla's could, but a mushroom: under a tree it is in more light than
        // vanilla lets one be planted in, so it is set down as it is, and drops if something beside it changes.
        boolean mushroom = plant == Material.BROWN_MUSHROOM || plant == Material.RED_MUSHROOM;
        if (!mushroom && !above.canPlace(data)) return;
        above.setBlockData(data, false);
        records.recordPlant(above);
        placed++;
    }

    /** What comes up at a spot in spring: petals under a cherry tree, else flowers on grass; null for nothing. */
    private Material inSpring(Block at, Material soil, Material canopy, ThreadLocalRandom random) {
        if (soil != Material.GRASS_BLOCK) return null;
        if (canopy == Material.CHERRY_LEAVES) return settings.petals() ? Material.PINK_PETALS : null;
        if (!settings.flowers()) return null;
        String biome = at.getBiome().getKey().getKey();
        if (biome.contains("swamp")) return Material.BLUE_ORCHID;
        // Wildflowers in meadows, plains and birch woods, among the single flowers.
        if ((biome.contains("meadow") || biome.contains("plains") || biome.contains("birch")) && random.nextInt(3) == 0)
            return Material.WILDFLOWERS;
        if (biome.contains("forest") && random.nextInt(4) == 0) return Material.LILY_OF_THE_VALLEY;
        return FLOWERS[random.nextInt(FLOWERS.length)];
    }

    /**
     * What comes up at a spot in autumn: leaf litter and mushrooms under
     * broadleaf trees, mushrooms alone on podzol and mycelium, and nothing
     * out on open grass.
     */
    private Material inAutumn(Material soil, Material canopy, ThreadLocalRandom random) {
        Material mushroom = random.nextDouble() < 0.7 ? Material.BROWN_MUSHROOM : Material.RED_MUSHROOM;
        if (soil == Material.PODZOL || soil == Material.MYCELIUM) return settings.mushrooms() ? mushroom : null;
        if (canopy == null || !mushroomSoil(soil)) return null;
        boolean litter = settings.leafLitter() && shedsLeaves(canopy);
        if (litter && (!settings.mushrooms() || random.nextInt(5) != 0)) return Material.LEAF_LITTER;
        return settings.mushrooms() ? mushroom : null;
    }

    /** The leaves over a spot: the nearest thing above it, within reach of a canopy; null under open sky or a roof. */
    private static Material canopyOver(Block spot) {
        Block top = spot.getWorld().getHighestBlockAt(spot.getX(), spot.getZ(), HeightMap.MOTION_BLOCKING);
        return top.getY() > spot.getY() && top.getY() - spot.getY() <= 24 && Tag.LEAVES.isTagged(top.getType())
                ? top.getType() : null;
    }

    /** Trees that drop their leaves in autumn: the broadleaf ones, not spruce, jungle, mangrove, azalea or cherry. */
    static boolean shedsLeaves(Material leaves) {
        return switch (leaves) {
            case OAK_LEAVES, BIRCH_LEAVES, DARK_OAK_LEAVES, ACACIA_LEAVES, PALE_OAK_LEAVES -> true;
            default -> false;
        };
    }

    /** Ground cover that lies flat, as against flowers and mushrooms that stand. */
    static boolean cover(Material type) {
        return type == Material.LEAF_LITTER || type == Material.PINK_PETALS;
    }

    private static boolean mushroomSoil(Material soil) {
        return switch (soil) {
            case GRASS_BLOCK, PODZOL, MYCELIUM, COARSE_DIRT, ROOTED_DIRT, DIRT, MOSS_BLOCK -> true;
            default -> false;
        };
    }

    /** A plant as it is set down: leaf litter, petals and wildflowers in a random amount, facing a random way. */
    private static BlockData dataFor(Material plant, ThreadLocalRandom random) {
        String amount = plant == Material.LEAF_LITTER ? "segment_amount"
                : plant == Material.PINK_PETALS || plant == Material.WILDFLOWERS ? "flower_amount" : null;
        if (amount != null) {
            String facing = new String[] {"north", "east", "south", "west"}[random.nextInt(4)];
            try {
                return org.bukkit.Bukkit.createBlockData(plant.getKey() + "[" + amount + "=" + (1 + random.nextInt(4))
                        + ",facing=" + facing + "]");
            } catch (IllegalArgumentException differentOnThisVersion) {
                // Its plain form, then.
            }
        }
        return plant.createBlockData();
    }

    /** What EZclimates brings up itself, so a record whose block is anything else is forgotten, not removed. */
    static boolean ours(Material type) {
        return Tag.SMALL_FLOWERS.isTagged(type) || type == Material.BROWN_MUSHROOM || type == Material.RED_MUSHROOM
                || type == Material.WILDFLOWERS || type == Material.PINK_PETALS || type == Material.LEAF_LITTER;
    }

    /**
     * Whether a recorded plant goes at this look, by what it is: flowers
     * and wildflowers last through summer and die back in autumn (at once
     * in winter, or in a hard frost); petals go once spring is over; leaf
     * litter lies through autumn and winter and is gone by spring;
     * mushrooms go once autumn is over. Gradually, so a field thins rather
     * than vanishing.
     */
    private boolean goes(Block block) {
        SeasonView.Season season = ezvac.season(block.getWorld()).map(SeasonView::season).orElse(null);
        if (season == null || !on) return true;
        double roll = ThreadLocalRandom.current().nextDouble();
        Material type = block.getType();
        if (type == Material.LEAF_LITTER)
            return (season == SeasonView.Season.SPRING || season == SeasonView.Season.SUMMER) && roll < 0.5;
        if (type == Material.PINK_PETALS) return season != SeasonView.Season.SPRING && roll < 0.5;
        if (type == Material.BROWN_MUSHROOM || type == Material.RED_MUSHROOM)
            return season != SeasonView.Season.AUTUMN && roll < 0.5;
        if (season == SeasonView.Season.WINTER) return true;
        if (ezvac.outdoorAt(block.getLocation().add(0.5, 0.5, 0.5)).airF() < FROST_F) return true;
        return season == SeasonView.Season.AUTUMN && roll < 0.3;
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
