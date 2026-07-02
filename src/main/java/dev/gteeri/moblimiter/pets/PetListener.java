package dev.gteeri.moblimiter.pets;

import dev.gteeri.moblimiter.MobLimiterPlugin;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public final class PetListener implements Listener {

    private final MobLimiterPlugin plugin;

    public PetListener(MobLimiterPlugin plugin) {
        this.plugin = plugin;
    }

    /** Block taming above the limit (LOW so other plugins can still react). */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onTame(EntityTameEvent event) {
        if (!plugin.cfg().petsEnabled) {
            return;
        }
        if (!(event.getOwner() instanceof Player player)) {
            return;
        }
        PetManager.TameCheck check = plugin.pets().canTame(player.getUniqueId(), event.getEntity().getType());
        if (!check.allowed()) {
            event.setCancelled(true);
            plugin.msg().deny(player, check.messageKey(),
                    "<red>\u041b\u0438\u043c\u0438\u0442 \u043f\u0438\u0442\u043e\u043c\u0446\u0435\u0432 \u0434\u043e\u0441\u0442\u0438\u0433\u043d\u0443\u0442 (<count>/<limit>).</red>",
                    Placeholder.unparsed("count", String.valueOf(check.count())),
                    Placeholder.unparsed("limit", String.valueOf(check.limit())));
        }
    }

    /** Successful tame: tag the pet with its owner and bump the ledger. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTameSuccess(EntityTameEvent event) {
        if (!(event.getOwner() instanceof Player player)) {
            return;
        }
        LivingEntity pet = event.getEntity();
        pet.getPersistentDataContainer().set(plugin.pets().ownerKey(), PersistentDataType.STRING,
                player.getUniqueId().toString());
        plugin.pets().increment(player.getUniqueId(), pet.getType());
    }

    /** Pet death: decrement via the PDC tag, works with the owner offline. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPetDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        String ownerRaw = entity.getPersistentDataContainer()
                .get(plugin.pets().ownerKey(), PersistentDataType.STRING);
        if (ownerRaw == null
                && entity instanceof Tameable tameable
                && tameable.isTamed()
                && tameable.getOwnerUniqueId() != null) {
            ownerRaw = tameable.getOwnerUniqueId().toString();
        }
        if (ownerRaw == null) {
            return;
        }
        try {
            plugin.pets().decrement(UUID.fromString(ownerRaw), entity.getType());
        } catch (IllegalArgumentException ignored) {
        }
    }
}
