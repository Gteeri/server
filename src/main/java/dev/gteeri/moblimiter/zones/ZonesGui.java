package dev.gteeri.moblimiter.zones;

import dev.gteeri.moblimiter.MobLimiterPlugin;
import dev.gteeri.moblimiter.util.Categories;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Admin GUI: worst zones on top, yellow = overloaded, red = critical, click = teleport. */
public final class ZonesGui implements Listener {

    private static final int SIZE = 54;
    private static final int REFRESH_SLOT = 49;
    private static final int MAX_ZONES = 45;

    private final MobLimiterPlugin plugin;

    public ZonesGui(MobLimiterPlugin plugin) {
        this.plugin = plugin;
    }

    private static final class Holder implements InventoryHolder {
        private Inventory inventory;
        private List<ZoneRegistry.ZoneSample> zones = List.of();

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public void open(Player player) {
        List<ZoneRegistry.ZoneSample> top = plugin.zones().topZones(MAX_ZONES);
        Holder holder = new Holder();
        Inventory inventory = Bukkit.createInventory(holder, SIZE,
                Component.text("\u0413\u043e\u0440\u044f\u0447\u0438\u0435 \u0437\u043e\u043d\u044b (" + top.size() + ")", NamedTextColor.DARK_RED));
        holder.inventory = inventory;
        holder.zones = top;

        var cfg = plugin.cfg();
        for (int i = 0; i < top.size() && i < MAX_ZONES; i++) {
            inventory.setItem(i, zoneItem(top.get(i), cfg.zoneCriticalThreshold, cfg.zoneOverloadedThreshold));
        }
        inventory.setItem(REFRESH_SLOT, refreshItem());
        player.openInventory(inventory);
    }

    private ItemStack zoneItem(ZoneRegistry.ZoneSample zone, int criticalThreshold, int overloadedThreshold) {
        boolean critical = zone.total() >= criticalThreshold;
        ItemStack item = new ItemStack(critical ? Material.RED_CONCRETE : Material.YELLOW_CONCRETE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plain(
                (critical ? "\u041a\u0420\u0418\u0422\u0418\u0427\u0415\u0421\u041a\u0410\u042f " : "\u041f\u0435\u0440\u0435\u0433\u0440\u0443\u0436\u0435\u043d\u0430 ") + zone.key().world()
                        + " [" + (int) zone.x() + ", " + (int) zone.z() + "]",
                critical ? NamedTextColor.RED : NamedTextColor.YELLOW));
        List<Component> lore = new ArrayList<>();
        lore.add(plain("\u0412\u0441\u0435\u0433\u043e \u0441\u0443\u0449\u043d\u043e\u0441\u0442\u0435\u0439: " + zone.total(), NamedTextColor.WHITE));
        int percent = (int) Math.round(zone.total() * 100.0 / overloadedThreshold);
        lore.add(plain("\u041d\u0430\u0433\u0440\u0443\u0437\u043a\u0430: " + percent + "% \u043e\u0442 \u043f\u043e\u0440\u043e\u0433\u0430", NamedTextColor.GRAY));
        for (Map.Entry<Categories.Category, Integer> entry : zone.counts().entrySet()) {
            lore.add(plain("  " + categoryName(entry.getKey()) + ": " + entry.getValue(), NamedTextColor.GRAY));
        }
        lore.add(plain("\u041a\u043b\u0438\u043a \u2014 \u0442\u0435\u043b\u0435\u043f\u043e\u0440\u0442 \u043a \u0437\u043e\u043d\u0435", NamedTextColor.AQUA));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack refreshItem() {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plain("\u041e\u0431\u043d\u043e\u0432\u0438\u0442\u044c", NamedTextColor.GREEN));
        item.setItemMeta(meta);
        return item;
    }

    private Component plain(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private String categoryName(Categories.Category category) {
        return switch (category) {
            case MOBS -> "\u041c\u043e\u0431\u044b";
            case ITEM_FRAMES -> "\u0420\u0430\u043c\u043a\u0438";
            case ARMOR_STANDS -> "\u0410\u0440\u043c\u043e\u0440-\u0441\u0442\u0435\u043d\u0434\u044b";
            case PAINTINGS -> "\u041a\u0430\u0440\u0442\u0438\u043d\u044b";
            case VEHICLES -> "\u0422\u0440\u0430\u043d\u0441\u043f\u043e\u0440\u0442";
        };
    }

    /**
     * Find a safe standing spot at/above the sample point: two passable blocks
     * with solid ground, scanning upward; falls back to the surface. Must run
     * on the region thread owning the location.
     */
    private static Location findSafeSpot(Location target) {
        World world = target.getWorld();
        int x = target.getBlockX();
        int z = target.getBlockZ();
        int start = Math.max(world.getMinHeight() + 1, target.getBlockY());
        int max = Math.min(world.getMaxHeight() - 2, start + 32);
        for (int y = start; y <= max; y++) {
            if (world.getBlockAt(x, y, z).isPassable()
                    && world.getBlockAt(x, y + 1, z).isPassable()
                    && !world.getBlockAt(x, y - 1, z).isPassable()) {
                return new Location(world, x + 0.5, y, z + 0.5);
            }
        }
        int top = world.getHighestBlockYAt(x, z) + 1;
        return new Location(world, x + 0.5, top, z + 0.5);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == REFRESH_SLOT) {
            open(player);
            return;
        }
        if (slot < 0 || slot >= holder.zones.size()) {
            return;
        }
        ZoneRegistry.ZoneSample zone = holder.zones.get(slot);
        World world = Bukkit.getWorld(zone.key().world());
        if (world == null) {
            return;
        }
        Location target = new Location(world, zone.x(), zone.y(), zone.z());
        player.closeInventory();
        // Compute a safe spot on the region owning the target, then teleport.
        Bukkit.getRegionScheduler().run(plugin, target, task -> {
            Location safe = findSafeSpot(target);
            player.getScheduler().run(plugin, t -> player.teleportAsync(safe), null);
        });
        plugin.msg().send(player, "teleported",
                "<green>\u0422\u0435\u043b\u0435\u043f\u043e\u0440\u0442\u0430\u0446\u0438\u044f: <world> [<x>, <z>]</green>",
                Placeholder.unparsed("world", zone.key().world()),
                Placeholder.unparsed("x", String.valueOf((int) zone.x())),
                Placeholder.unparsed("z", String.valueOf((int) zone.z())));
    }
}
