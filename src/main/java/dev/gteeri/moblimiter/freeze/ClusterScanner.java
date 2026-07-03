package dev.gteeri.moblimiter.freeze;

import dev.gteeri.moblimiter.MobLimiterPlugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Player-centric crowd scanner (Folia region-safe).
 *
 * Entities only tick near players, so we scan around each player instead of
 * the whole world (which is not even safely possible across regions on Folia).
 * Density is a sliding window: neighbours within density-radius around each
 * mob. Chunk/region borders give no advantage to border farms.
 */
public final class ClusterScanner {

    private record Bucket(int x, int y, int z) {
    }

    private final MobLimiterPlugin plugin;
    private volatile ScheduledTask task;

    public ClusterScanner(MobLimiterPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        long periodTicks = plugin.cfg().scanIntervalSeconds * 20L;
        this.task = Bukkit.getGlobalRegionScheduler()
                .runAtFixedRate(plugin, t -> dispatch(), 60L, periodTicks);
    }

    public void stop() {
        ScheduledTask current = task;
        if (current != null) {
            current.cancel();
        }
        task = null;
    }

    public void restart() {
        stop();
        start();
    }

    /** Global tick: only fans work out to each player's region thread. */
    private void dispatch() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.getScheduler().run(plugin, t -> scanAround(player), null);
        }
    }

    /** Runs on the player's region thread. */
    private void scanAround(Player player) {
        if (!player.isValid() || !player.isOnline()) {
            return;
        }
        scanAt(player.getLocation());
    }

    /**
     * Runs one crowd scan/freeze pass around an arbitrary location. Used by the
     * periodic player-centric scanner, and by the "/moblimit scan" admin
     * command (an ops/testing utility to exercise this logic without needing a
     * player physically present, e.g. in headless CI integration tests).
     * Must run on the region thread that owns the location; callers driving
     * this off the main/global thread must schedule via the region scheduler.
     */
    public void scanAt(Location origin) {
        var cfg = plugin.cfg();
        double radius = cfg.scanRadius;
        List<Entity> nearby = origin.getWorld().getNearbyEntities(origin, radius, radius, radius);

        plugin.zones().record(origin.getWorld(), nearby);

        if (!cfg.freezeEnabled) {
            return;
        }

        FreezeManager freezer = plugin.freezer();
        double cellSize = cfg.densityRadius;
        double radiusSq = cfg.densityRadius * cfg.densityRadius;

        List<Mob> mobs = new ArrayList<>();
        Map<Bucket, List<Mob>> buckets = new HashMap<>();
        for (Entity entity : nearby) {
            if (entity instanceof Mob mob) {
                mobs.add(mob);
                buckets.computeIfAbsent(bucketOf(mob, cellSize), b -> new ArrayList<>()).add(mob);
            }
        }

        for (Mob mob : mobs) {
            boolean frozenNow = freezer.isFrozen(mob);

            boolean professionVillager = cfg.freezeVillagersWithProfession
                    && mob instanceof Villager villager
                    && !villager.getProfession().equals(Villager.Profession.NONE);
            if (professionVillager) {
                // Villagers with a job freeze unconditionally (trading still works).
                // Villagers without a job fall through to the density logic below,
                // so a profession reset wakes them up unless they are in a crowd.
                if (!frozenNow && !freezer.isOnRefreezeCooldown(mob)) {
                    freezer.freezeLater(mob);
                } else if (frozenNow) {
                    freezer.reinforceLater(mob);
                }
                continue;
            }

            int neighbours = countNeighbours(mob, buckets, cellSize, radiusSq);
            if (!frozenNow) {
                if (neighbours >= cfg.freezeThreshold
                        && !freezer.isOnRefreezeCooldown(mob)
                        && !freezer.isExemptFromDensityFreeze(mob)) {
                    freezer.freezeLater(mob);
                }
            } else {
                // Hysteresis: wake up only when the crowd clearly thinned out.
                if (neighbours <= cfg.unfreezeThreshold || freezer.isExemptFromDensityFreeze(mob)) {
                    freezer.unfreezeLater(mob, 0L);
                } else {
                    freezer.reinforceLater(mob);
                }
            }
        }
    }

    private Bucket bucketOf(Mob mob, double cellSize) {
        var location = mob.getLocation();
        return new Bucket(
                (int) Math.floor(location.getX() / cellSize),
                (int) Math.floor(location.getY() / cellSize),
                (int) Math.floor(location.getZ() / cellSize));
    }

    /** Spatial-hash neighbour count: O(n*k) instead of O(n^2). */
    private int countNeighbours(Mob mob, Map<Bucket, List<Mob>> buckets, double cellSize, double radiusSq) {
        Bucket center = bucketOf(mob, cellSize);
        var origin = mob.getLocation();
        int count = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    List<Mob> cell = buckets.get(new Bucket(center.x() + dx, center.y() + dy, center.z() + dz));
                    if (cell == null) {
                        continue;
                    }
                    for (Mob other : cell) {
                        if (other == mob) {
                            continue;
                        }
                        if (other.getLocation().distanceSquared(origin) <= radiusSq) {
                            count++;
                        }
                    }
                }
            }
        }
        return count;
    }
}
