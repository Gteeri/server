package dev.gteeri.moblimiter.zones;

import dev.gteeri.moblimiter.MobLimiterPlugin;
import dev.gteeri.moblimiter.util.Categories;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Hot-zone statistics aggregated by the cluster scanner (64x64 cells). */
public final class ZoneRegistry {

    public record ZoneKey(String world, int cellX, int cellZ) {
    }

    public record ZoneSample(ZoneKey key, Map<Categories.Category, Integer> counts, int total,
                             long timestamp, double x, double y, double z) {
    }

    private static final long STALE_AFTER_MS = 5 * 60_000L;

    private final MobLimiterPlugin plugin;
    private final Map<ZoneKey, ZoneSample> zones = new ConcurrentHashMap<>();
    private final Map<ZoneKey, Long> lastAlertAt = new ConcurrentHashMap<>();

    public ZoneRegistry(MobLimiterPlugin plugin) {
        this.plugin = plugin;
    }

    public int trackedZones() {
        return zones.size();
    }

    /** Fresh entity totals by category across all tracked zones (stats GUI). */
    public Map<Categories.Category, Integer> freshTotals() {
        long now = System.currentTimeMillis();
        EnumMap<Categories.Category, Integer> totals = new EnumMap<>(Categories.Category.class);
        for (ZoneSample sample : zones.values()) {
            if (now - sample.timestamp() > STALE_AFTER_MS) {
                continue;
            }
            for (Map.Entry<Categories.Category, Integer> entry : sample.counts().entrySet()) {
                totals.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
        }
        return totals;
    }

    /** Called from region threads by the cluster scanner. */
    public void record(World world, List<Entity> entities) {
        var cfg = plugin.cfg();
        int cellSize = cfg.zoneCellSize;
        Map<ZoneKey, EnumMap<Categories.Category, Integer>> aggregate = new HashMap<>();
        Map<ZoneKey, Location> sampleLocation = new HashMap<>();
        for (Entity entity : entities) {
            Categories.Category category = Categories.categorize(entity);
            if (category == null) {
                continue;
            }
            Location location = entity.getLocation();
            ZoneKey key = new ZoneKey(world.getName(),
                    Math.floorDiv(location.getBlockX(), cellSize),
                    Math.floorDiv(location.getBlockZ(), cellSize));
            aggregate.computeIfAbsent(key, k -> new EnumMap<>(Categories.Category.class))
                    .merge(category, 1, Integer::sum);
            sampleLocation.putIfAbsent(key, location);
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<ZoneKey, EnumMap<Categories.Category, Integer>> entry : aggregate.entrySet()) {
            int total = 0;
            for (int value : entry.getValue().values()) {
                total += value;
            }
            Location location = sampleLocation.get(entry.getKey());
            ZoneSample sample = new ZoneSample(entry.getKey(), Map.copyOf(entry.getValue()), total,
                    now, location.getX(), location.getY(), location.getZ());
            zones.put(entry.getKey(), sample);
            if (total >= cfg.zoneCriticalThreshold) {
                maybeAlert(sample);
            }
        }
        zones.entrySet().removeIf(entry -> now - entry.getValue().timestamp() > STALE_AFTER_MS * 2);
        // Keep the alert-cooldown map from growing forever.
        long alertRetentionMs = Math.max(cfg.zoneAlertCooldownSeconds * 1000L, STALE_AFTER_MS) * 2L;
        lastAlertAt.entrySet().removeIf(entry -> now - entry.getValue() > alertRetentionMs);
    }

    /** Overloaded zones, worst first. */
    public List<ZoneSample> topZones(int max) {
        long now = System.currentTimeMillis();
        List<ZoneSample> result = new ArrayList<>();
        for (ZoneSample sample : zones.values()) {
            if (now - sample.timestamp() > STALE_AFTER_MS) {
                continue;
            }
            if (sample.total() < plugin.cfg().zoneOverloadedThreshold) {
                continue;
            }
            result.add(sample);
        }
        result.sort(Comparator.comparingInt(ZoneSample::total).reversed());
        return result.size() > max ? List.copyOf(result.subList(0, max)) : result;
    }

    /** New critical zone: chat + sound for everyone with moblimit.admin. */
    private void maybeAlert(ZoneSample sample) {
        long cooldownMs = plugin.cfg().zoneAlertCooldownSeconds * 1000L;
        long now = System.currentTimeMillis();
        Long last = lastAlertAt.get(sample.key());
        if (last != null && now - last < cooldownMs) {
            return;
        }
        lastAlertAt.put(sample.key(), now);
        Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.hasPermission("moblimit.admin")) {
                    continue;
                }
                plugin.msg().send(online, "zone-critical-alert",
                        "<red>\u26a0 \u041a\u0440\u0438\u0442\u0438\u0447\u0435\u0441\u043a\u0430\u044f \u0437\u043e\u043d\u0430: <world> [<x>, <z>] \u2014 <total> \u0441\u0443\u0449\u043d\u043e\u0441\u0442\u0435\u0439!</red>",
                        Placeholder.unparsed("world", sample.key().world()),
                        Placeholder.unparsed("x", String.valueOf((int) sample.x())),
                        Placeholder.unparsed("z", String.valueOf((int) sample.z())),
                        Placeholder.unparsed("total", String.valueOf(sample.total())));
                plugin.msg().playSound(online, plugin.cfg().alertSound);
            }
        });
    }
}
