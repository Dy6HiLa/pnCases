package ru.privatenull.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

public final class VisualEntity {

    private static final double ARMOR_STAND_HEAD_OFFSET = 1.55;
    private static final double ARMOR_STAND_TEXT_OFFSET = 0.20;

    private final Entity entity;
    private final Kind kind;
    private Location visualLocation;

    private VisualEntity(Entity entity, Kind kind, Location visualLocation) {
        this.entity = entity;
        this.kind = kind;
        this.visualLocation = visualLocation == null ? null : visualLocation.clone();
    }

    public static VisualEntity item(Location location, ItemStack itemStack) {
        ArmorStand armorStand = spawnBaseArmorStand(location.clone().subtract(0.0, ARMOR_STAND_HEAD_OFFSET, 0.0), true);
        setHelmet(armorStand, sanitizeItem(itemStack, Material.NETHER_STAR));
        return new VisualEntity(armorStand, Kind.BLOCK, location);
    }

    public static VisualEntity block(Location location, Material material) {
        ArmorStand armorStand = spawnBaseArmorStand(location.clone().subtract(0.0, ARMOR_STAND_HEAD_OFFSET, 0.0), true);
        setHelmet(armorStand, new ItemStack(material == null || material.isAir() ? Material.CHEST : material));
        return new VisualEntity(armorStand, Kind.BLOCK, location);
    }

    public static VisualEntity text(Location location, String text) {
        ArmorStand armorStand = spawnBaseArmorStand(location.clone().subtract(0.0, ARMOR_STAND_TEXT_OFFSET, 0.0), true);
        armorStand.setCustomName(text == null ? "" : text);
        armorStand.setCustomNameVisible(true);
        return new VisualEntity(armorStand, Kind.TEXT, location);
    }

    public Entity entity() {
        return entity;
    }

    public boolean isValid() {
        return entity != null && entity.isValid() && !entity.isDead();
    }

    public boolean isDead() {
        return entity == null || entity.isDead();
    }

    public Location getLocation() {
        if (visualLocation != null) {
            return visualLocation.clone();
        }
        return entity == null ? null : entity.getLocation();
    }

    public void teleport(Location location) {
        if (entity == null || location == null) {
            return;
        }
        visualLocation = location.clone();
        if (kind == Kind.BLOCK) {
            entity.teleport(location.clone().subtract(0.0, ARMOR_STAND_HEAD_OFFSET, 0.0));
        } else if (kind == Kind.TEXT) {
            entity.teleport(location.clone().subtract(0.0, ARMOR_STAND_TEXT_OFFSET, 0.0));
        } else {
            entity.teleport(location);
        }
    }

    public void setRotation(float yaw, float pitch) {
        if (entity != null) {
            entity.setRotation(yaw, pitch);
        }
    }

    public void setScale(float scale) {
        if (entity instanceof ArmorStand armorStand) {
            armorStand.setSmall(scale > 0.0f && scale < 0.72f);
        }
    }

    public void setItem(ItemStack itemStack) {
        if (entity instanceof ArmorStand armorStand) {
            setHelmet(armorStand, sanitizeItem(itemStack, Material.CHEST));
        }
    }

    public void setBlock(Material material) {
        Material safeMaterial = material == null || material.isAir() ? Material.CHEST : material;
        setItem(new ItemStack(safeMaterial));
    }

    public void setText(String text) {
        if (entity instanceof ArmorStand armorStand) {
            armorStand.setCustomName(text == null ? "" : text);
            armorStand.setCustomNameVisible(true);
        }
    }

    public void addScoreboardTag(String tag) {
        if (entity != null && tag != null && !tag.isBlank()) {
            entity.addScoreboardTag(tag);
        }
    }

    public void remove() {
        if (entity != null && entity.isValid()) {
            entity.remove();
        }
    }

    private static ArmorStand spawnBaseArmorStand(Location entityLocation, boolean marker) {
        World world = requireWorld(entityLocation);
        ArmorStand armorStand = (ArmorStand) world.spawnEntity(entityLocation, EntityType.ARMOR_STAND);
        armorStand.setVisible(false);
        armorStand.setGravity(false);
        armorStand.setMarker(marker);
        armorStand.setCustomName("");
        armorStand.setCustomNameVisible(false);
        armorStand.setCollidable(false);
        armorStand.setSilent(true);
        armorStand.setInvulnerable(true);
        armorStand.setPersistent(false);
        armorStand.setBasePlate(false);
        armorStand.setArms(false);
        return armorStand;
    }

    private static void setHelmet(ArmorStand armorStand, ItemStack itemStack) {
        EntityEquipment equipment = armorStand.getEquipment();
        if (equipment != null) {
            equipment.setHelmet(itemStack);
        }
    }

    private static ItemStack sanitizeItem(ItemStack itemStack, Material fallback) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return new ItemStack(fallback == null ? Material.STONE : fallback);
        }
        ItemStack clone = itemStack.clone();
        clone.setAmount(1);
        return clone;
    }

    private static World requireWorld(Location location) {
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("Location must have world");
        }
        return location.getWorld();
    }

    private enum Kind {
        BLOCK,
        TEXT
    }
}
