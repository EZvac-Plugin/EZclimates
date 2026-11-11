package com.github.blade.ezclimates.body;

import com.github.blade.hvac.api.ClimateReading;
import com.github.blade.hvac.api.event.PlayerClimateUpdateEvent;
import com.github.blade.ezclimates.body.model.BodyModel;
import com.github.blade.ezclimates.body.model.ExposureSettings;
import com.github.blade.ezclimates.body.model.SicknessModel;
import com.github.blade.ezclimates.body.model.Stage;
import com.github.blade.ezclimates.body.model.Surroundings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.HashMap;
import java.util.Map;

/**
 * Steps every player's body on each of EZvac's climate updates and applies
 * what their stage, and any fever, does to them.
 */
final class ExposureListener implements Listener {
    /** Effects last a little longer than the gap between updates, so they fade out by themselves. */
    private static final int EFFECT_TICKS = 60;
    private static final int NAUSEA_TICKS = 80;
    /** Darkness pulses slowly: long enough to keep pulsing between updates. */
    private static final int DARKNESS_TICKS = 100;

    private final BodyTemperature plugin;

    ExposureListener(BodyTemperature plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClimate(PlayerClimateUpdateEvent event) {
        Player player = event.getPlayer();
        if (player.isDead()) return;
        PlayerState state = plugin.state(player);
        ExposureSettings settings = plugin.settings();
        long now = Bukkit.getCurrentTick();
        double seconds = state.lastTick < 0 ? 0.5 : Math.min(5.0, Math.max(0, now - state.lastTick) / 20.0);
        state.lastTick = now;

        SicknessModel.Illness illness = plugin.sicknessOn() ? state.illness : null;
        double restF = illness == null ? ExposureSettings.Thresholds.REST_F
                : SicknessModel.feverF(illness.severity(), settings.sickness());
        double heatMultiplier = illness == null ? 1.0 : SicknessModel.heatMultiplier(illness.severity(), settings.sickness());

        if (!plugin.affected(player)) {
            // Creative, a bypass, or a world it doesn't apply in: the body simply settles again.
            double comfortable = (settings.comfort().lowF() + settings.comfort().highF()) / 2.0;
            state.bodyF = BodyModel.step(state.bodyF, BodyModel.Conditions.healthy(comfortable, false, 0, false),
                    seconds, settings);
            state.stage = Stage.of(state.bodyF, settings.thresholds());
            return;
        }

        ClimateReading reading = event.getReading();
        double feelsF = reading.inWater() ? reading.temperatureF()
                : reading.feelsLikeF();   // EZvac's feels-like counts the sun and the shade itself (BETA 1.0 Pilot 4)
        BodyModel.Extras extras = extras(player, state, reading, seconds, now);
        BodyModel.Conditions conditions = new BodyModel.Conditions(feelsF, reading.inWater(),
                coldResistanceF(player, settings), player.hasPotionEffect(PotionEffectType.FIRE_RESISTANCE),
                restF, heatMultiplier, extras);
        state.bodyF = BodyModel.step(state.bodyF, conditions, seconds, settings);
        // Keeping warm burns food: the colder it is after everything worn, eaten and near, the faster.
        state.hungerFactor = Surroundings.hungerFactor(settings.comfort().lowF()
                - BodyModel.effectiveF(conditions, settings.comfort()), plugin.surroundings().metabolism());

        state.daySeconds += seconds;
        if (BodyModel.exposedToCold(BodyModel.surroundingsF(conditions, settings.comfort()), settings.comfort())
                || state.bodyF < settings.thresholds().chilledF())
            state.coldSeconds += seconds;
        // Asleep where it still feels cold, with everything worn and eaten counted: a cold night.
        if (player.isSleeping() && BodyModel.effectiveF(conditions, settings.comfort()) < settings.comfort().lowF())
            state.sleptCold = true;
        breathe(player, state, reading, seconds);

        Stage stage = Stage.of(state.bodyF, settings.thresholds());
        if (stage.worseThan(state.stage)) {
            player.sendMessage(Component.text(warning(stage), stage.cold() ? NamedTextColor.AQUA : NamedTextColor.GOLD));
            if (stage == Stage.SEVERE_HEAT) effect(player, PotionEffectType.NAUSEA, 0, NAUSEA_TICKS);
        }
        state.stage = stage;
        apply(player, state, stage, illness, seconds, settings);
        actionBar(player, state, stage, illness);
    }

    /**
     * What the body meets besides the air this step (EZclimates 0.2.0): the
     * rain on it and how wet it is, fires near it, metal armor in the sun,
     * how hard it is working, and a meal still warming or cooling it.
     */
    private BodyModel.Extras extras(Player player, PlayerState state, ClimateReading reading, double seconds, long now) {
        Surroundings around = plugin.surroundings();
        boolean inWater = reading.inWater();
        Location feet = player.getLocation();
        World world = player.getWorld();
        // Open to the sky: nothing that stops rain above the head.
        boolean open = !inWater && world.getHighestBlockYAt(feet.getBlockX(), feet.getBlockZ(), HeightMap.MOTION_BLOCKING)
                <= feet.getBlockY();
        double rain = Surroundings.rainOn(reading.weather(), open, around.wetness());
        long time = world.getTime();
        boolean inSun = open && rain == 0.0 && !world.hasStorm() && (time < 12_300L || time > 23_850L)
                && world.getEnvironment() == World.Environment.NORMAL;

        state.fireF = around.fires().enabled() ? fireWarmthF(player, around.fires()) : 0.0;
        state.wetness = Surroundings.wetness(state.wetness, inWater, rain, reading.feelsLikeF(), state.fireF > 3.0,
                inSun, seconds, around.wetness());
        boolean working = player.isSprinting() || player.isSwimming() || now - state.minedAt < 60L;
        state.exertion = Surroundings.exertion(state.exertion, working, seconds, around.exertion());
        state.armorF = around.armor().enabled() && inSun ? metalPieces(player) * around.armor().sunFPerPiece() : 0.0;

        double meal = around.food().enabled() && now < state.mealUntil ? state.mealF : 0.0;
        double wet = inWater ? 0.0 : state.wetness;
        Surroundings.Metabolism metabolism = around.metabolism();
        state.hungry = metabolism.enabled() && player.getFoodLevel() <= metabolism.hungryAt();
        Surroundings.Wetness wetting = around.wetness();
        // Back from the cold faster by a fire or after a warm meal; from the heat in water or after a cool drink.
        double rewarm = 1.0 + state.fireF * around.fires().rewarmPerF() + (meal > 0 ? 1.0 : 0.0);
        double recool = (inWater ? plugin.settings().rates().waterFactor() : 1.0) + (meal < 0 ? 1.0 : 0.0);
        return new BodyModel.Extras(state.exertion * around.exertion().mostF(), state.armorF,
                wet * wetting.colderF(), wet * wetting.coolerF(),
                (1.0 + wet * wetting.coldRateBonus()) * (state.hungry ? 1.0 + metabolism.hungryColdRate() : 1.0),
                Math.max(0.0, meal), Math.max(0.0, -meal), rewarm, recool, inWater ? 0.0 : state.fireF);
    }

    /** The warmth of campfires, fire, lava and magma round the body, nearest counting most. */
    private static double fireWarmthF(Player player, Surroundings.Fires fires) {
        Location at = player.getLocation();
        World world = player.getWorld();
        int radius = fires.radius(), bx = at.getBlockX(), by = at.getBlockY(), bz = at.getBlockZ();
        double total = 0.0;
        for (int dx = -radius; dx <= radius; dx++)
            for (int dy = -2; dy <= 2; dy++)
                for (int dz = -radius; dz <= radius; dz++) {
                    org.bukkit.block.Block block = world.getBlockAt(bx + dx, by + dy, bz + dz);
                    Double warmth = fires.warmthF().get(block.getType().getKey().getKey());
                    if (warmth == null) continue;
                    // An unlit campfire gives nothing.
                    if (block.getBlockData() instanceof org.bukkit.block.data.Lightable lit && !lit.isLit()) continue;
                    total += Surroundings.fireWarmthF(warmth, Math.sqrt(dx * dx + dy * dy + dz * dz), radius);
                    if (total >= fires.mostF()) return fires.mostF();
                }
        return total;
    }

    /** Metal armor pieces worn: iron, chainmail, gold, copper, diamond or netherite. */
    private static int metalPieces(Player player) {
        int pieces = 0;
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (item == null) continue;
            String name = item.getType().name();
            boolean armor = name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS")
                    || name.endsWith("_BOOTS");
            boolean metal = name.startsWith("IRON_") || name.startsWith("CHAINMAIL_") || name.startsWith("GOLDEN_")
                    || name.startsWith("COPPER_") || name.startsWith("DIAMOND_") || name.startsWith("NETHERITE_");
            if (armor && metal) pieces++;
        }
        return pieces;
    }

