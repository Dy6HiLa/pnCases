package ru.privatenull.listeners;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class CaseGuiHolder implements InventoryHolder {

    public enum Type { CASE }

    private final Type type;
    private final String caseName;

    private CaseGuiHolder(Type type, String caseName) {
        this.type = type;
        this.caseName = caseName;
    }

    public static CaseGuiHolder caseGui(String caseName) {
        return new CaseGuiHolder(Type.CASE, caseName);
    }

    public Type type() { return type; }
    public String caseName() { return caseName; }

    @Override
    public Inventory getInventory() { return null; }
}
