package dev.gteeri.moblimiter.gui;

import dev.gteeri.moblimiter.MobLimiterPlugin;
import dev.gteeri.moblimiter.util.Categories;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.gteeri.moblimiter.gui.GuiUtils.*;

/**
 * Statistics & analysis screen (54 slots, 6 rows).
 *
 *  Row 0: title bar (fillers)
 *  Row 1: [Freeze stats] [Limit stats] [Pet stats]
 *  Row 2: [Entity breakdown by category × 5]
 *  Row 3: [Zone summary] [Server load estimate]
 *  Row 4: fillers
 *  Row 5: [back btn at 45]
 */
public final class StatsGui implements Listener {

    private static final int SLOT_FREEZE  = 10;
    private static final int SLOT_LIMITS  = 13;
    private static final int SLOT_PETS    = 16;
    private static final int SLOT_CAT_START = 19; // 19-23 (5 categories)
    private static final int SLOT_ZONES   = 28;
    private static final int SLOT_SERVER  = 31;
    private static final int SLOT_BACK    = 45;

    private final MobLimiterPlugin plugin;

    public StatsGui(MobLimiterPlugin plugin) {
        this.plugin = plugin;
    }

    private static final class Holder implements InventoryHolder {
        Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    public void open(Player player) {
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("MobLimiter ", GREEN)
                        .append(Component.text("\u2014 ", GREY))
                        .append(Component.text("\u0421\u0442\u0430\u0442\u0438\u0441\u0442\u0438\u043a\u0430", PURPLE))
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        holder.inventory = inv;

        // --- Freeze stats
        long totalFreezes = plugin.freezer().totalFreezes();
        long totalWakeups = plugin.freezer().totalWakeups();
        int currentFrozen = plugin.freezer().frozenCount();
        List<Component> freezeLore = new ArrayList<>();
        freezeLore.add(text("\u0421\u0435\u0439\u0447\u0430\u0441 \u0437\u0430\u043c\u043e\u0440\u043e\u0436\u0435\u043d\u043e: ", GREY)
                .append(text(String.valueOf(currentFrozen), GREEN)));
        freezeLore.add(text("\u0412\u0441\u0435\u0433\u043e \u0437\u0430\u043c\u043e\u0440\u043e\u0436\u0435\u043d\u043e \u0437\u0430 \u0441\u0435\u0441\u0441\u0438\u044e: ", GREY)
                .append(text(String.valueOf(totalFreezes), GREEN)));
        freezeLore.add(text("\u041f\u0440\u043e\u0431\u0443\u0436\u0434\u0435\u043d\u0438\u0439 \u0437\u0430 \u0441\u0435\u0441\u0441\u0438\u044e: ", GREY)
                .append(text(String.valueOf(totalWakeups), PURPLE)));
        long freezeRatio = totalFreezes == 0 ? 0 : totalWakeups * 100L / totalFreezes;
        freezeLore.add(Component.empty());
        freezeLore.add(text("\u0410\u043a\u0442\u0438\u0432\u043d\u043e\u0441\u0442\u044c \u043f\u0440\u043e\u0431\u0443\u0436\u0434\u0435\u043d\u0438\u0439: " + freezeRatio + "%", GREY));
        freezeLore.add(text("(\u0447\u0435\u043c \u043d\u0438\u0436\u0435 \u2014 \u043b\u0443\u0447\u0448\u0435 \u0434\u043b\u044f TPS)", GREY));
        inv.setItem(SLOT_FREEZE, item(Material.ICE, "\u2744 \u0417\u0430\u043c\u043e\u0440\u043e\u0437\u043a\u0430 \u043c\u043e\u0431\u043e\u0432", GREEN, freezeLore));

        // --- Limits stats
        long blockedTotal = plugin.limits().blockedTotal();
        long allowedTotal = plugin.limits().allowedTotal();
        long checkTotal = blockedTotal + allowedTotal;
        long blockPercent = checkTotal == 0 ? 0 : blockedTotal * 100L / checkTotal;
        List<Component> limitsLore = new ArrayList<>();
        limitsLore.add(text("\u0411\u043b\u043e\u043a\u0438\u0440\u043e\u0432ок \u0437\u0430 \u0441\u0435\u0441\u0441\u0438\u044e: ", GREY)
                .append(text(String.valueOf(blockedTotal), RED)));
        limitsLore.add(text("\u0420\u0430\u0437\u0440\u0435\u0448\u0435\u043d\u043e \u0437\u0430 \u0441\u0435\u0441\u0441\u0438\u044e: ", GREY)
                .append(text(String.valueOf(allowedTotal), GREEN)));
        limitsLore.add(Component.empty());
        limitsLore.add(text("\u0417\u0430блокировано: " + blockPercent + "% от попыток",
                blockPercent > 50 ? RED : blockPercent > 20 ? YELLOW : GREEN));
        for (Categories.Category cat : Categories.Category.values()) {
            long blocked = plugin.limits().blockedCount(cat);
            if (blocked > 0) {
                limitsLore.add(text("  " + catName(cat) + ": -" + blocked, GREY));
            }
        }
        inv.setItem(SLOT_LIMITS, item(Material.BARRIER, "\u26d4 \u041b\u0438\u043c\u0438\u0442\u044b \u043e\u0431\u043b\u0430\u0441\u0442\u0435\u0439", PURPLE, limitsLore));

        // --- Pet stats
        int globalPets = plugin.pets().globalTotal();
        int owners = plugin.pets().ownersTracked();
        List<Component> petLore = new ArrayList<>();
        petLore.add(text("\u0412\u0441\u0435\u0433\u043e \u043f\u0438\u0442\u043e\u043c\u0446\u0435\u0432 \u043d\u0430 \u0441\u0435\u0440\u0432\u0435\u0440\u0435: ", GREY)
                .append(text(String.valueOf(globalPets), GREEN)));
        petLore.add(text("\u0418\u0433\u0440\u043e\u043a\u043e\u0432 \u0441 \u043f\u0438\u0442\u043e\u043c\u0446\u0430\u043c\u0438: ", GREY)
                .append(text(String.valueOf(owners), GREEN)));
        petLore.add(Component.empty());
        petLore.add(text("\u041b\u0438\u043c\u0438\u0442 \u043d\u0430 \u0438\u0433\u0440\u043e\u043a\u0430: " + plugin.cfg().petTotalLimit, GREY));
        inv.setItem(SLOT_PETS, item(Material.BONE, "\u2665 \u041f\u0438\u0442\u043e\u043c\u0446\u044b", GREEN, petLore));

        // --- Category breakdown
        Map<Categories.Category, Integer> totals = plugin.zones().freshTotals();
        Categories.Category[] cats = Categories.Category.values();
        for (int i = 0; i < cats.length; i++) {
            Categories.Category cat = cats[i];
            int count = totals.getOrDefault(cat, 0);
            Integer limit = plugin.cfg().categoryLimits.get(cat);
            String limitStr = (limit != null && limit >= 0) ? String.valueOf(limit) : "\u221e";
            boolean danger = limit != null && limit >= 0 && count >= limit * 0.9;
            List<Component> catLore = new ArrayList<>();
            catLore.add(text("\u0412 \u043e\u0442\u0441\u043b\u0435\u0436\u0438\u0432\u0430\u0435\u043c\u044b\u0445 \u0437\u043e\u043d\u0430\u0445:", GREY));
            catLore.add(text(count + " / " + limitStr, danger ? RED : GREEN));
            if (danger) {
                catLore.add(text("\u26a0 \u0411\u043b\u0438\u0437\u043a\u043e \u043a \u043b\u0438\u043c\u0438\u0442\u0443!", YELLOW));
            }
            inv.setItem(SLOT_CAT_START + i, item(catMaterial(cat), catName(cat), danger ? RED : GREEN, catLore));
        }

        // --- Zone summary
        int zones = plugin.zones().trackedZones();
        int topCount = plugin.zones().topZones(100).size();
        List<Component> zoneLore = new ArrayList<>();
        zoneLore.add(text("\u041e\u0442\u0441\u043b\u0435\u0436\u0438\u0432\u0430\u0435\u0442\u0441\u044f \u0437\u043e\u043d: ", GREY).append(text(String.valueOf(zones), GREEN)));
        zoneLore.add(text("\u041f\u0435\u0440\u0435\u0433\u0440\u0443\u0436\u0435\u043d\u043d\u044b\u0445 (≥" + plugin.cfg().zoneOverloadedThreshold + "): ", GREY)
                .append(text(String.valueOf(topCount), topCount > 0 ? YELLOW : GREEN)));
        zoneLore.add(Component.empty());
        zoneLore.add(text("\u25ba /moblimit zones \u2014 \u043f\u043e\u0434\u0440\u043e\u0431\u043d\u0435\u0435", GREY));
        inv.setItem(SLOT_ZONES, item(Material.MAP, "\ud83d\uddfa \u0417\u043e\u043d\u044b", GREEN, zoneLore));

        // --- Server load estimate
        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / 1_048_576;
        long totalMb = rt.totalMemory() / 1_048_576;
        List<Component> serverLore = new ArrayList<>();
        serverLore.add(text("\u0418\u0441\u043fо\u043bьзуемая RAM: " + usedMb + " / " + totalMb + " MB", GREY));
        serverLore.add(text("\u041e\u043d\u043b\u0430\u0439\u043d \u0438\u0433\u0440\u043e\u043a\u043e\u0432: "
                + Bukkit.getOnlinePlayers().size() + "/" + Bukkit.getMaxPlayers(), GREY));
        serverLore.add(Component.empty());
        serverLore.add(text("\u0414\u0430\u043d\u043d\u044b\u0435 \u043d\u0430 \u043c\u043e\u043c\u0435\u043d\u0442 \u043e\u0442\u043a\u0440\u044b\u0442\u0438\u044f GUI", GREY));
        inv.setItem(SLOT_SERVER, item(Material.COMMAND_BLOCK, "\u2699 \u0421\u0435\u0440\u0432\u0435\u0440", GREY, serverLore));

        // Back
        inv.setItem(SLOT_BACK, backButton());
        fillEmpty(inv);
        player.openInventory(inv);
    }

    private String catName(Categories.Category cat) {
        return switch (cat) {
            case MOBS -> "\u041c\u043e\u0431\u044b";
            case ITEM_FRAMES -> "\u0420\u0430\u043c\u043a\u0438";
            case ARMOR_STANDS -> "\u0410\u0440\u043c\u043e\u0440-\u0441\u0442\u0435\u043d\u0434\u044b";
            case PAINTINGS -> "\u041a\u0430\u0440\u0442\u0438\u043d\u044b";
            case VEHICLES -> "\u0422\u0440\u0430\u043d\u0441\u043f\u043e\u0440\u0442";
        };
    }

    private Material catMaterial(Categories.Category cat) {
        return switch (cat) {
            case MOBS -> Material.ZOMBIE_HEAD;
            case ITEM_FRAMES -> Material.ITEM_FRAME;
            case ARMOR_STANDS -> Material.ARMOR_STAND;
            case PAINTINGS -> Material.PAINTING;
            case VEHICLES -> Material.OAK_BOAT;
        };
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
        if (event.getRawSlot() == SLOT_BACK) {
            plugin.mainMenu().open(player);
        }
    }
}