    /** A puff of breath in front of the face in cold air, every few seconds. */
    private void breathe(Player player, PlayerState state, ClimateReading reading, double seconds) {
        Surroundings.Breath breath = plugin.surroundings().breath();
        if (!breath.enabled() || reading.inWater() || !(reading.temperatureF() <= breath.belowF())) return;
        state.breathTimer -= seconds;
        if (state.breathTimer > 0) return;
        state.breathTimer = breath.everySeconds() * (0.8 + plugin.random().nextDouble() * 0.4);
        Location eye = player.getEyeLocation();
        org.bukkit.util.Vector ahead = eye.getDirection().setY(0);
        if (ahead.lengthSquared() < 1.0e-6) ahead = new org.bukkit.util.Vector(0, 0, 1);
        ahead.normalize();
        Location mouth = eye.clone().add(ahead.clone().multiply(0.3)).subtract(0, 0.12, 0);
        // White smoke drifts and fades by itself; sent a little forward and mostly up, it rises and vanishes
        // (a count of 0 makes the offsets its direction and the last number its speed).
        for (int puff = 0; puff < 2; puff++) {
            double spread = (plugin.random().nextDouble() - 0.5) * 0.2;
            player.getWorld().spawnParticle(Particle.WHITE_SMOKE, mouth, 0,
                    ahead.getX() * 0.35 + spread, 1.0, ahead.getZ() * 0.35 - spread, 0.025);
        }
    }

