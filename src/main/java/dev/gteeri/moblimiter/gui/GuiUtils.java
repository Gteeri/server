package dev.gteeri.moblimiter.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** Shared helpers for the branded GUI panels. */
public final class GuiUtils {

    // Brand palette
    /** #1f2428 — dark background (approximated with black glass pane) */
    public static final TextColor BG = TextColor.color(0x1f2428);
    /** #7fffbd — brand green */
    public static final TextColor GREEN = TextColor.color(0x7fffbd);
    /** #ff00ff — brand purple/magenta */
    public static final TextColor PURPLE = TextColor.color(0xff00ff);
    /** Neutral white */
    public static final TextColor WHITE = TextColor.color(0xffffff);
    /** Muted grey for secondary info */
    public static final TextColor GREY = TextColor.color(0x8a9ba8);
    /** Danger red */
    public static final TextColor RED = TextColor.color(0xff4d4d);
    /** Warning yellow */
    public static final TextColor YELLOW = TextColor.color(0xffd966);

    private GuiUtils() {
    }

    /** Plain (non-italic) component with the given color. */
    public static Component text(String text, TextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    /** Bold non-italic component with the given color (no legacy codes). */
    public static Component boldText(String text, TextColor color) {
        return Component.text(text, color)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true);
    }

    /** Build a labeled item with a lore list. */
    public static ItemStack item(Material material, String name, TextColor nameColor,
                                  List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(text(name, nameColor));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** Build a labeled item with a bold title and a lore list. */
    public static ItemStack itemBold(Material material, String name, TextColor nameColor,
                                      List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(boldText(name, nameColor));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** Build an item with a single lore line. */
    public static ItemStack item(Material material, String name, TextColor nameColor,
                                  String loreLine, TextColor loreColor) {
        return item(material, name, nameColor, List.of(text(loreLine, loreColor)));
    }

    /** Blank dark-glass filler for the background. */
    public static ItemStack filler() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        return item;
    }

    /** Fill all empty slots of an inventory with the filler pane. */
    public static void fillEmpty(org.bukkit.inventory.Inventory inv) {
        ItemStack f = filler();
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, f);
            }
        }
    }

    /** A simple "back" button. */
    public static ItemStack backButton() {
        return item(Material.ARROW, "\u2190 \u041d\u0430\u0437\u0430\u0434", GREY, "\u0412\u0435\u0440\u043d\u0443\u0442\u044c\u0441\u044f \u0432 \u0433\u043b\u0430\u0432\u043d\u043e\u0435 \u043c\u0435\u043d\u044e", GREY);
    }
}
