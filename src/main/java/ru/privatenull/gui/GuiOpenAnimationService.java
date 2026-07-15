package ru.privatenull.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import ru.privatenull.PnCasesPlugin;
import ru.privatenull.util.InventoryViewCompat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Reveals GUI contents from the centre outwards instead of opening a full inventory at once. */
public final class GuiOpenAnimationService {

    private static final int ITEMS_PER_TICK = 6;

    private final PnCasesPlugin plugin;
    private final Map<UUID, BukkitTask> tasks = new HashMap<>();

    public GuiOpenAnimationService(PnCasesPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Inventory inventory) {
        cancel(player);
        List<SlotItem> items = snapshot(inventory);
        inventory.clear();
        player.openInventory(inventory);
        if (items.isEmpty()) return;

        int[] cursor = {0};
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (InventoryViewCompat.topInventory(player) != inventory) {
                cancel(player);
                return;
            }
            for (int count = 0; count < ITEMS_PER_TICK && cursor[0] < items.size(); count++) {
                SlotItem next = items.get(cursor[0]++);
                plugin.getGuiUpdates().setTopSlot(player, next.slot(), next.item());
            }
            if (cursor[0] >= items.size()) {
                cancel(player);
            }
        }, 1L, 1L);
        tasks.put(player.getUniqueId(), task);
    }

    public void cancel(Player player) {
        if (player == null) return;
        BukkitTask task = tasks.remove(player.getUniqueId());
        if (task != null) task.cancel();
    }

    public void shutdown() {
        for (BukkitTask task : tasks.values()) task.cancel();
        tasks.clear();
    }

    private static List<SlotItem> snapshot(Inventory inventory) {
        List<SlotItem> result = new ArrayList<>();
        double centerX = 4.0;
        double centerY = (inventory.getSize() / 9 - 1) / 2.0;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && !item.getType().isAir()) result.add(new SlotItem(slot, item.clone()));
        }
        result.sort(Comparator.comparingDouble(entry -> {
            double x = entry.slot() % 9;
            double y = entry.slot() / 9;
            return Math.max(Math.abs(x - centerX), Math.abs(y - centerY));
        }));
        return result;
    }

    private record SlotItem(int slot, ItemStack item) {
    }
}
