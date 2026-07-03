package dev.gteeri.moblimiter.util;

import dev.gteeri.moblimiter.MobLimiterPlugin;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** MiniMessage-based messaging with anti-spam cooldown and sounds. */
public final class Msg {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final MobLimiterPlugin plugin;
    private final Map<UUID, Long> lastDeny = new ConcurrentHashMap<>();

    public Msg(MobLimiterPlugin plugin) {
        this.plugin = plugin;
    }

    public Component parse(String raw, TagResolver... resolvers) {
        return MINI.deserialize(raw, resolvers);
    }

    public void send(CommandSender to, String messageKey, String fallback, TagResolver... resolvers) {
        String raw = plugin.cfg().prefix + plugin.cfg().message(messageKey, fallback);
        to.sendMessage(parse(raw, resolvers));
    }

    /** Deny feedback for players: message + sound, rate-limited per player. */
    public void deny(Player player, String messageKey, String fallback, TagResolver... resolvers) {
        long now = System.currentTimeMillis();
        long cooldownMs = plugin.cfg().messageCooldownSeconds * 1000L;
        Long last = lastDeny.get(player.getUniqueId());
        if (last != null && now - last < cooldownMs) {
            return;
        }
        lastDeny.put(player.getUniqueId(), now);
        send(player, messageKey, fallback, resolvers);
        playSound(player, plugin.cfg().denySound);
    }

    public void playSound(Player player, String soundKey) {
        try {
            player.playSound(Sound.sound(Key.key(soundKey), Sound.Source.MASTER, 1.0f, 1.0f));
        } catch (Exception ignored) {
            // invalid sound key in config -- ignore
        }
    }
}