    /** Leather pieces worn, as degrees of cold tolerance. */
    private static double coldResistanceF(Player player, ExposureSettings settings) {
        PlayerInventory inventory = player.getInventory();
        return settings.coldResistance().resistanceF(leather(inventory.getHelmet(), Material.LEATHER_HELMET),
                leather(inventory.getChestplate(), Material.LEATHER_CHESTPLATE),
                leather(inventory.getLeggings(), Material.LEATHER_LEGGINGS),
                leather(inventory.getBoots(), Material.LEATHER_BOOTS));
    }

    private static boolean leather(ItemStack item, Material material) {
        return item != null && item.getType() == material;
    }

    private void apply(Player player, PlayerState state, Stage stage, SicknessModel.Illness illness, double seconds,
                       ExposureSettings settings) {
        Map<PotionEffectType, Integer> effects = new HashMap<>();
        switch (stage) {
            case HYPOTHERMIA, HEAT_EXHAUSTION -> effects.put(PotionEffectType.SLOWNESS, 0);
            case SEVERE_HYPOTHERMIA, FREEZING -> {
                effects.put(PotionEffectType.SLOWNESS, 1);
                effects.put(PotionEffectType.WEAKNESS, 0);
                effects.put(PotionEffectType.MINING_FATIGUE, 0);
            }
            case SEVERE_HEAT, HEATSTROKE -> {
                effects.put(PotionEffectType.SLOWNESS, 0);
                effects.put(PotionEffectType.WEAKNESS, 0);
                effects.put(PotionEffectType.MINING_FATIGUE, 0);
            }
            default -> { }
        }
        if (illness != null) {
            effects.merge(PotionEffectType.SLOWNESS, illness.severity() == SicknessModel.Severity.SEVERE ? 1 : 0, Math::max);
            // Nausea comes in bursts, the first one a full interval after falling ill.
            double interval = SicknessModel.nauseaEveryMinutes(illness.severity(), settings.sickness()) * 60.0;
            if (Double.isNaN(state.nauseaTimer)) state.nauseaTimer = interval;
            state.nauseaTimer -= seconds;
            if (state.nauseaTimer <= 0) {
                effect(player, PotionEffectType.NAUSEA, 0, NAUSEA_TICKS);
                state.nauseaTimer = interval;
            }
        } else {
            state.nauseaTimer = Double.NaN;
        }
        effects.forEach((type, amplifier) -> effect(player, type, amplifier, EFFECT_TICKS));

        // Sweat: drops falling from around the head, more the hotter it gets.
        if (stage.hot() && plugin.getConfig().getBoolean("sweat.enabled", true)
                && player.getLocation().getBlock().getType() != Material.WATER) sweat(player, stage.severity());

        // Heat exhaustion and worse make you hungry faster.
        if (stage.hot() && stage.severity() >= 2) player.setExhaustion(player.getExhaustion() + (float) (0.1 * seconds));

        // The game's own frost overlay, kept just short of where vanilla would start freezing damage.
        if (stage.cold()) {
            int frost = switch (stage) {
                case CHILLED -> 60;
                case HYPOTHERMIA -> 100;
                default -> player.getMaxFreezeTicks() - 1;
            };
            player.setFreezeTicks(Math.max(player.getFreezeTicks(), Math.min(player.getMaxFreezeTicks() - 1, frost)));
        }

        if (stage == Stage.FREEZING || stage == Stage.HEATSTROKE) {
            // The world closing in: about to die of it (EZclimates 0.2.0).
            if (plugin.surroundings().darkness()) effect(player, PotionEffectType.DARKNESS, 0, DARKNESS_TICKS);
            state.damageTimer += seconds;
            ExposureSettings.Damage damage = settings.damage();
            while (state.damageTimer >= damage.intervalSeconds()) {
                state.damageTimer -= damage.intervalSeconds();
                hurt(player, stage == Stage.FREEZING, damage);
            }
        } else {
            state.damageTimer = 0;
        }
    }

