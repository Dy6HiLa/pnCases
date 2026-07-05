package ru.privatenull.listeners;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class MachineGuiHolder implements InventoryHolder {

    public enum Type { MAIN, ANIMATION, LAYOUT, HOLOGRAM, PARTICLES, MENU, PURCHASE }

    private final Type type;
    private final String caseName;

    private MachineGuiHolder(Type type, String caseName) {
        this.type = type;
        this.caseName = caseName;
    }

    public static MachineGuiHolder main(String caseName) {
        return new MachineGuiHolder(Type.MAIN, caseName);
    }

    public static MachineGuiHolder animation(String caseName) {
        return new MachineGuiHolder(Type.ANIMATION, caseName);
    }

    public static MachineGuiHolder layout(String caseName) {
        return new MachineGuiHolder(Type.LAYOUT, caseName);
    }

    public static MachineGuiHolder hologram(String caseName) {
        return new MachineGuiHolder(Type.HOLOGRAM, caseName);
    }

    public static MachineGuiHolder particles(String caseName) {
        return new MachineGuiHolder(Type.PARTICLES, caseName);
    }

    public static MachineGuiHolder menu(String caseName) {
        return new MachineGuiHolder(Type.MENU, caseName);
    }

    public static MachineGuiHolder purchase(String caseName) {
        return new MachineGuiHolder(Type.PURCHASE, caseName);
    }

    public Type type() {
        return type;
    }

    public String caseName() {
        return caseName;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
