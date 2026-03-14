package ru.privatenull.listeners;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public record CaseGuiHolder(String caseName) implements InventoryHolder {
    @Override
    public Inventory getInventory() {
        return null;
    }
}