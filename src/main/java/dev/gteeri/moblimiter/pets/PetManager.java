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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Persistent per-player pet ledger (pets.yml).
 *
 * Tracks the concrete entity UUIDs of tamed pets (owner -> type -> ids), not
 * bare counters. This makes registration idempotent and lets the ledger
 * self-heal: every pet also carries a PDC owner tag, and tagged pets are
 * re-registered whenever their chunk loads, so a lost or outdated pets.yml
 * rebuilds itself automatically.
 *
 * Every mutation immediately schedules an async save (plus the periodic
 * fallback save and the on-disable save), so an ungraceful server stop loses
 * at most the very last in-flight write.
 */
public final class PetManager {

    public record TameCheck(boolean allowed, String messageKey, int count, int limit) {
    }

    private final MobLimiterPlugin plugin;
    private final NamespacedKey ownerKey;
    /** owner -> entity type name -> set of pet entity UUIDs. */
    private final Map<UUID, Map<String, Set<UUID>>> pets = new ConcurrentHashMap<>();
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
            Map<String, Set<UUID>> perType = new ConcurrentHashMap<>();
            for (String type : section.getKeys(false)) {
                Set<UUID> ids = ConcurrentHashMap.newKeySet();
                for (String raw : section.getStringList(type)) {
                    try {
                        ids.add(UUID.fromString(raw));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                // Legacy format stored bare counts; those cannot be mapped to
                // concrete pets and are skipped. The ledger self-heals from
                // the PDC tags when the pets' chunks load.
                if (!ids.isEmpty()) {
                    perType.put(type.toUpperCase(Locale.ROOT), ids);
                }
            }
            if (!perType.isEmpty()) {
                pets.put(owner, perType);
            }
        }
    }

    public void save() {
        synchronized (ioLock) {
            YamlConfiguration yml = new YamlConfiguration();
            for (Map.Entry<UUID, Map<String, Set<UUID>>> entry : pets.entrySet()) {
                for (Map.Entry<String, Set<UUID>> typeEntry : entry.getValue().entrySet()) {
                    if (!typeEntry.getValue().isEmpty()) {
                        List<String> ids = new ArrayList<>();
                        for (UUID id : typeEntry.getValue()) {
                            ids.add(id.toString());
                        }
                        yml.set(entry.getKey() + "." + typeEntry.getKey(), ids);
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

    /** Fire-and-forget async save, triggered right after every ledger mutation. */
    private void saveSoon() {
        try {
            Bukkit.getAsyncScheduler().runNow(plugin, task -> save());
        } catch (IllegalStateException e) {
            // Plugin is disabling/scheduler unavailable; the on-disable save() covers this case.
        }
    }

    public int total(UUID owner) {
        Map<String, Set<UUID>> perType = pets.get(owner);
        if (perType == null) {
            return 0;
        }
        int sum = 0;
        for (Set<UUID> ids : perType.values()) {
            sum += ids.size();
        }
        return sum;
    }

    /** All pets tracked across every player (for the stats GUI). */
    public int globalTotal() {
        int sum = 0;
        for (Map<String, Set<UUID>> perType : pets.values()) {
            for (Set<UUID> ids : perType.values()) {
                sum += ids.size();
            }
        }
        return sum;
    }

    /** Number of players that own at least one tracked pet. */
    public int ownersTracked() {
        return pets.size();
    }

    public int ofType(UUID owner, EntityType type) {
        Map<String, Set<UUID>> perType = pets.get(owner);
        if (perType == null) {
            return 0;
        }
        Set<UUID> ids = perType.get(type.name());
        return ids == null ? 0 : ids.size();
    }

    public Map<String, Integer> snapshot(UUID owner) {
        Map<String, Set<UUID>> perType = pets.get(owner);
        if (perType == null) {
            return Map.of();
        }
        Map<String, Integer> result = new HashMap<>();
        for (Map.Entry<String, Set<UUID>> entry : perType.entrySet()) {
            result.put(entry.getKey(), entry.getValue().size());
        }
        return Map.copyOf(result);
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

    /** Idempotent: also used to self-heal from PDC tags on chunk load. */
    public void register(UUID owner, EntityType type, UUID petId) {
        Set<UUID> ids = pets.computeIfAbsent(owner, o -> new ConcurrentHashMap<>())
                .computeIfAbsent(type.name(), t -> ConcurrentHashMap.newKeySet());
        if (ids.add(petId)) {
            dirty = true;
            saveSoon();
        }
    }

    /** Remove a pet by its entity id (type looked up across the owner's sets). */
    public void unregister(UUID owner, UUID petId) {
        Map<String, Set<UUID>> perType = pets.get(owner);
        if (perType == null) {
            return;
        }
        boolean removed = false;
        var iterator = perType.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Set<UUID>> entry = iterator.next();
            if (entry.getValue().remove(petId)) {
                removed = true;
            }
            if (entry.getValue().isEmpty()) {
                iterator.remove();
            }
        }
        if (perType.isEmpty()) {
            pets.remove(owner, perType);
        }
        if (removed) {
            dirty = true;
            saveSoon();
        }
    }
}