    /** A few drops falling from around the head; everyone nearby sees them, and the player in third person. */
    private static void sweat(Player player, int drops) {
        Location head = player.getEyeLocation().subtract(0, 0.15, 0);
        player.getWorld().spawnParticle(Particle.FALLING_WATER, head, drops, 0.22, 0.12, 0.22, 0.0);
    }

    private static void hurt(Player player, boolean cold, ExposureSettings.Damage damage) {
        double amount = damage.amount();
        if (!damage.lethal()) amount = Math.min(amount, player.getHealth() - damage.floorHealth());
        if (amount <= 0) return;
        // Freezing reads "froze to death"; heatstroke "died from dehydration".
        DamageSource source = DamageSource.builder(cold ? DamageType.FREEZE : DamageType.DRY_OUT).build();
        player.damage(amount, source);
    }

    private static void effect(Player player, PotionEffectType type, int amplifier, int ticks) {
        player.addPotionEffect(new PotionEffect(type, ticks, amplifier, true, false, true));
    }

    private void actionBar(Player player, PlayerState state, Stage stage, SicknessModel.Illness illness) {
        if (stage == Stage.NORMAL && illness == null) return;
        NamedTextColor color = stage.cold() ? NamedTextColor.AQUA : stage.hot() ? NamedTextColor.GOLD : NamedTextColor.YELLOW;
        Component bar = Component.text("Body " + plugin.degrees(state.bodyF), color);
        if (stage != Stage.NORMAL) bar = bar.append(Component.text(" · " + stage.displayName(), color));
        String wet = Surroundings.wetWord(state.wetness);
        if (!wet.isEmpty()) bar = bar.append(Component.text(" · " + wet, NamedTextColor.BLUE));
        if (illness != null)
            bar = bar.append(Component.text(" · " + capitalised(illness.severity().displayName()) + " fever",
                    NamedTextColor.YELLOW));
        player.sendActionBar(bar);
    }

