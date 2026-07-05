package ru.privatenull.util;

import org.bukkit.enchantments.Enchantment;

public final class EnchantmentCompat {

    private EnchantmentCompat() {
    }

    @SuppressWarnings("deprecation")
    public static Enchantment unbreaking() {
        Enchantment enchantment = Enchantment.getByName("UNBREAKING");
        if (enchantment != null) {
            return enchantment;
        }
        return Enchantment.getByName("DURABILITY");
    }
}
