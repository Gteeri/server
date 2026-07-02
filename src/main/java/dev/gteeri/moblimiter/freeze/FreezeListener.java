package dev.gteeri.moblimiter.freeze;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import dev.gteeri.moblimiter.MobLimiterPlugin;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.entity.VillagerCareerChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;

/** Instant wake-up on damage/interaction + frozen-state restore on chunk load. */
public final class FreezeListener implements Listener {

    private final MobLimiterPlugin plugin;

    public FreezeListener(MobLimiterPlugin plugin) {
        this.plugin = plugin;
    }

    private long normalCooldownMs() {
        return plugin.cfg().refreezeCooldownSeconds * 1000L;
    }

    private long environmentCooldownMs() {
        return plugin.cfg().environmentDamageCooldownSeconds * 1000L;
    }

    /**
     * Any damage wakes the mob instantly. Environmental damage (suffocation,
     * drowning, fire...) grants a longer re-freeze cooldown so the mob can
     * actually escape instead of dying "quietly" while frozen.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }
        if (!plugin.freezer().isFrozen(mob)) {
            return;
        }
        long cooldown = isEnvironmental(event.getCause()) ? environmentCooldownMs() : normalCooldownMs();
        plugin.freezer().unfreezeNow(mob, cooldown);
    }

    private boolean isEnvironmental(EntityDamageEvent.DamageCause cause) {
        return switch (cause) {
            case SUFFOCATION, DROWNING, FIRE, FIRE_TICK, LAVA, HOT_FLOOR, FREEZE, CRAMMING -> true;
            default -> false;
        };
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof Mob mob && plugin.freezer().isFrozen(mob)) {
            plugin.freezer().unfreezeNow(mob, normalCooldownMs());
        }
    }

    /** Leashing a frozen mob wakes it so the player can lead it away. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLeash(PlayerLeashEntityEvent event) {
        if (event.getEntity() instanceof Mob mob && plugin.freezer().isFrozen(mob)) {
            plugin.freezer().unfreezeNow(mob, normalCooldownMs());
        }
    }

    /** Profession gained -> freeze; profession lost -> wake up immediately. */
    @EventHandler
    public void onVillagerCareerChange(VillagerCareerChangeEvent event) {
        if (!plugin.cfg().freezeVillagersWithProfession) {
            return;
        }
        Villager villager = event.getEntity();
        if (event.getReason() == VillagerCareerChangeEvent.ChangeReason.LOSING_JOB) {
            plugin.freezer().unfreezeLater(villager, 0L);
        } else {
            plugin.freezer().freezeLater(villager);
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            plugin.freezer().restore(entity);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        plugin.freezer().forget(event.getEntity().getUniqueId());
    }

    /** Chunk unloads keep only the persistent PDC flag; drop volatile maps. */
    @EventHandler
    public void onRemoveFromWorld(EntityRemoveFromWorldEvent event) {
        plugin.freezer().forget(event.getEntity().getUniqueId());
    }
}
