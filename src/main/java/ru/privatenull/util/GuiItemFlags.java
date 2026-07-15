package ru.privatenull.util;

import com.google.common.collect.ImmutableMultimap;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;

/** Compatibility flags for items shown only inside inventories. */
public final class GuiItemFlags {

    private GuiItemFlags() {
    }

    public static void hideAttributes(ItemMeta meta) {
        if (meta == null) return;
        // An explicit empty component prevents modern clients from showing a
        // tool's default attack damage and attack speed in GUI copies.
        meta.setAttributeModifiers(ImmutableMultimap.of());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        // Modern Paper uses a separate component tooltip flag as well.
        try {
            meta.addItemFlags(ItemFlag.valueOf("HIDE_ADDITIONAL_TOOLTIP"));
        } catch (IllegalArgumentException ignored) {
            // The flag does not exist on pre-1.20.5 servers.
        }
    }
}
