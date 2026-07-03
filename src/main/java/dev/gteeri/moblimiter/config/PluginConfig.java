package dev.gteeri.moblimiter.config;

import dev.gteeri.moblimiter.util.Categories;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Immutable snapshot of config.yml. Recreated on /moblimit reload. */
public final class PluginConfig {

    // freeze
    public final boolean freezeEnabled;
    public final int scanIntervalSeconds;
    public final double scanRadius;
    public final double densityRadius;
    public final int freezeThreshold;
    public final int unfreezeThreshold;
    public final int refreezeCooldownSeconds;
    public final int environmentDamageCooldownSeconds;
    public final boolean freezeVillagersWithProfession;
    public final boolean exemptNamed;
    public final boolean exemptTamed;
    public final boolean exemptLeashedByPlayer;
    public final boolean exemptInVehicleWithPlayer;
    public final boolean exemptWithEquipment;

    // limits
    public final boolean limitsEnabled;
    public final double limitRadius;
    public final double limitVerticalRadius;
    public final double limitCacheSeconds;
    public final Map<Categories.Category, Integer> categoryLimits;
    public final Set<CreatureSpawnEvent.SpawnReason> blockedSpawnReasons;

    // pets
    public final boolean petsEnabled;
    public final int petTotalLimit;
    public final Map<EntityType, Integer> petTypeLimits;

    // zones
    public final int zoneCellSize;
    public final int zoneOverloadedThreshold;
    public final int zoneCriticalThreshold;
    public final int zoneAlertCooldownSeconds;

    // messages
    public final int messageCooldownSeconds;
    public final String prefix;
    public final String denySound;
    public final String alertSound;
    private final Map<String, String> messages;

    public PluginConfig(JavaPlugin plugin) {
        FileConfiguration c = plugin.getConfig();

        this.freezeEnabled = c.getBoolean("freeze.enabled", true);
        this.scanIntervalSeconds = Math.max(1, c.getInt("freeze.scan-interval-seconds", 3));
        this.scanRadius = Math.max(8.0, c.getDouble("freeze.scan-radius", 48.0));
        this.densityRadius = Math.max(2.0, c.getDouble("freeze.density-radius", 10.0));
        this.freezeThreshold = Math.max(2, c.getInt("freeze.freeze-threshold", 25));
        this.unfreezeThreshold = Math.min(this.freezeThreshold - 1,
                Math.max(1, c.getInt("freeze.unfreeze-threshold", 18)));
        this.refreezeCooldownSeconds = Math.max(0, c.getInt("freeze.refreeze-cooldown-seconds", 5));
        this.environmentDamageCooldownSeconds =
                Math.max(0, c.getInt("freeze.environment-damage-cooldown-seconds", 10));
        this.freezeVillagersWithProfession = c.getBoolean("freeze.freeze-villagers-with-profession", true);
        this.exemptNamed = c.getBoolean("freeze.exempt.named", true);
        this.exemptTamed = c.getBoolean("freeze.exempt.tamed", true);
        this.exemptLeashedByPlayer = c.getBoolean("freeze.exempt.leashed-by-player", true);
        this.exemptInVehicleWithPlayer = c.getBoolean("freeze.exempt.in-vehicle-with-player", true);
        this.exemptWithEquipment = c.getBoolean("freeze.exempt.with-equipment", true);

        this.limitsEnabled = c.getBoolean("limits.enabled", true);
        this.limitRadius = Math.max(4.0, c.getDouble("limits.radius", 32.0));
        this.limitVerticalRadius = Math.max(2.0, c.getDouble("limits.vertical-radius", 16.0));
        this.limitCacheSeconds = Math.max(0.0, c.getDouble("limits.cache-seconds", 1.5));

        Map<Categories.Category, Integer> limits = new EnumMap<>(Categories.Category.class);
        ConfigurationSection categoriesSection = c.getConfigurationSection("limits.categories");
        if (categoriesSection != null) {
            for (String key : categoriesSection.getKeys(false)) {
                Categories.Category category = Categories.byKey(key);
                if (category == null) {
                    plugin.getLogger().warning("Unknown limit category in config: " + key);
                    continue;
                }
                limits.put(category, categoriesSection.getInt(key));
            }
        }
        this.categoryLimits = limits;

        Set<CreatureSpawnEvent.SpawnReason> reasons = EnumSet.noneOf(CreatureSpawnEvent.SpawnReason.class);
        for (String raw : c.getStringList("limits.blocked-spawn-reasons")) {
            try {
                reasons.add(CreatureSpawnEvent.SpawnReason.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Unknown spawn reason in config: " + raw);
            }
        }
        this.blockedSpawnReasons = reasons;

        this.petsEnabled = c.getBoolean("pets.enabled", true);
        this.petTotalLimit = c.getInt("pets.total-limit", 16);
        Map<EntityType, Integer> petLimits = new EnumMap<>(EntityType.class);
        ConfigurationSection petSection = c.getConfigurationSection("pets.per-type");
        if (petSection != null) {
            for (String key : petSection.getKeys(false)) {
                try {
                    petLimits.put(EntityType.valueOf(key.trim().toUpperCase(Locale.ROOT)),
                            petSection.getInt(key));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Unknown pet entity type in config: " + key);
                }
            }
        }
        this.petTypeLimits = petLimits;

        this.zoneCellSize = Math.max(16, c.getInt("zones.cell-size", 64));
        this.zoneOverloadedThreshold = Math.max(1, c.getInt("zones.overloaded-threshold", 100));
        this.zoneCriticalThreshold =
                Math.max(this.zoneOverloadedThreshold, c.getInt("zones.critical-threshold", 200));
        this.zoneAlertCooldownSeconds = Math.max(0, c.getInt("zones.alert-cooldown-seconds", 300));

        this.messageCooldownSeconds = Math.max(0, c.getInt("messages.cooldown-seconds", 2));
        this.prefix = c.getString("messages.prefix", "<gray>[<aqua>MobLimiter</aqua>]</gray> ");
        this.denySound = c.getString("messages.deny-sound", "entity.villager.no");
        this.alertSound = c.getString("messages.alert-sound", "block.note_block.pling");

        Map<String, String> parsedMessages = new HashMap<>();
        ConfigurationSection messagesSection = c.getConfigurationSection("messages");
        if (messagesSection != null) {
            for (String key : messagesSection.getKeys(false)) {
                if (messagesSection.isString(key)) {
                    parsedMessages.put(key, messagesSection.getString(key));
                }
            }
        }
        this.messages = parsedMessages;
    }

    public String message(String key, String fallback) {
        return messages.getOrDefault(key, fallback);
    }
}
