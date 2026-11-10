package com.github.blade.ezclimates.body;

import com.github.blade.ezclimates.body.model.ExposureSettings;
import com.github.blade.ezclimates.body.model.SicknessModel;
import com.github.blade.ezclimates.body.model.Stage;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * One player's body and illness. Kept in memory while they're online and in
 * their own persistent data, so relogs and restarts keep it. Main thread only.
 */
final class PlayerState {
    double bodyF = ExposureSettings.Thresholds.REST_F;
    Stage stage = Stage.NORMAL;
    /** Server tick of the last step; steps measure the real time between updates. */
    long lastTick = -1;
    double damageTimer;
    SicknessModel.Illness illness;
    double nauseaTimer = Double.NaN;
    /** Today's tally, for tomorrow's sickness roll. */
    double daySeconds;
    double coldSeconds;
    double contactSeconds;

    /** The persistent-data keys, made once per plugin. */
    record Keys(NamespacedKey body, NamespacedKey severity, NamespacedKey days, NamespacedKey water,
                NamespacedKey daySeconds, NamespacedKey coldSeconds, NamespacedKey contactSeconds) {
        static Keys of(Plugin plugin) {
            return in(plugin.getName().toLowerCase(java.util.Locale.ROOT));
        }

        /** What LiveExposure kept, before it became part of EZclimates: carried over once. */
        static Keys legacy() {
            return in("liveexposure");
        }

        private static Keys in(String namespace) {
            return new Keys(new NamespacedKey(namespace, "body_f"), new NamespacedKey(namespace, "sick_severity"),
                    new NamespacedKey(namespace, "sick_days_left"), new NamespacedKey(namespace, "sick_water_saved"),
                    new NamespacedKey(namespace, "day_seconds"), new NamespacedKey(namespace, "cold_seconds"),
                    new NamespacedKey(namespace, "contact_seconds"));
        }

        /** Takes everything kept under these keys off a player. */
        void clear(Player player) {
            PersistentDataContainer data = player.getPersistentDataContainer();
            for (NamespacedKey key : new NamespacedKey[] {body, severity, days, water, daySeconds, coldSeconds, contactSeconds})
                data.remove(key);
        }
    }

    /**
     * A player's state, from what LiveExposure kept for them when EZclimates
     * has nothing of its own yet: a body temperature and an illness carry
     * over the first time they join, and LiveExposure's copy is removed.
     */
    static PlayerState loadCarryingOver(Player player, Keys keys) {
        Keys legacy = Keys.legacy();
        PersistentDataContainer data = player.getPersistentDataContainer();
        if (data.has(keys.body(), PersistentDataType.DOUBLE) || !data.has(legacy.body(), PersistentDataType.DOUBLE))
            return load(player, keys);
        PlayerState state = load(player, legacy);
        legacy.clear(player);
        state.save(player, keys);
        return state;
    }

    static PlayerState load(Player player, Keys keys) {
        PersistentDataContainer data = player.getPersistentDataContainer();
        PlayerState state = new PlayerState();
        Double body = data.get(keys.body(), PersistentDataType.DOUBLE);
        if (body != null && Double.isFinite(body)) state.bodyF = body;
        SicknessModel.Severity severity = SicknessModel.Severity.parse(data.get(keys.severity(), PersistentDataType.STRING));
        Double days = data.get(keys.days(), PersistentDataType.DOUBLE);
        if (severity != null && days != null && days > 0) {
            Double water = data.get(keys.water(), PersistentDataType.DOUBLE);
            state.illness = new SicknessModel.Illness(severity, days, water == null ? 0.0 : water);
        }
        state.daySeconds = data.getOrDefault(keys.daySeconds(), PersistentDataType.DOUBLE, 0.0);
        state.coldSeconds = data.getOrDefault(keys.coldSeconds(), PersistentDataType.DOUBLE, 0.0);
        state.contactSeconds = data.getOrDefault(keys.contactSeconds(), PersistentDataType.DOUBLE, 0.0);
        return state;
    }

    void save(Player player, Keys keys) {
        PersistentDataContainer data = player.getPersistentDataContainer();
        data.set(keys.body(), PersistentDataType.DOUBLE, bodyF);
        if (illness == null) {
            data.remove(keys.severity());
            data.remove(keys.days());
            data.remove(keys.water());
        } else {
            data.set(keys.severity(), PersistentDataType.STRING, illness.severity().name());
            data.set(keys.days(), PersistentDataType.DOUBLE, illness.daysLeft());
            data.set(keys.water(), PersistentDataType.DOUBLE, illness.waterSaved());
        }
        data.set(keys.daySeconds(), PersistentDataType.DOUBLE, daySeconds);
        data.set(keys.coldSeconds(), PersistentDataType.DOUBLE, coldSeconds);
        data.set(keys.contactSeconds(), PersistentDataType.DOUBLE, contactSeconds);
    }

    /** A new day: the tally starts again. */
    void resetDay() {
        daySeconds = 0;
        coldSeconds = 0;
        contactSeconds = 0;
    }
}
