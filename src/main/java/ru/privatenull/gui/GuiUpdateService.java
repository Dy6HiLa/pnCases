package ru.privatenull.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.PnCasesPlugin;
import ru.privatenull.util.InventoryViewCompat;

import java.lang.reflect.Method;

/** Sends an in-place GUI slot update through ProtocolLib when it is available. */
public final class GuiUpdateService {

    private final PnCasesPlugin plugin;
    private Object protocolManager;
    private Object setSlotPacketType;

    public GuiUpdateService(PnCasesPlugin plugin) {
        this.plugin = plugin;
        connectProtocolLib();
    }

    public void setTopSlot(Player player, int slot, ItemStack item) {
        if (player == null || slot < 0) return;
        Inventory top = InventoryViewCompat.topInventory(player);
        if (top == null || slot >= top.getSize()) return;

        ItemStack copy = item == null ? null : item.clone();
        top.setItem(slot, copy);
        if (!sendSetSlot(player, slot, copy)) {
            player.updateInventory();
        }
    }

    private void connectProtocolLib() {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("ProtocolLib")) return;
        try {
            Class<?> library = Class.forName("com.comphenix.protocol.ProtocolLibrary");
            Class<?> serverPackets = Class.forName("com.comphenix.protocol.PacketType$Play$Server");
            protocolManager = library.getMethod("getProtocolManager").invoke(null);
            setSlotPacketType = serverPackets.getField("SET_SLOT").get(null);
            plugin.getLogger().info("ProtocolLib подключён: GUI pnCases обновляются без переоткрытия окна.");
        } catch (ReflectiveOperationException exception) {
            protocolManager = null;
            setSlotPacketType = null;
            plugin.getLogger().warning("ProtocolLib найден, но SET_SLOT недоступен; используется Bukkit-обновление GUI.");
        }
    }

    private boolean sendSetSlot(Player player, int slot, ItemStack item) {
        if (protocolManager == null || setSlotPacketType == null) return false;
        try {
            Object packet = createPacket();
            Object integers = packet.getClass().getMethod("getIntegers").invoke(packet);
            int count = (int) integers.getClass().getMethod("size").invoke(integers);
            if (count < 2) return false;

            int windowId = (int) player.getOpenInventory().getClass().getMethod("getWindowId")
                    .invoke(player.getOpenInventory());
            write(integers, 0, windowId);
            if (count >= 3) write(integers, 1, 0);
            write(integers, count - 1, slot);

            Object items = packet.getClass().getMethod("getItemModifier").invoke(packet);
            write(items, 0, item);
            for (Method method : protocolManager.getClass().getMethods()) {
                if (method.getName().equals("sendServerPacket") && method.getParameterCount() == 2) {
                    method.invoke(protocolManager, player, packet);
                    return true;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Packet layouts differ by Minecraft version. Bukkit is the compatibility fallback.
        }
        return false;
    }

    private Object createPacket() throws ReflectiveOperationException {
        for (Method method : protocolManager.getClass().getMethods()) {
            if (method.getName().equals("createPacket") && method.getParameterCount() == 1) {
                return method.invoke(protocolManager, setSlotPacketType);
            }
        }
        throw new NoSuchMethodException("ProtocolManager#createPacket");
    }

    private static void write(Object modifier, int index, Object value) throws ReflectiveOperationException {
        modifier.getClass().getMethod("write", int.class, Object.class).invoke(modifier, index, value);
    }
}
