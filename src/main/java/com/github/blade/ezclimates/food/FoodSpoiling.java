package com.github.blade.ezclimates.food;

import com.github.blade.hvac.api.EzvacApi;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Food that spoils (EZclimates 0.3.0): kept on each stack and looked at
 * lazily, in players' inventories every 30 seconds by the air around them,
 * and in a container whenever it is opened or shut, by how cold it is
 * inside (an EZvac fridge's or freezer's inside, through API 1.12, else the
 * air there). Stale food feeds less; spoiled food feeds less still and can
 * make you sick; half a life later it turns into what it spoils into.
 * Main thread only.
 */
public final class FoodSpoiling implements Listener {
    /** How often players' own food is looked at, in ticks. */
    static final long SWEEP_TICKS = 600L;

    private final Plugin plugin;
    private final EzvacApi ezvac;
    private FoodSettings settings;
    private final NamespacedKey spoilAt, rateStep, lore;
    /** EZvac's storageTemperatureF (API 1.12), looked up once; null on an older EZvac. */
    private final Method storage;
    /** And its slot by slot form, for a mini fridge's chiller box; null where EZvac hasn't it. */
    private final Method slotStorage;
    private boolean on;
    private BukkitTask sweep;

    public FoodSpoiling(Plugin plugin, EzvacApi ezvac, FoodSettings settings) {
        this.plugin = plugin;
        this.ezvac = ezvac;
        this.settings = settings;
        this.spoilAt = new NamespacedKey(plugin, "spoil_at");
        this.rateStep = new NamespacedKey(plugin, "spoil_rate");
        this.lore = new NamespacedKey(plugin, "spoil_lore");
        Method found;
        try {
            found = EzvacApi.class.getMethod("storageTemperatureF", World.class, int.class, int.class, int.class);
        } catch (NoSuchMethodException older) {
            found = null;
        }
        this.storage = found;
        Method bySlot;
        try {
            bySlot = EzvacApi.class.getMethod("storageTemperatureF", World.class, int.class, int.class, int.class,
                    int.class);
        } catch (NoSuchMethodException older) {
            bySlot = null;
        }
        this.slotStorage = bySlot;
    }

    public FoodSettings settings() { return settings; }

    public void setSettings(FoodSettings settings) { this.settings = settings; }

    public boolean on() { return on; }

