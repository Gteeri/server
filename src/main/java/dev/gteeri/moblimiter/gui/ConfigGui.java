package dev.gteeri.moblimiter.gui;

import dev.gteeri.moblimiter.MobLimiterPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;

import static dev.gteeri.moblimiter.gui.GuiUtils.*;

/**
 * In-game config editor (54 slots).
 *
 * Left-click = increase value, right-click = decrease, shift+click = ±10.
 * Changes are persisted to config.yml immediately via plugin#updateConfigValue.
 *
 * Layout (rows 0-5):
 *  Row 0 : header fillers
 *  Row 1 : Freeze on/off | freeze-threshold | unfreeze-threshold | refreeze-cooldown | env-cooldown
 *  Row 2 : Limits on/off | mob limit | frame limit | armor-stand limit | painting limit | vehicle limit
 *  Row 3 : Pet on/off   | pet total  | (4 fillers)
 *  Row 4 : scan-interval | scan-radius | density-radius | (fillers)
 *  Row 5 : back btn
 */
public final class ConfigGui implements Listener {

    // Row 1: freeze settings
    private static final int S_FREEZE_ON    = 10;
    private static final int S_FREEZE_THR   = 11;
    private static final int S_UNFREEZE_THR = 12;
    private static final int S_REFREEZE_CD  = 13;
    private static final int S_ENV_CD       = 14;

    // Row 2: limit settings
    private static final int S_LIMITS_ON  = 19;
    private static final int S_LIM_MOBS   = 20;
    private static final int S_LIM_FRAMES = 21;
    private static final int S_LIM_ARMOR  = 22;
    private static final int S_LIM_PAINT  = 23;
    private static final int S_LIM_VEH    = 24;

    // Row 3: pet settings
    private static final int S_PETS_ON    = 28;
    private static final int S_PET_TOTAL  = 29;

    // Row 4: scanner
    private static final int S_SCAN_INT    = 37;
    private static final int S_SCAN_RAD    = 38;
    private static final int S_DENSITY_RAD = 39;

    private static final int S_BACK = 45;

    private final MobLimiterPlugin plugin;

