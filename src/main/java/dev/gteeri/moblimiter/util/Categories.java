package dev.gteeri.moblimiter.util;

import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Vehicle;

import java.util.Locale;

/** Entity categories used by area limits and hot-zone stats. */
public final class Categories {

    public enum Category {
        MOBS("mobs"),
        ITEM_FRAMES("item-frames"),
        ARMOR_STANDS("armor-stands"),
        PAINTINGS("paintings"),
        VEHICLES("vehicles");

        private final String key;

        Category(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

    private Categories() {
    }

    public static Category byKey(String key) {
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        for (Category category : Category.values()) {
            if (category.key().equals(normalized)) {
                return category;
            }
        }
        return null;
    }

    /** Category of an existing entity, or null when the entity is not limited. */
    public static Category categorize(Entity entity) {
        if (entity instanceof ItemFrame) {
            return Category.ITEM_FRAMES;
        }
        if (entity instanceof Painting) {
            return Category.PAINTINGS;
        }
        if (entity instanceof ArmorStand) {
            return Category.ARMOR_STANDS;
        }
        if (entity instanceof Vehicle && !(entity instanceof LivingEntity)) {
            return Category.VEHICLES;
        }
        if (entity instanceof Mob) {
            return Category.MOBS;
        }
        return null;
    }

    /** Category of the entity that an item would place/spawn, or null. */
    public static Category categorizeMaterial(Material material) {
        String name = material.name();
        if (name.endsWith("_SPAWN_EGG")) {
            return Category.MOBS;
        }
        if (name.endsWith("_BOAT") || name.endsWith("_RAFT") || name.endsWith("MINECART")) {
            return Category.VEHICLES;
        }
        return switch (material) {
            case ARMOR_STAND -> Category.ARMOR_STANDS;
            case ITEM_FRAME, GLOW_ITEM_FRAME -> Category.ITEM_FRAMES;
            case PAINTING -> Category.PAINTINGS;
            default -> null;
        };
    }

    public static boolean isSpawnEgg(Material material) {
        return material.name().endsWith("_SPAWN_EGG");
    }
}
