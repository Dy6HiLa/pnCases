package ru.privatenull.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Locale;

public final class ItemNames {

    private ItemNames() {
    }

    public static String readableItemName(ItemStack item) {
        if (item == null) return "нет";
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) return meta.getDisplayName();
        return readableMaterialName(item.getType());
    }

    public static String readableMaterialName(Material material) {
        if (material == null) return "нет";
        if ("ECHO_SHARD".equals(material.name())) return "Эхо-осколок";
        return switch (material) {
            case BARRIER -> "Барьер";
            case CHEST -> "Сундук";
            case ENDER_CHEST -> "Эндер-сундук";
            case TRAPPED_CHEST -> "Сундук-ловушка";
            case GRAY_STAINED_GLASS_PANE -> "Серая стеклянная панель";
            case BLACK_STAINED_GLASS_PANE -> "Чёрная стеклянная панель";
            case LIME_DYE -> "Лаймовый краситель";
            case GRAY_DYE -> "Серый краситель";
            case EXPERIENCE_BOTTLE -> "Пузырёк опыта";
            case GLASS_BOTTLE -> "Пустая бутылочка";
            case ARMOR_STAND -> "Стойка для брони";
            case CRAFTING_TABLE -> "Верстак";
            case NAME_TAG -> "Бирка";
            case OAK_SIGN -> "Дубовая табличка";
            case ENDER_EYE -> "Око Эндера";
            case NETHER_STAR -> "Звезда Незера";
            case CLOCK -> "Часы";
            case COMPASS -> "Компас";
            case REPEATER -> "Повторитель";
            case COMPARATOR -> "Компаратор";
            case FEATHER -> "Перо";
            case EMERALD_BLOCK -> "Изумрудный блок";
            case WRITABLE_BOOK -> "Книга с пером";
            case EMERALD -> "Изумруд";
            case GOLD_INGOT -> "Золотой слиток";
            case DIAMOND -> "Алмаз";
            case NETHERITE_SWORD -> "Незеритовый меч";
            case DIAMOND_SWORD -> "Алмазный меч";
            case IRON_SWORD -> "Железный меч";
            case GOLDEN_SWORD -> "Золотой меч";
            case STONE_SWORD -> "Каменный меч";
            case WOODEN_SWORD -> "Деревянный меч";
            case NETHERITE_INGOT -> "Незеритовый слиток";
            case NETHERITE_HELMET -> "Незеритовый шлем";
            case NETHERITE_CHESTPLATE -> "Незеритовый нагрудник";
            case NETHERITE_LEGGINGS -> "Незеритовые поножи";
            case NETHERITE_BOOTS -> "Незеритовые ботинки";
            case ANVIL -> "Наковальня";
            case TNT -> "Динамит";
            case SLIME_BALL -> "Сгусток слизи";
            case ARROW -> "Стрела";
            default -> humanizeMaterial(material.name());
        };
    }

    static String humanizeMaterial(String name) {
        String value = name.toLowerCase(Locale.ROOT).replace('_', ' ');
        if (value.isEmpty()) return value;
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
