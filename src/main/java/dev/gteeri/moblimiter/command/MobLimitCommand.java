package dev.gteeri.moblimiter.command;

import dev.gteeri.moblimiter.MobLimiterPlugin;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MobLimitCommand implements TabExecutor {

    private final MobLimiterPlugin plugin;

    public MobLimitCommand(MobLimiterPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "info" -> info(sender);
            case "reload" -> {
                if (noAdmin(sender)) {
                    return true;
                }
                plugin.reloadPluginConfig();
                plugin.msg().send(sender, "reloaded",
                        "<green>\u041a\u043e\u043d\u0444\u0438\u0433\u0443\u0440\u0430\u0446\u0438\u044f \u043f\u0435\u0440\u0435\u0437\u0430\u0433\u0440\u0443\u0436\u0435\u043d\u0430.</green>");
            }
            case "pets" -> pets(sender);
            case "gui", "menu" -> {
                if (noAdmin(sender)) {
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    plugin.msg().send(sender, "players-only", "<red>\u0422\u043e\u043b\u044c\u043a\u043e \u0434\u043b\u044f \u0438\u0433\u0440\u043e\u043a\u043e\u0432.</red>");
                    return true;
                }
                plugin.mainMenu().open(player);
            }
            case "zones" -> {
                if (noAdmin(sender)) {
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    plugin.msg().send(sender, "players-only", "<red>\u0422\u043e\u043b\u044c\u043a\u043e \u0434\u043b\u044f \u0438\u0433\u0440\u043e\u043a\u043e\u0432.</red>");
                    return true;
                }
                plugin.zonesGui().open(player);
            }
            default -> plugin.msg().send(sender, "usage",
                    "<gray>\u0418\u0441\u043f\u043e\u043b\u044c\u0437\u043e\u0432\u0430\u043d\u0438\u0435: /moblimit [gui|reload|pets|zones]</gray>");
        }
        return true;
    }

    private boolean noAdmin(CommandSender sender) {
        if (sender.hasPermission("moblimit.admin")) {
            return false;
        }
        plugin.msg().send(sender, "no-permission", "<red>\u041d\u0435\u0434\u043e\u0441\u0442\u0430\u0442\u043e\u0447\u043d\u043e \u043f\u0440\u0430\u0432.</red>");
        return true;
    }

    private void info(CommandSender sender) {
        plugin.msg().send(sender, "info",
                "<gray>MobLimiter: \u0437\u0430\u043c\u043e\u0440\u043e\u0436\u0435\u043d\u043e \u043c\u043e\u0431\u043e\u0432 \u2014 <frozen>, \u043e\u0442\u0441\u043b\u0435\u0436\u0438\u0432\u0430\u0435\u0442\u0441\u044f \u0437\u043e\u043d \u2014 <zones>.</gray>",
                Placeholder.unparsed("frozen", String.valueOf(plugin.freezer().frozenCount())),
                Placeholder.unparsed("zones", String.valueOf(plugin.zones().trackedZones())));
    }

    private void pets(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.msg().send(sender, "players-only", "<red>\u0422\u043e\u043b\u044c\u043a\u043e \u0434\u043b\u044f \u0438\u0433\u0440\u043e\u043a\u043e\u0432.</red>");
            return;
        }
        Map<String, Integer> pets = plugin.pets().snapshot(player.getUniqueId());
        int total = plugin.pets().total(player.getUniqueId());
        plugin.msg().send(player, "pets-header",
                "<gray>\u0412\u0430\u0448\u0438 \u043f\u0438\u0442\u043e\u043c\u0446\u044b: <total>/<limit></gray>",
                Placeholder.unparsed("total", String.valueOf(total)),
                Placeholder.unparsed("limit", String.valueOf(plugin.cfg().petTotalLimit)));
        for (Map.Entry<String, Integer> entry : pets.entrySet()) {
            plugin.msg().send(player, "pets-line", "<gray>  <type>: <count></gray>",
                    Placeholder.unparsed("type", entry.getKey()),
                    Placeholder.unparsed("count", String.valueOf(entry.getValue())));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> options = new ArrayList<>(List.of("info", "pets"));
        if (sender.hasPermission("moblimit.admin")) {
            options.add("gui");
            options.add("reload");
            options.add("zones");
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.startsWith(prefix)).toList();
    }
}