    public ConfigGui(MobLimiterPlugin plugin) {
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
                        .append(Component.text("\u041a\u043e\u043d\u0444\u0438\u0433\u0443\u0440\u0430\u0446\u0438\u044f", PURPLE))
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        holder.inventory = inv;
        var cfg = plugin.cfg();

        String hint = "\u041b\u041a\u041c +1 \u00b7 \u041f\u041a\u041c -1 \u00b7 Shift\u00d710";

        // -- Freeze toggle
        inv.setItem(S_FREEZE_ON, item(
                cfg.freezeEnabled ? Material.LIME_DYE : Material.GRAY_DYE,
                "\u0417\u0430\u043c\u043e\u0440\u043e\u0437\u043a\u0430: " + (cfg.freezeEnabled ? "\u0412\u041a\u041b" : "\u0412\u042b\u041a\u041b"),
                cfg.freezeEnabled ? GREEN : GREY,
                "\u041a\u043b\u0438\u043a \u2014 \u043f\u0435\u0440\u0435\u043a\u043b\u044e\u0447\u0438\u0442\u044c", GREY));

        // -- Freeze threshold
        inv.setItem(S_FREEZE_THR, item(Material.SNOWBALL,
                "\u041f\u043e\u0440\u043e\u0433 \u0437\u0430\u043c\u043e\u0440\u043e\u0437\u043a\u0438",
                PURPLE,
                List.of(text("\u0422\u0435\u043a\u0443\u0449\u0435\u0435 \u0437\u043d\u0430\u0447\u0435\u043d\u0438\u0435: ", GREY).append(text(String.valueOf(cfg.freezeThreshold), GREEN)),
                        text(hint, GREY))));

        // -- Unfreeze threshold
        inv.setItem(S_UNFREEZE_THR, item(Material.FIRE_CHARGE,
                "\u041f\u043e\u0440\u043e\u0433 \u0440\u0430\u0437\u043c\u043e\u0440\u043e\u0437\u043a\u0438",
                PURPLE,
                List.of(text("\u0422\u0435\u043a\u0443\u0449\u0435\u0435 \u0437\u043d\u0430\u0447\u0435\u043d\u0438\u0435: ", GREY).append(text(String.valueOf(cfg.unfreezeThreshold), GREEN)),
                        text(hint, GREY))));

        // -- Refreeze cooldown
        inv.setItem(S_REFREEZE_CD, item(Material.CLOCK,
                "\u041a\u0443\u043b\u0434\u0430\u0443\u043d \u0430\u043d\u0442\u0438\u0444\u043b\u0438\u043a\u0435\u0440\u0430 (\u0441\u0435\u043a)",
                PURPLE,
                List.of(text("\u0422\u0435\u043a\u0443\u0449\u0435\u0435 \u0437\u043d\u0430\u0447\u0435\u043d\u0438\u0435: ", GREY).append(text(cfg.refreezeCooldownSeconds + " \u0441", GREEN)),
                        text(hint, GREY))));

        // -- Env damage cooldown
        inv.setItem(S_ENV_CD, item(Material.BUCKET,
                "\u041a\u0443\u043b\u0434\u0430\u0443\u043d \u043e\u0442 \u0441\u0440\u0435\u0434\u044b (\u0441\u0435\u043a)",
                PURPLE,
                List.of(text("\u0422\u0435\u043a\u0443\u0449\u0435\u0435 \u0437\u043d\u0430\u0447\u0435\u043d\u0438\u0435: ", GREY).append(text(cfg.environmentDamageCooldownSeconds + " \u0441", GREEN)),
                        text(hint, GREY))));

        // -- Limits toggle
        inv.setItem(S_LIMITS_ON, item(
                cfg.limitsEnabled ? Material.LIME_DYE : Material.GRAY_DYE,
                "\u041b\u0438\u043c\u0438\u0442\u044b: " + (cfg.limitsEnabled ? "\u0412\u041a\u041b" : "\u0412\u042b\u041a\u041b"),
                cfg.limitsEnabled ? GREEN : GREY,
                "\u041a\u043b\u0438\u043a \u2014 \u043f\u0435\u0440\u0435\u043a\u043b\u044e\u0447\u0438\u0442\u044c", GREY));

        Integer[] limValues = {
                cfg.categoryLimits.get(dev.gteeri.moblimiter.util.Categories.Category.MOBS),
                cfg.categoryLimits.get(dev.gteeri.moblimiter.util.Categories.Category.ITEM_FRAMES),
                cfg.categoryLimits.get(dev.gteeri.moblimiter.util.Categories.Category.ARMOR_STANDS),
                cfg.categoryLimits.get(dev.gteeri.moblimiter.util.Categories.Category.PAINTINGS),
                cfg.categoryLimits.get(dev.gteeri.moblimiter.util.Categories.Category.VEHICLES)
        };
        String[] limNames = { "\u041b\u0438\u043c\u0438\u0442 \u043c\u043e\u0431\u043e\u0432", "\u041b\u0438\u043c\u0438\u0442 \u0440\u0430\u043c\u043e\u043a",
                "\u041b\u0438\u043c\u0438\u0442 \u0430\u0440\u043c\u043e\u0440-\u0441\u0442\u0435\u043d\u0434\u043e\u0432",
                "\u041b\u0438\u043c\u0438\u0442 \u043a\u0430\u0440\u0442\u0438\u043d", "\u041b\u0438\u043c\u0438\u0442 \u0442\u0440\u0430\u043d\u0441\u043f\u043e\u0440\u0442\u0430" };
        Material[] limMat = { Material.ZOMBIE_HEAD, Material.ITEM_FRAME,
                Material.ARMOR_STAND, Material.PAINTING, Material.OAK_BOAT };
        for (int i = 0; i < 5; i++) {
            int v = limValues[i] != null ? limValues[i] : -1;
            inv.setItem(S_LIM_MOBS + i, item(limMat[i], limNames[i], GREEN,
                    List.of(text("\u0422\u0435\u043a\u0443\u0449\u0435\u0435: ", GREY).append(text(String.valueOf(v), GREEN)),
                            text(hint, GREY))));
        }

        // -- Pets toggle
        inv.setItem(S_PETS_ON, item(
                cfg.petsEnabled ? Material.LIME_DYE : Material.GRAY_DYE,
                "\u041f\u0438\u0442\u043e\u043c\u0446\u044b: " + (cfg.petsEnabled ? "\u0412\u041a\u041b" : "\u0412\u042b\u041a\u041b"),
                cfg.petsEnabled ? GREEN : GREY,
                "\u041a\u043b\u0438\u043a \u2014 \u043f\u0435\u0440\u0435\u043a\u043b\u044e\u0447\u0438\u0442\u044c", GREY));

        // -- Pet total limit
        inv.setItem(S_PET_TOTAL, item(Material.BONE,
                "\u041e\u0431\u0449\u0438\u0439 \u043b\u0438\u043c\u0438\u0442 \u043f\u0438\u0442\u043e\u043c\u0446\u0435\u0432",
                GREEN,
                List.of(text("\u0422\u0435\u043a\u0443\u0449\u0435\u0435: ", GREY).append(text(String.valueOf(cfg.petTotalLimit), GREEN)),
                        text(hint, GREY))));

        // -- Scanner settings
        inv.setItem(S_SCAN_INT, item(Material.REPEATER,
                "\u0418\u043d\u0442\u0435\u0440\u0432\u0430\u043b \u0441\u043a\u0430\u043d\u0435\u0440\u0430 (\u0441\u0435\u043a)",
                GREY,
                List.of(text("\u0422\u0435\u043a\u0443\u0449\u0435\u0435: ", GREY).append(text(cfg.scanIntervalSeconds + " \u0441", GREEN)),
                        text(hint, GREY))));
        inv.setItem(S_SCAN_RAD, item(Material.SPYGLASS,
                "\u0420\u0430\u0434\u0438\u0443\u0441 \u0441\u043a\u0430\u043d\u0435\u0440\u0430 (\u0431\u043b)",
                GREY,
                List.of(text("\u0422\u0435\u043a\u0443\u0449\u0435\u0435: ", GREY).append(text(String.valueOf((int) cfg.scanRadius), GREEN)),
                        text(hint, GREY))));
        inv.setItem(S_DENSITY_RAD, item(Material.GLASS,
                "\u0420\u0430\u0434\u0438\u0443\u0441 \u043f\u043b\u043e\u0442\u043d\u043e\u0441\u0442\u0438 (\u0431\u043b)",
                GREY,
                List.of(text("\u0422\u0435\u043a\u0443\u0449\u0435\u0435: ", GREY).append(text(String.valueOf((int) cfg.densityRadius), GREEN)),
                        text(hint, GREY))));

        inv.setItem(S_BACK, backButton());
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
        // Clicks in the player's own inventory (below the panel) are just
        // cancelled and ignored -- no config reload, no GUI reopen.
        if (slot < 0 || slot >= event.getInventory().getSize()) {
            return;
        }
        if (slot == S_BACK) {
            plugin.mainMenu().open(player);
            return;
        }
        ClickType click = event.getClick();
        int delta = click.isShiftClick() ? 10 : 1;
        boolean increase = click == ClickType.LEFT || click == ClickType.SHIFT_LEFT;
        boolean decrease = click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT;
        if (!increase && !decrease) {
            return;
        }
        int d = increase ? delta : -delta;
        var cfg = plugin.cfg();
        boolean changed = true;
        switch (slot) {
            case S_FREEZE_ON -> toggle("freeze.enabled", !cfg.freezeEnabled);
            case S_FREEZE_THR -> setInt("freeze.freeze-threshold",
                    Math.max(2, cfg.freezeThreshold + d));
            case S_UNFREEZE_THR -> setInt("freeze.unfreeze-threshold",
                    Math.max(1, Math.min(cfg.freezeThreshold - 1, cfg.unfreezeThreshold + d)));
            case S_REFREEZE_CD -> setInt("freeze.refreeze-cooldown-seconds",
                    Math.max(0, cfg.refreezeCooldownSeconds + d));
            case S_ENV_CD -> setInt("freeze.environment-damage-cooldown-seconds",
                    Math.max(0, cfg.environmentDamageCooldownSeconds + d));
            case S_LIMITS_ON -> toggle("limits.enabled", !cfg.limitsEnabled);
            case S_LIM_MOBS -> setInt("limits.categories.mobs",
                    Math.max(1, getOrDef(cfg.categoryLimits,
                            dev.gteeri.moblimiter.util.Categories.Category.MOBS, 60) + d));
            case S_LIM_FRAMES -> setInt("limits.categories.item-frames",
                    Math.max(1, getOrDef(cfg.categoryLimits,
                            dev.gteeri.moblimiter.util.Categories.Category.ITEM_FRAMES, 30) + d));
            case S_LIM_ARMOR -> setInt("limits.categories.armor-stands",
                    Math.max(1, getOrDef(cfg.categoryLimits,
                            dev.gteeri.moblimiter.util.Categories.Category.ARMOR_STANDS, 20) + d));
            case S_LIM_PAINT -> setInt("limits.categories.paintings",
                    Math.max(1, getOrDef(cfg.categoryLimits,
                            dev.gteeri.moblimiter.util.Categories.Category.PAINTINGS, 20) + d));
            case S_LIM_VEH -> setInt("limits.categories.vehicles",
                    Math.max(1, getOrDef(cfg.categoryLimits,
                            dev.gteeri.moblimiter.util.Categories.Category.VEHICLES, 16) + d));
            case S_PETS_ON -> toggle("pets.enabled", !cfg.petsEnabled);
            case S_PET_TOTAL -> setInt("pets.total-limit", Math.max(1, cfg.petTotalLimit + d));
            case S_SCAN_INT -> setInt("freeze.scan-interval-seconds",
                    Math.max(1, cfg.scanIntervalSeconds + d));
            case S_SCAN_RAD -> setInt("freeze.scan-radius",
                    Math.max(8, (int) cfg.scanRadius + d));
            case S_DENSITY_RAD -> setInt("freeze.density-radius",
                    Math.max(2, (int) cfg.densityRadius + d));
            default -> changed = false; // filler click -- no reload, no reopen
        }
        if (changed) {
            // Reopen to show updated values
            plugin.configGui().open(player);
        }
    }

    private void toggle(String path, boolean value) {
        plugin.updateConfigValue(path, value);
    }

    private void setInt(String path, int value) {
        plugin.updateConfigValue(path, value);
    }

    private int getOrDef(java.util.Map<dev.gteeri.moblimiter.util.Categories.Category, Integer> map,
                         dev.gteeri.moblimiter.util.Categories.Category key, int def) {
        Integer v = map.get(key);
        return v != null ? v : def;
    }
}
