package ru.privatenull.gui.machine;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.cases.CaseManager;
import ru.privatenull.util.InventoryViewCompat;

import java.util.Objects;
import java.util.function.Consumer;

/** Keeps setting changes inside the currently open Machine inventory. */
final class MachineScreenRefresh {

    private MachineScreenRefresh() {
    }

    static boolean refreshIfOpen(
            CaseManager caseManager,
            Player player,
            MachineGuiHolder.Type expectedType,
            String caseName,
            Consumer<Inventory> renderer
    ) {
        Inventory inventory = InventoryViewCompat.topInventory(player);
        if (inventory == null || !(inventory.getHolder() instanceof MachineGuiHolder holder)) return false;
        if (holder.type() != expectedType || !holder.caseName().equalsIgnoreCase(caseName)) return false;

        caseManager.getPlugin().getGuiOpenAnimations().cancel(player);
        Inventory rendered = Bukkit.createInventory(holder, inventory.getSize());
        renderer.accept(rendered);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack previous = inventory.getItem(slot);
            ItemStack current = rendered.getItem(slot);
            if (!Objects.equals(previous, current)) {
                caseManager.getPlugin().getGuiUpdates().setTopSlot(player, slot, current);
            }
        }
        return true;
    }
}
