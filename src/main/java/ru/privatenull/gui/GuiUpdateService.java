package ru.privatenull.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.PnCasesPlugin;
import ru.privatenull.util.InventoryViewCompat;

/** Applies server-backed, in-place slot changes without reopening the GUI. */
public final class GuiUpdateService {

    public GuiUpdateService(PnCasesPlugin plugin) {
        // Kept as a plugin service so every GUI uses one update path. Bukkit's
        // Inventory#setItem already emits the correct version-specific SET_SLOT
        // packet (including container id and state id) and remains compatible
        // with ProtocolLib. Building a second packet manually would duplicate
        // that update and can desynchronise modern container state ids.
    }

    public void setTopSlot(Player player, int slot, ItemStack item) {
        if (player == null || slot < 0) return;
        Inventory top = InventoryViewCompat.topInventory(player);
        if (top == null || slot >= top.getSize()) return;
        top.setItem(slot, item == null ? null : item.clone());
    }
}
