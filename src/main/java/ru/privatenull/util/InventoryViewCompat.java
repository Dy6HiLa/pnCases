package ru.privatenull.util;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.lang.reflect.Method;

public final class InventoryViewCompat {

    private InventoryViewCompat() {
    }

    public static Inventory topInventory(Player player) {
        if (player == null) {
            return null;
        }
        try {
            Object view = player.getOpenInventory();
            Method method = view.getClass().getMethod("getTopInventory");
            Object result = method.invoke(view);
            return result instanceof Inventory inventory ? inventory : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
