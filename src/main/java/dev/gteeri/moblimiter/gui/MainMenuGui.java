package dev.gteeri.moblimiter.gui;

import dev.gteeri.moblimiter.MobLimiterPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;

import static dev.gteeri.moblimiter.gui.GuiUtils.*;

/**
 * Main hub: 3 big navigation buttons on a branded dark background.
 *
 *  Slots (27-slot inventory, 3 rows):
 *  [fill][fill][STATS ][fill][CONFIG][fill][ZONES ][fill][fill]
 *  Row 2: all fillers
 *  Row 3: all fillers, centre = version/info item
 */
public final class MainMenuGui implements Listener {

    private static final int SLOT_STATS  = 2;
    private static final int SLOT_CONFIG = 4;
    private static final int SLOT_ZONES  = 6;
    private static final int SLOT_INFO   = 22;

    private final MobLimiterPlugin plugin;

    public MainMenuGui(MobLimiterPlugin plugin) {
        this.plugin = plugin;
    }

    private static final class Holder implements InventoryHolder {
        Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    public void open(Player player) {
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, 27,
                Component.text("MobLimiter ", GREEN)
                        .append(Component.text("— ", GREY))
                        .append(Component.text("\u0413\u043b\u0430\u0432\u043d\u043e\u0435 \u043c\u0435\u043d\u044e", PURPLE))
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        holder.inventory = inv;

        // --- Stats button
        inv.setItem(SLOT_STATS, item(Material.COMPARATOR,
                "\u00a7f\u00a7l\u25b6 \u0421\u0442\u0430\u0442\u0438\u0441\u0442\u0438\u043a\u0430 \u0438 \u0430\u043d\u0430\u043b\u0438\u0437",
                GREEN,
                List.of(
                        text("\u0417\u0430\u043c\u043e\u0440\u043e\u0437\u043a\u0438, \u0431\u043b\u043e\u043a\u0438\u0440\u043e\u0432\u043a\u0438, \u043f\u0438\u0442\u043e\u043c\u0446\u044b", GREY),
                        text("\u0410\u043d\u0430\u043b\u0438\u0437 \u043d\u0430\u0433\u0440\u0443\u0437\u043a\u0438 \u043f\u043e \u043a\u0430\u0442\u0435\u0433\u043e\u0440\u0438\u044f\u043c", GREY),
                        Component.empty(),
                        text("\u25ba \u041d\u0430\u0436\u043c\u0438\u0442\u0435 \u0434\u043b\u044f \u043e\u0442\u043a\u0440\u044b\u0442\u0438\u044f", GREEN))));

        // --- Config button
        inv.setItem(SLOT_CONFIG, item(Material.WRITABLE_BOOK,
                "\u00a7f\u00a7l\u25b6 \u041a\u043e\u043d\u0444\u0438\u0433\u0443\u0440\u0430\u0446\u0438\u044f",
                PURPLE,
                List.of(
                        text("\u0420\u0435\u0434\u0430\u043a\u0442\u0438\u0440\u043e\u0432\u0430\u043d\u0438\u0435 \u043b\u0438\u043c\u0438\u0442\u043e\u0432 \u043a\u043b\u0438\u043a\u0430\u043c\u0438", GREY),
                        text("\u041d\u0430\u0441\u0442\u0440ойка заморозки и питомцев", GREY),
                        Component.empty(),
                        text("\u25ba \u041d\u0430\u0436\u043c\u0438\u0442\u0435 \u0434\u043b\u044f \u043e\u0442\u043a\u0440\u044b\u0442\u0438\u044f", PURPLE))));

        // --- Zones button
        inv.setItem(SLOT_ZONES, item(Material.BEACON,
                "\u00a7f\u00a7l\u25b6 \u0413\u043e\u0440\u044f\u0447\u0438\u0435 \u0437\u043e\u043d\u044b",
                YELLOW,
                List.of(
                        text("\u041f\u0435\u0440\u0435\u0433\u0440\u0443\u0436\u0435\u043d\u043d\u044b\u0435 \u043e\u0431\u043b\u0430\u0441\u0442\u0438 \u0441 \u0442\u0435\u043b\u0435\u043f\u043e\u0440\u0442\u043e\u043c", GREY),
                        text("\u0416\u0451\u043b\u0442\u044b\u0435/\u043a\u0440\u0430\u0441\u043d\u044b\u0435 \u0437\u043e\u043d\u044b, \u0441\u043e\u0440\u0442\u0438\u0440\u043e\u0432\u043a\u0430", GREY),
                        Component.empty(),
                        text("\u25ba \u041d\u0430\u0436\u043c\u0438\u0442\u0435 \u0434\u043b\u044f \u043e\u0442\u043a\u0440\u044b\u0442\u0438\u044f", YELLOW))));

        // --- Info / version item
        inv.setItem(SLOT_INFO, item(Material.ENDER_EYE,
                "MobLimiter v" + plugin.getPluginMeta().getVersion(),
                GREEN,
                List.of(
                        text("Folia 1.21.11 \u00b7 \u0410\u0432\u0442\u043e\u0440: Gteeri", GREY),
                        text("/moblimit reload — \u043f\u0435\u0440\u0435\u0437\u0430\u0433\u0440\u0443\u0437\u043a\u0430", GREY))));

        fillEmpty(inv);
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();
        switch (slot) {
            case SLOT_STATS  -> plugin.statsGui().open(player);
            case SLOT_CONFIG -> plugin.configGui().open(player);
            case SLOT_ZONES  -> plugin.zonesGui().open(player);
            default -> { /* filler click — ignore */ }
        }
    }
}
