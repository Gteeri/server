package dev.gteeri.moblimiter.limits;

import dev.gteeri.moblimiter.MobLimiterPlugin;
import dev.gteeri.moblimiter.util.Categories;
import org.bukkit.Location;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Area entity limits with a sliding window: the count is taken within a radius
 * around the exact spawn point, so farms split across chunk/region borders get
 * no advantage. A short-lived cache keeps natural spawn storms cheap; every
 * allowed spawn bumps all covering cache cells so bursts cannot overshoot the
 * limit while a cached count is still alive.
 */
public final class LimitService {

    public record Check(boolean overLimit, int count, int limit) {
    }

    private record CacheKey(String world, int cx, int cy, int cz, Categories.Category category) {
    }

    private record CacheEntry(int count, long expiresAt) {
    }

    private final MobLimiterPlugin plugin;
    private final Map<CacheKey, CacheEntry> cache = new ConcurrentHashMap<>();
    private volatile long lastHousekeeping;

    public LimitService(MobLimiterPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Whether spawning/placing one more entity of the category at this location
     * is allowed. Must be called on the location's region thread (event threads
     * already are). When allowed, cached counts are bumped immediately.
     */
    public Check check(Location location, Categories.Category category) {
        var cfg = plugin.cfg();
        Integer limit = cfg.categoryLimits.get(category);
        if (!cfg.limitsEnabled || limit == null || limit < 0) {
            return new Check(false, 0, -1);
        }
        int count = count(location, category);
        boolean over = count >= limit;
        if (!over) {
            bump(location, category);
        }
        return new Check(over, count, limit);
    }

    /** Count of limited entities around a location (with a short cache). */
    public int count(Location location, Categories.Category category) {
        long now = System.currentTimeMillis();
        CacheKey key = keyOf(location, category);
        CacheEntry cached = cache.get(key);
        if (cached != null && cached.expiresAt() > now) {
            return cached.count();
        }
        var cfg = plugin.cfg();
        int count = location.getWorld().getNearbyEntities(
                location, cfg.limitRadius, cfg.limitVerticalRadius, cfg.limitRadius,
                entity -> !entity.isDead() && Categories.categorize(entity) == category).size();
        long ttlMs = (long) (cfg.limitCacheSeconds * 1000.0);
        if (ttlMs > 0) {
            cache.put(key, new CacheEntry(count, now + ttlMs));
        }
        housekeeping(now);
        return count;
    }

    /**
     * Increment every cached cell whose counting window covers this location so
     * bursts near cell borders cannot overshoot the limit while a stale cached
     * count is still alive.
     */
    private void bump(Location location, Categories.Category category) {
        var cfg = plugin.cfg();
        String world = location.getWorld().getName();
        int minX = (int) Math.floor(location.getX() - cfg.limitRadius) >> 4;
        int maxX = (int) Math.floor(location.getX() + cfg.limitRadius) >> 4;
        int minY = (int) Math.floor(location.getY() - cfg.limitVerticalRadius) >> 4;
        int maxY = (int) Math.floor(location.getY() + cfg.limitVerticalRadius) >> 4;
        int minZ = (int) Math.floor(location.getZ() - cfg.limitRadius) >> 4;
        int maxZ = (int) Math.floor(location.getZ() + cfg.limitRadius) >> 4;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cache.computeIfPresent(new CacheKey(world, x, y, z, category),
                            (k, entry) -> new CacheEntry(entry.count() + 1, entry.expiresAt()));
                }
            }
        }
    }

    private CacheKey keyOf(Location location, Categories.Category category) {
        return new CacheKey(location.getWorld().getName(),
                location.getBlockX() >> 4, location.getBlockY() >> 4, location.getBlockZ() >> 4,
                category);
    }

    private void housekeeping(long now) {
        if (now - lastHousekeeping < 30_000L) {
            return;
        }
        lastHousekeeping = now;
        cache.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
    }

    public void invalidateCache() {
        cache.clear();
    }
}
