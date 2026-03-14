package ru.privatenull.listeners;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public record AnimationSelectHolder(String caseName) implements InventoryHolder {
    @Override
    public Inventory getInventory() { return null; }
}