package ru.privatenull.gui.caseview;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class CaseGuiHolder implements InventoryHolder {

    public enum Type { CASE, PREVIEW }

    private final Type type;
    private final String caseName;
    private final int page;

    private CaseGuiHolder(Type type, String caseName, int page) {
        this.type = type;
        this.caseName = caseName;
        this.page = Math.max(0, page);
    }

    public static CaseGuiHolder caseGui(String caseName) {
        return new CaseGuiHolder(Type.CASE, caseName, 0);
    }

    public static CaseGuiHolder previewGui(String caseName, int page) {
        return new CaseGuiHolder(Type.PREVIEW, caseName, page);
    }

    public Type type() { return type; }
    public String caseName() { return caseName; }
    public int page() { return page; }

    @Override
    public Inventory getInventory() { return null; }
}