    /** Switched in EZvac's Addons menu (Food). Off, food keeps what it had and nothing changes. */
    public void setOn(boolean on) {
        if (on == this.on) return;
        this.on = on;
        if (on) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            sweep = plugin.getServer().getScheduler().runTaskTimer(plugin, this::sweep, 40L, SWEEP_TICKS);
        } else {
            HandlerList.unregisterAll(this);
            if (sweep != null) sweep.cancel();
            sweep = null;
        }
    }

    /** One clock for every world, so food carried between them keeps its age. */
    static long clock() {
        List<World> worlds = Bukkit.getWorlds();
        return worlds.isEmpty() ? 0L : worlds.get(0).getFullTime();
    }

    /** Players whose food spoils: not in creative or spectator. */
    static boolean affected(Player player) {
        return player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR;
    }

    /** The air around a player, from EZvac. */
    double airAt(Location location) {
        return ezvac.readingAt(location).temperatureF();
    }

    /** How cold it is inside a container at a block: a fridge's inside through API 1.12, else the air there. */
    public double storageAt(Location block) {
        if (storage != null) {
            try {
                return (double) storage.invoke(ezvac, block.getWorld(), block.getBlockX(), block.getBlockY(),
                        block.getBlockZ());
            } catch (ReflectiveOperationException | RuntimeException failed) {
                // Fall back to the air there.
            }
        }
        return airAt(block.clone().add(0.5, 0.5, 0.5));
    }

    private void sweep() {
        long now = clock();
        for (Player player : plugin.getServer().getOnlinePlayers())
            if (affected(player)) keepAll(player.getInventory(), airAt(player.getLocation()), now);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (affected(event.getPlayer()))
            keepAll(event.getPlayer().getInventory(), airAt(event.getPlayer().getLocation()), clock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        kept(event.getInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        // Food just put in starts keeping at the container's cold from now on.
        kept(event.getInventory());
    }

    private void kept(Inventory inventory) {
        if (inventory.getHolder(false) instanceof Player) return;
        Location location = inventory.getLocation();
        if (location == null || location.getWorld() == null) return;
        double everywhere = storageAt(location);
        if (slotStorage == null) {
            keepAll(inventory, everywhere, clock());
            return;
        }
        // Slot by slot: the same all through but for a mini fridge's chiller box, a little colder.
        long now = clock();
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType().isAir()) continue;
            double here = everywhere;
            try {
                double found = (double) slotStorage.invoke(ezvac, location.getWorld(), location.getBlockX(),
                        location.getBlockY(), location.getBlockZ(), slot);
                if (Double.isFinite(found)) here = found;
            } catch (ReflectiveOperationException | RuntimeException failed) {
                // The container's temperature, then.
            }
            ItemStack kept = keep(item, here, now);
            if (kept != item) inventory.setItem(slot, kept);
        }
    }

    /** Every food stack in an inventory, looked at again where it is kept now. */
    public void keepAll(Inventory inventory, double temperatureF, long now) {
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType().isAir()) continue;
            ItemStack kept = keep(item, temperatureF, now);
            if (kept != item) inventory.setItem(slot, kept);
        }
    }

    /**
     * A stack looked at again, kept at this temperature from now on: fresh
     * when first seen, its life carried over otherwise, its line on the item
     * written again. Gone, it is what it spoils into (or nothing).
     *
     * @return the same stack, changed in place, or what replaces it
     */
    public ItemStack keep(ItemStack item, double temperatureF, long now) {
        FoodSettings.Food food = settings.food(item.getType().getKey().getKey());
        if (food == null) return item;
        double life = food.lifeRoomTicks(settings.pace());
        double rate = food.rate(temperatureF);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        PersistentDataContainer data = meta.getPersistentDataContainer();
        Long at = data.get(spoilAt, PersistentDataType.LONG);
        Integer step = data.get(rateStep, PersistentDataType.INTEGER);
        Freshness.Kept kept;
        if (at == null || step == null) kept = Freshness.fresh(life, now, rate);
        else if (Math.abs(Freshness.step(rate) - step) <= 1) kept = new Freshness.Kept(at, step);
        // A step either way (a few degrees) is left as it was: each re-anchoring rounds, and a player walking
        // in and out of the shade would otherwise lose a little life every time.
        else kept = Freshness.reanchor(new Freshness.Kept(at, step), life, now, rate);
        Freshness.Stage stage = Freshness.stage(Freshness.leftRoomTicks(kept, now), life);
        if (stage == Freshness.Stage.GONE) return spoiledInto(food, item.getAmount());
        data.set(spoilAt, PersistentDataType.LONG, kept.spoilAt());
        data.set(rateStep, PersistentDataType.INTEGER, kept.rateStep());
        writeLine(meta, data, line(stage, Freshness.daysLeft(kept, now)));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack spoiledInto(FoodSettings.Food food, int amount) {
        Material into = food.into() == null ? null : Material.matchMaterial(food.into());
        return into == null || into.isAir() ? new ItemStack(Material.AIR) : new ItemStack(into, Math.min(amount, into.getMaxStackSize()));
    }

    /** Whether a lore line is one of its own: {@code Fresh · ...}, {@code Stale · ...} or {@code Spoiled}. */
    static boolean isFreshnessLine(String plain) {
        return plain.startsWith("Fresh · ") || plain.startsWith("Stale · ") || plain.equals("Stale")
                || plain.startsWith("Spoiled");
    }

    /** {@code Fresh · 3 days}, {@code Stale · 1 day}, or {@code Spoiled}, coloured. */
    static Component line(Freshness.Stage stage, double daysLeft) {
        String days = daysLeft < 1.0 ? "under a day" : Math.round(daysLeft) == 1 ? "1 day" : Math.round(daysLeft) + " days";
        Component text = switch (stage) {
            case FRESH -> Component.text("Fresh · " + days, NamedTextColor.GREEN);
            case STALE -> Component.text("Stale · " + days, NamedTextColor.YELLOW);
            case SPOILED, GONE -> Component.text("Spoiled", NamedTextColor.RED);
        };
        return text.decoration(TextDecoration.ITALIC, false);
    }

    /** Its freshness as a lore line, replacing the one it wrote before and nothing else. */
    private void writeLine(ItemMeta meta, PersistentDataContainer data, Component line) {
        List<Component> lines = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        // Its own line only, found by what it says: another plugin's line after it (EZvac's frosted over) stays.
        if (data.has(lore, PersistentDataType.BYTE))
            lines.removeIf(old -> isFreshnessLine(PlainTextComponentSerializer.plainText().serialize(old)));
        lines.add(line);
        meta.lore(lines);
        data.set(lore, PersistentDataType.BYTE, (byte) 1);
    }

    /** How fresh a stack is right now, kept as it was: for eating and /ezclimates food. */
    public Freshness.Stage stage(ItemStack item, long now) {
        FoodSettings.Food food = item == null ? null : settings.food(item.getType().getKey().getKey());
        if (food == null || item.getItemMeta() == null) return null;
        PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
        Long at = data.get(spoilAt, PersistentDataType.LONG);
        Integer step = data.get(rateStep, PersistentDataType.INTEGER);
        if (at == null || step == null) return Freshness.Stage.FRESH;
        return Freshness.stage(Freshness.leftRoomTicks(new Freshness.Kept(at, step), now),
                food.lifeRoomTicks(settings.pace()));
    }

    /** Stale food feeds less, spoiled food less still, and spoiled food can make you sick. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEat(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player) || !affected(player)) return;
        ItemStack item = event.getItem();
        Freshness.Stage stage = stage(item, clock());
        if (stage == null || stage == Freshness.Stage.FRESH) return;
        int before = player.getFoodLevel(), gained = event.getFoodLevel() - before;
        if (gained <= 0) return;
        boolean spoiled = stage == Freshness.Stage.SPOILED || stage == Freshness.Stage.GONE;
        double share = spoiled ? settings.spoiledFeeds() : settings.staleFeeds();
        event.setFoodLevel(before + (int) Math.round(gained * share));
        if (!spoiled) return;
        if (settings.spoiledHungerSeconds() > 0)
            player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, settings.spoiledHungerSeconds() * 20, 0));
        if (ThreadLocalRandom.current().nextDouble() < settings.spoiledNauseaChance())
            player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 200, 0));
        player.sendMessage(Component.text("That " + item.getType().getKey().getKey().replace('_', ' ')
                + " had gone off.", NamedTextColor.RED));
    }

    /** {@code /ezclimates food}: the food in a player's hand, and how fast it is going where they stand. */
    public List<String> describe(Player player) {
        List<String> lines = new ArrayList<>();
        ItemStack item = player.getInventory().getItemInMainHand();
        FoodSettings.Food food = item.getType().isAir() ? null : settings.food(item.getType().getKey().getKey());
        if (food == null) {
            lines.add(item.getType().isAir() ? "Hold some food to see how fresh it is."
                    : item.getType().getKey().getKey().replace('_', ' ') + " never spoils.");
            return lines;
        }
        double air = airAt(player.getLocation());
        long now = clock();
        ItemStack kept = keep(item, air, now);
        if (kept != item) player.getInventory().setItemInMainHand(kept);
        Freshness.Stage stage = stage(kept, now);
        lines.add(item.getType().getKey().getKey().replace('_', ' ') + ": "
                + (stage == null ? "gone" : stage.name().toLowerCase(Locale.ROOT)) + ".");
        lines.add(String.format(Locale.ROOT, "Here (%.0f F) it goes %.1f times as fast as at 70 F; it keeps %.0f days"
                        + " at 70 F, %.0f times longer in a fridge and %.0f in a freezer.", air, food.rate(air),
                food.days() * settings.pace(), food.fridge(), food.freezer()));
        return lines;
    }
}
