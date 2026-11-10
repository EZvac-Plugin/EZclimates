package com.github.blade.ezclimates.body;

import com.github.blade.hvac.api.ClimateReading;
import com.github.blade.hvac.api.event.PlayerClimateUpdateEvent;
import com.github.blade.ezclimates.body.model.BodyModel;
import com.github.blade.ezclimates.body.model.ExposureSettings;
import com.github.blade.ezclimates.body.model.SicknessModel;
import com.github.blade.ezclimates.body.model.Stage;
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
        BodyModel.Conditions conditions = new BodyModel.Conditions(feelsF, reading.inWater(),
                coldResistanceF(player, settings), player.hasPotionEffect(PotionEffectType.FIRE_RESISTANCE),
                restF, heatMultiplier);
        state.bodyF = BodyModel.step(state.bodyF, conditions, seconds, settings);

        state.daySeconds += seconds;
        if (BodyModel.exposedToCold(feelsF, settings.comfort()) || state.bodyF < settings.thresholds().chilledF())
            state.coldSeconds += seconds;

        Stage stage = Stage.of(state.bodyF, settings.thresholds());
        if (stage.worseThan(state.stage)) {
            player.sendMessage(Component.text(warning(stage), stage.cold() ? NamedTextColor.AQUA : NamedTextColor.GOLD));
            if (stage == Stage.SEVERE_HEAT) effect(player, PotionEffectType.NAUSEA, 0, NAUSEA_TICKS);
        }
        state.stage = stage;
        apply(player, state, stage, illness, seconds, settings);
        actionBar(player, state, stage, illness);
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
    }

    /** A water bottle helps a sick player get better sooner. */
    @EventHandler(ignoreCancelled = true)
    public void onDrink(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (item.getType() != Material.POTION || !(item.getItemMeta() instanceof PotionMeta meta)) return;
        if (meta.getBasePotionType() == PotionType.WATER) plugin.sickness().drank(event.getPlayer());
    }
}
