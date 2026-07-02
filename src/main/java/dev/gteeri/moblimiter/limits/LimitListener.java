package dev.gteeri.moblimiter.limits;

import dev.gteeri.moblimiter.MobLimiterPlugin;
import dev.gteeri.moblimiter.util.Categories;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * The key anti-dupe idea: we cancel the INTERACTION, not the spawn. A cancelled
 * PlayerInteractEvent means the click "never happened": the spawn egg stays in
 * the hand, breeding food is not eaten. The item never leaves the inventory, so
 * duping is impossible. CreatureSpawnEvent stays as a backstop for all other
 * spawn paths.
 */
public final class LimitListener implements Listener {

    private final MobLimiterPlugin plugin;

    public LimitListener(MobLimiterPlugin plugin) {
        this.plugin = plugin;
    }

    private TagResolver[] resolvers(LimitService.Check check) {
        return new TagResolver[]{
                Placeholder.unparsed("count", String.valueOf(check.count())),
                Placeholder.unparsed("limit", String.valueOf(check.limit())),
        };
    }

    private void deny(Player player, Categories.Category category, LimitService.Check check) {
        plugin.msg().deny(player, "region-full-" + category.key(),
                "<red>\u0417\u0434\u0435\u0441\u044c \u0443\u0436\u0435 \u0441\u043b\u0438\u0448\u043a\u043e\u043c \u043c\u043d\u043e\u0433\u043e \u0441\u0443\u0449\u043d\u043e\u0441\u0442\u0435\u0439 (<count>/<limit>).</red>",
                resolvers(check));
    }

    /** Spawn egg used on a block: cancel BEFORE the item is consumed. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteractBlock(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || !Categories.isSpawnEgg(item.getType())) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }
        Location spawnAt = clicked.getRelative(event.getBlockFace()).getLocation().add(0.5, 0.0, 0.5);
        LimitService.Check check = plugin.limits().check(spawnAt, Categories.Category.MOBS);
        if (check.overLimit()) {
            event.setCancelled(true);
            deny(event.getPlayer(), Categories.Category.MOBS, check);
        }
    }

    /** Spawn egg used on an entity, or feeding animals into breeding. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItem(event.getHand());
        if (item == null || item.getType().isAir()) {
            return;
        }
        Entity target = event.getRightClicked();

        if (Categories.isSpawnEgg(item.getType())) {
            LimitService.Check check = plugin.limits().check(target.getLocation(), Categories.Category.MOBS);
            if (check.overLimit()) {
                event.setCancelled(true);
                deny(player, Categories.Category.MOBS, check);
            }
            return;
        }

        // Feeding towards breeding: cancel before the food is eaten. Feeding a
        // baby to grow it is always allowed (it adds no new entity).
        if (target instanceof Animals animal
                && animal.isBreedItem(item)
                && animal.canBreed()
                && animal.getLoveModeTicks() <= 0) {
            LimitService.Check check = plugin.limits().check(animal.getLocation(), Categories.Category.MOBS);
            if (check.overLimit()) {
                event.setCancelled(true);
                plugin.msg().deny(player, "region-full-breeding",
                        "<red>\u0417\u0434\u0435\u0441\u044c \u0443\u0436\u0435 \u0441\u043b\u0438\u0448\u043a\u043e\u043c \u043c\u043d\u043e\u0433\u043e \u043c\u043e\u0431\u043e\u0432 (<count>/<limit>) \u2014 \u0440\u0430\u0437\u0432\u0435\u0434\u0435\u043d\u0438\u0435 \u043d\u0435\u0434\u043e\u0441\u0442\u0443\u043f\u043d\u043e.</red>",
                        resolvers(check));
            }
        }
    }

    /** Backstop for every other spawn path (natural, spawners, raids...). */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!plugin.cfg().blockedSpawnReasons.contains(event.getSpawnReason())) {
            return;
        }
        LimitService.Check check = plugin.limits().check(event.getLocation(), Categories.Category.MOBS);
        if (check.overLimit()) {
            event.setCancelled(true);
        }
    }

    /** Item frames and paintings. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        Categories.Category category = event.getEntity() instanceof Painting
                ? Categories.Category.PAINTINGS
                : Categories.Category.ITEM_FRAMES;
        LimitService.Check check = plugin.limits().check(event.getEntity().getLocation(), category);
        if (check.overLimit()) {
            event.setCancelled(true);
            if (event.getPlayer() != null) {
                deny(event.getPlayer(), category, check);
            }
        }
    }

    /** Armor stands, boats, minecarts... */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        Categories.Category category = Categories.categorize(event.getEntity());
        if (category == null || category == Categories.Category.MOBS) {
            return;
        }
        LimitService.Check check = plugin.limits().check(event.getEntity().getLocation(), category);
        if (check.overLimit()) {
            event.setCancelled(true);
            if (event.getPlayer() != null) {
                deny(event.getPlayer(), category, check);
            }
        }
    }

    /** Dispensers with spawn eggs / boats / armor stands: cancel keeps the item inside. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        Categories.Category category = Categories.categorizeMaterial(event.getItem().getType());
        if (category == null) {
            return;
        }
        Block block = event.getBlock();
        if (!(block.getBlockData() instanceof Directional directional)) {
            return;
        }
        Location target = block.getRelative(directional.getFacing()).getLocation().add(0.5, 0.5, 0.5);
        LimitService.Check check = plugin.limits().check(target, category);
        if (check.overLimit()) {
            event.setCancelled(true);
        }
    }
}
