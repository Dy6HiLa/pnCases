package ru.privatenull.util;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public final class InventoryViewCompat {

    private InventoryViewCompat() {
    }

    public static Inventory topInventory(Player player) {
        if (player == null) {
            return null;
        }
        return player.getOpenInventory().getTopInventory();
    }
}
