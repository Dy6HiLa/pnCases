package ru.privatenull.storage;

import org.bukkit.inventory.ItemStack;

import java.util.Base64;

final class ItemCodec {
    private ItemCodec() {
    }

    static String serialize(ItemStack item) {
        if (item == null) return null;
        try {
            return Base64.getEncoder().encodeToString(item.serializeAsBytes());
        } catch (Throwable ignored) {
            return null;
        }
    }

    static ItemStack deserialize(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(value));
        } catch (Throwable ignored) {
            return null;
        }
    }
}
