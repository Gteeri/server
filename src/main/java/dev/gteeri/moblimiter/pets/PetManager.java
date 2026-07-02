package dev.gteeri.moblimiter.pets;

import dev.gteeri.moblimiter.MobLimiterPlugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Persistent per-player pet ledger (pets.yml): +1 on tame, -1 only when an
 * owned (PDC-tagged) pet dies. Vanilla has no "release pet" mechanic, so the
 * counter cannot be reset by tricks; the tag survives owner being offline.
 */
public final class PetManager {

    public record TameCheck(boolean allowed, String messageKey, int count, int limit) {
    }

    private final MobLimiterPlugin plugin;
    private final NamespacedKey ownerKey;
    private final Map<UUID, Map<String, Integer>> counts = new ConcurrentHashMap<>();
    private final Object ioLock = new Object();
    private volatile boolean dirty;
    private volatile ScheduledTask saveTask;
    private File file;

    public PetManager(MobLimiterPlugin plugin) {
        this.plugin = plugin;
        this.ownerKey = new NamespacedKey(plugin, "pet_owner");
    }

    public NamespacedKey ownerKey() {
        return ownerKey;
    }

    public void start() {
        this.file = new File(plugin.getDataFolder(), "pets.yml");
        load();
        this.saveTask = Bukkit.getAsyncScheduler().runAtFixedRate(plugin,
                task -> {
                    if (dirty) {
                        save();
                    }
                }, 60L, 60L, TimeUnit.SECONDS);
    }

    public void stop() {
        ScheduledTask task = saveTask;
        if (task != null) {
            task.cancel();
        }
        save();
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String uuidRaw : yml.getKeys(false)) {
            UUID owner;
            try {
                owner = UUID.fromString(uuidRaw);
            } catch (IllegalArgumentException e) {
                continue;
            }
            ConfigurationSection section = yml.getConfigurationSection(uuidRaw);
            if (section == null) {
                continue;
            }
            Map<String, Integer> perType = new ConcurrentHashMap<>();
            for (String type : section.getKeys(false)) {
                int value = section.getInt(type);
                if (value > 0) {
                    perType.put(type.toUpperCase(Locale.ROOT), value);
                }
            }
            if (!perType.isEmpty()) {
                counts.put(owner, perType);
            }
        }
    }

    public void save() {
        synchronized (ioLock) {
            YamlConfiguration yml = new YamlConfiguration();
            for (Map.Entry<UUID, Map<String, Integer>> entry : counts.entrySet()) {
                for (Map.Entry<String, Integer> typeEntry : entry.getValue().entrySet()) {
                    if (typeEntry.getValue() > 0) {
                        yml.set(entry.getKey() + "." + typeEntry.getKey(), typeEntry.getValue());
                    }
                }
            }
            try {
                yml.save(file);
                dirty = false;
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to save pets.yml: " + e.getMessage());
            }
        }
    }

    public int total(UUID owner) {
        Map<String, Integer> perType = counts.get(owner);
        if (perType == null) {
            return 0;
        }
        int sum = 0;
        for (int value : perType.values()) {
            sum += value;
        }
        return sum;
    }

    public int ofType(UUID owner, EntityType type) {
        Map<String, Integer> perType = counts.get(owner);
        if (perType == null) {
            return 0;
        }
        return perType.getOrDefault(type.name(), 0);
    }

    public Map<String, Integer> snapshot(UUID owner) {
        Map<String, Integer> perType = counts.get(owner);
        return perType == null ? Map.of() : Map.copyOf(perType);
    }

    public TameCheck canTame(UUID owner, EntityType type) {
        var cfg = plugin.cfg();
        Integer typeLimit = cfg.petTypeLimits.get(type);
        if (typeLimit != null && typeLimit >= 0) {
            int current = ofType(owner, type);
            if (current >= typeLimit) {
                return new TameCheck(false, "pet-limit-type", current, typeLimit);
            }
        }
        if (cfg.petTotalLimit >= 0) {
            int current = total(owner);
            if (current >= cfg.petTotalLimit) {
                return new TameCheck(false, "pet-limit-total", current, cfg.petTotalLimit);
            }
        }
        return new TameCheck(true, null, 0, 0);
    }

    public void increment(UUID owner, EntityType type) {
        counts.computeIfAbsent(owner, o -> new ConcurrentHashMap<>())
                .merge(type.name(), 1, Integer::sum);
        dirty = true;
    }

    public void decrement(UUID owner, EntityType type) {
        Map<String, Integer> perType = counts.get(owner);
        if (perType == null) {
            return;
        }
        perType.computeIfPresent(type.name(), (key, value) -> value <= 1 ? null : value - 1);
        dirty = true;
    }
}