    private static String capitalised(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static String warning(Stage stage) {
        return switch (stage) {
            case CHILLED -> "You're getting chilled. Find warmth, or put on leather.";
            case HYPOTHERMIA -> "Hypothermia is setting in.";
            case SEVERE_HYPOTHERMIA -> "You're dangerously cold.";
            case FREEZING -> "You're freezing to death!";
            case OVERHEATING -> "You're overheating. Find shade or cool air.";
            case HEAT_EXHAUSTION -> "Heat exhaustion is setting in.";
            case SEVERE_HEAT -> "The heat is overwhelming you.";
            case HEATSTROKE -> "Heatstroke!";
            case NORMAL -> "";
        };
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.state(event.getPlayer()).lastTick = -1;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.forget(event.getPlayer());
    }

    /** Death resets the body; an illness carries on. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        PlayerState state = plugin.state(event.getPlayer());
        state.bodyF = ExposureSettings.Thresholds.REST_F;
        state.stage = Stage.NORMAL;
        state.damageTimer = 0;
        state.wetness = 0;
        state.exertion = 0;
        state.mealUntil = 0;
        state.hungerFactor = 1.0;
    }

    /**
     * A water bottle helps a sick player get better sooner; warm food holds
     * the cold off for a while, and cool food (a water bottle too) the heat.
     */
    @EventHandler(ignoreCancelled = true)
    public void onDrink(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        String name = item.getType().getKey().getKey();
        if (item.getType() == Material.POTION) {
            if (!(item.getItemMeta() instanceof PotionMeta meta) || meta.getBasePotionType() != PotionType.WATER) return;
            plugin.sickness().drank(event.getPlayer());
            name = "water_bottle";
        }
        Surroundings.Food food = plugin.surroundings().food();
        if (!food.enabled()) return;
        Surroundings.Meal warm = food.warm().get(name), cool = food.cool().get(name);
        if (warm == null && cool == null) return;
        PlayerState state = plugin.state(event.getPlayer());
        Surroundings.Meal meal = warm != null ? warm : cool;
        state.mealF = warm != null ? warm.f() : -cool.f();
        state.mealUntil = Bukkit.getCurrentTick() + (long) (meal.minutes() * 1200.0);
        state.mealName = name.replace('_', ' ');
    }

    /** In the cold, hunger goes faster: the body burns food to keep warm. */
    @EventHandler(ignoreCancelled = true)
    public void onHunger(org.bukkit.event.entity.EntityExhaustionEvent event) {
        if (!(event.getEntity() instanceof Player player) || !plugin.affected(player)) return;
        double factor = plugin.state(player).hungerFactor;
        if (factor > 1.0) event.setExhaustion((float) (event.getExhaustion() * factor));
    }

    /** Mining counts as work, which warms the body. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMine(org.bukkit.event.block.BlockBreakEvent event) {
        plugin.state(event.getPlayer()).minedAt = Bukkit.getCurrentTick();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBed(org.bukkit.event.player.PlayerBedEnterEvent event) {
        plugin.state(event.getPlayer()).sleptCold = false;
    }

    /** A night spent cold: the body wakes chilled, and it counts toward catching a cold. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWake(org.bukkit.event.player.PlayerBedLeaveEvent event) {
        Player player = event.getPlayer();
        PlayerState state = plugin.state(player);
        Surroundings.Sleep sleep = plugin.surroundings().sleep();
        boolean cold = state.sleptCold;
        state.sleptCold = false;
        if (!cold || !sleep.enabled() || !plugin.affected(player)) return;
        state.bodyF = Math.min(state.bodyF, plugin.settings().thresholds().chilledF() - 0.3);
        state.coldSeconds += sleep.coldMinutes() * 60.0;
        player.sendMessage(Component.text("You slept in the cold and woke up chilled. A warmer room, a fire nearby"
                + " or leather would have helped.", NamedTextColor.AQUA));
    }
}
