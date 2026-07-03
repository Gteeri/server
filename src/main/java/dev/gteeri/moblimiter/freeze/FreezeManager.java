package dev.gteeri.moblimiter.freeze;

import dev.gteeri.moblimiter.MobLimiterPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Boss;
import org.bukkit.entity.ChestedHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Steerable;
import org.bukkit.entity.Tameable;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Freezing = Mob#setAware(false): goal selector and pathfinding stop ticking
 * (the expensive part), but the mob stays in the world, can be hit, leashed,
 * put in a boat, traded with, etc. State survives restarts via PDC.
 */
public final class FreezeManager {

    private final MobLimiterPlugin plugin;
    private final NamespacedKey frozenKey;
    private final Set<UUID> frozen = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> refreezeCooldownUntil = new ConcurrentHashMap<>();
    private final AtomicLong totalFreezes = new AtomicLong();
    private final AtomicLong totalWakeups = new AtomicLong();

    public FreezeManager(MobLimiterPlugin plugin) {
        this.plugin = plugin;
        this.frozenKey = new NamespacedKey(plugin, "frozen");
    }

    public int frozenCount() {
        return frozen.size();
    }

    public long totalFreezes() {
        return totalFreezes.get();
    }

    public long totalWakeups() {
        return totalWakeups.get();
    }

    public boolean isFrozen(Mob mob) {
        return frozen.contains(mob.getUniqueId());
    }

    /** Anti-flicker: freshly woken mobs cannot be re-frozen for a while. */
    public boolean isOnRefreezeCooldown(Mob mob) {
        Long until = refreezeCooldownUntil.get(mob.getUniqueId());
        if (until == null) {
            return false;
        }
        if (until <= System.currentTimeMillis()) {
            refreezeCooldownUntil.remove(mob.getUniqueId());
            return false;
        }
        return true;
    }

    /** Mobs that must never be frozen by the density rule (player mechanics). */
    public boolean isExemptFromDensityFreeze(Mob mob) {
        var cfg = plugin.cfg();
        if (mob instanceof Boss) {
            return true;
        }
        if (cfg.exemptNamed && mob.customName() != null) {
            return true;
        }
        if (cfg.exemptTamed && mob instanceof Tameable tameable && tameable.isTamed()) {
            return true;
        }
        if (cfg.exemptLeashedByPlayer && mob.isLeashed() && mob.getLeashHolder() instanceof Player) {
            return true;
        }
        if (cfg.exemptInVehicleWithPlayer && isInVehicleWithPlayer(mob)) {
            return true;
        }
        if (cfg.exemptWithEquipment && hasPlayerGear(mob)) {
            return true;
        }
        return false;
    }

    private boolean isInVehicleWithPlayer(Mob mob) {
        Entity vehicle = mob.getVehicle();
        if (vehicle == null) {
            return false;
        }
        for (Entity passenger : vehicle.getPassengers()) {
            if (passenger instanceof Player) {
                return true;
            }
        }
        return false;
    }

    private boolean hasPlayerGear(Mob mob) {
        if (mob instanceof AbstractHorse horse && horse.getInventory().getSaddle() != null) {
            return true;
        }
        if (mob instanceof Steerable steerable && steerable.hasSaddle()) {
            return true;
        }
        if (mob instanceof ChestedHorse chested && chested.isCarryingChest()) {
            return true;
        }
        EntityEquipment equipment = mob.getEquipment();
        if (equipment == null) {
            return false;
        }
        for (ItemStack armor : equipment.getArmorContents()) {
            if (armor != null && !armor.getType().isAir()) {
                return true;
            }
        }
        return false;
    }

    /** Must run on the mob's region thread. */
    public void freezeNow(Mob mob) {
        if (!mob.isValid()) {
            return;
        }
        mob.setAware(false);
        mob.getPersistentDataContainer().set(frozenKey, PersistentDataType.BYTE, (byte) 1);
        if (frozen.add(mob.getUniqueId())) {
            totalFreezes.incrementAndGet();
        }
    }

    /** Must run on the mob's region thread. */
    public void unfreezeNow(Mob mob, long refreezeCooldownMillis) {
        if (mob.isValid()) {
            mob.setAware(true);
        }
        mob.getPersistentDataContainer().remove(frozenKey);
        if (frozen.remove(mob.getUniqueId())) {
            totalWakeups.incrementAndGet();
        }
        if (refreezeCooldownMillis > 0) {
            refreezeCooldownUntil.put(mob.getUniqueId(),
                    System.currentTimeMillis() + refreezeCooldownMillis);
        }
    }

    /** Thread-safe: schedules onto the mob's region thread. */
    public void freezeLater(Mob mob) {
        mob.getScheduler().run(plugin, task -> freezeNow(mob), null);
    }

    /** Thread-safe: schedules onto the mob's region thread. */
    public void unfreezeLater(Mob mob, long refreezeCooldownMillis) {
        mob.getScheduler().run(plugin, task -> unfreezeNow(mob, refreezeCooldownMillis), null);
    }

    /** Re-apply persisted frozen state after a chunk load (not a new freeze). */
    public void restore(Entity entity) {
        if (!(entity instanceof Mob mob)) {
            return;
        }
        if (!mob.getPersistentDataContainer().has(frozenKey, PersistentDataType.BYTE)) {
            return;
        }
        frozen.add(mob.getUniqueId());
        mob.getScheduler().run(plugin, task -> {
            if (mob.isValid()) {
                mob.setAware(false);
            }
        }, null);
    }

    /** Drop volatile in-memory state for an entity that left the world. */
    public void forget(UUID entityId) {
        frozen.remove(entityId);
        refreezeCooldownUntil.remove(entityId);
    }
}
