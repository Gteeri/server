package dev.gteeri.moblimiter.selftest;

import dev.gteeri.moblimiter.MobLimiterPlugin;
import dev.gteeri.moblimiter.limits.LimitService;
import dev.gteeri.moblimiter.pets.PetManager;
import dev.gteeri.moblimiter.util.Categories;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Ops/testing utility: exercises the core limiter and pet-ledger logic
 * in-process (no real entities or player interactions required) and returns
 * pass/fail report lines. Wired to the admin-only "/moblimit selftest"
 * command. Intended for headless CI integration tests, where simulating real
 * player placements (armor stands, item frames, taming) is not possible over
 * RCON.
 *
 * Uses checkQuiet so runs do not pollute the gameplay statistics shown in the
 * stats GUI. Must be invoked on the region thread owning the given location
 * (the command wiring schedules this correctly).
 */
public final class SelfTest {

    private SelfTest() {
    }

    public static List<String> run(MobLimiterPlugin plugin, Location location) {
        List<String> lines = new ArrayList<>();
        for (Categories.Category category : Categories.Category.values()) {
            testLimitCategory(plugin, lines, location, category);
        }
        testPetsPerType(plugin, lines);
        testPetsTotal(plugin, lines);
        long failCount = lines.stream().filter(l -> l.startsWith("FAIL")).count();
        lines.add(failCount == 0 ? "SELFTEST RESULT: ALL PASSED" : "SELFTEST RESULT: " + failCount + " FAILURE(S)");
        return lines;
    }

    private static void testLimitCategory(MobLimiterPlugin plugin, List<String> lines, Location location,
                                           Categories.Category category) {
        var cfg = plugin.cfg();
        Integer limit = cfg.categoryLimits.get(category);
        if (limit == null || limit < 0) {
            lines.add("SKIP limit:" + category.key() + " (no limit configured)");
            return;
        }
        LimitService service = plugin.limits();
        int baseline = service.count(location, category);
        if (baseline != 0) {
            lines.add("FAIL limit:" + category.key() + " baseline not empty (" + baseline
                    + "); pick a cleaner test area");
            return;
        }
        for (int i = 0; i < limit; i++) {
            LimitService.Check check = service.checkQuiet(location, category);
            if (check.overLimit()) {
                lines.add("FAIL limit:" + category.key() + " blocked too early at i=" + i
                        + " (limit=" + limit + ")");
                return;
            }
            if (check.count() != i) {
                lines.add("FAIL limit:" + category.key() + " unexpected count at i=" + i
                        + ": got " + check.count());
                return;
            }
        }
        LimitService.Check overflow = service.checkQuiet(location, category);
        if (!overflow.overLimit()) {
            lines.add("FAIL limit:" + category.key() + " did not block at the configured limit (" + limit + ")");
            return;
        }
        if (overflow.count() != limit) {
            lines.add("FAIL limit:" + category.key() + " overLimit count mismatch: got " + overflow.count()
                    + " expected " + limit);
            return;
        }
        lines.add("PASS limit:" + category.key() + " (limit=" + limit + ")");
    }

    private static void testPetsPerType(MobLimiterPlugin plugin, List<String> lines) {
        var cfg = plugin.cfg();
        PetManager pets = plugin.pets();
        if (cfg.petTypeLimits.isEmpty()) {
            lines.add("SKIP pets:per-type (no per-type limits configured)");
            return;
        }
        EntityType type = cfg.petTypeLimits.keySet().iterator().next();
        int limit = cfg.petTypeLimits.get(type);
        UUID owner = UUID.randomUUID();
        List<UUID> petIds = new ArrayList<>();
        boolean ok = true;
        for (int i = 0; i < limit; i++) {
            PetManager.TameCheck check = pets.canTame(owner, type);
            if (!check.allowed()) {
                lines.add("FAIL pets:" + type + " blocked too early at i=" + i);
                ok = false;
                break;
            }
            UUID petId = UUID.randomUUID();
            pets.register(owner, type, petId);
            petIds.add(petId);
        }
        if (ok) {
            PetManager.TameCheck overflow = pets.canTame(owner, type);
            if (overflow.allowed()) {
                lines.add("FAIL pets:" + type + " did not block at type limit (" + limit + ")");
                ok = false;
            }
        }
        for (UUID petId : petIds) {
            pets.unregister(owner, petId);
        }
        if (pets.total(owner) != 0) {
            lines.add("FAIL pets:" + type + " ledger did not clean up to zero");
            ok = false;
        }
        if (ok) {
            lines.add("PASS pets:per-type (" + type + ", limit=" + limit + ")");
        }
    }

    private static void testPetsTotal(MobLimiterPlugin plugin, List<String> lines) {
        var cfg = plugin.cfg();
        PetManager pets = plugin.pets();
        if (cfg.petTotalLimit < 0) {
            lines.add("SKIP pets:total-limit (unlimited)");
            return;
        }
        EntityType type = Arrays.stream(EntityType.values())
                .filter(t -> !cfg.petTypeLimits.containsKey(t))
                .findFirst()
                .orElse(null);
        if (type == null) {
            lines.add("SKIP pets:total-limit (no unconfigured entity type available)");
            return;
        }
        UUID owner = UUID.randomUUID();
        List<UUID> petIds = new ArrayList<>();
        boolean ok = true;
        for (int i = 0; i < cfg.petTotalLimit; i++) {
            PetManager.TameCheck check = pets.canTame(owner, type);
            if (!check.allowed()) {
                lines.add("FAIL pets:total-limit blocked too early at i=" + i);
                ok = false;
                break;
            }
            UUID petId = UUID.randomUUID();
            pets.register(owner, type, petId);
            petIds.add(petId);
        }
        if (ok) {
            PetManager.TameCheck overflow = pets.canTame(owner, type);
            if (overflow.allowed()) {
                lines.add("FAIL pets:total-limit did not block at total limit (" + cfg.petTotalLimit + ")");
                ok = false;
            }
        }
        for (UUID petId : petIds) {
            pets.unregister(owner, petId);
        }
        if (pets.total(owner) != 0) {
            lines.add("FAIL pets:total-limit ledger did not clean up to zero");
            ok = false;
        }
        if (ok) {
            lines.add("PASS pets:total-limit (limit=" + cfg.petTotalLimit + ")");
        }
    }
}
