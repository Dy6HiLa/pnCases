package ru.privatenull.listeners;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import ru.privatenull.cases.CaseManager;
import ru.privatenull.cases.animation.AnimationType;
import ru.privatenull.cases.model.CaseDefinition;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CaseGuiListener implements Listener {

    private final CaseManager caseManager;
    private final Set<UUID> clickLock = ConcurrentHashMap.newKeySet();

    public CaseGuiListener(CaseManager caseManager) {
        this.caseManager = caseManager;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        if (e.getInventory().getHolder() instanceof AnimationSelectHolder selHolder) {
            e.setCancelled(true);
            handleAnimationSelectClick(p, selHolder, e.getRawSlot());
            return;
        }

        if (!(e.getInventory().getHolder() instanceof CaseGuiHolder holder)) return;
        e.setCancelled(true);

        int slot = e.getRawSlot();

        if (slot == 49) {
            openAnimationSelectGui(p, holder.caseName());
            return;
        }

        if (slot != 22) return;

        CaseDefinition def = caseManager.getCaseByName(holder.caseName());
        if (def == null) return;

        UUID uuid = p.getUniqueId();
        if (clickLock.contains(uuid)) return;
        clickLock.add(uuid);

        if (def.costType() != CaseDefinition.CostType.KEY) {
            clickLock.remove(uuid);
            caseManager.tryOpenCase(p, def);
            return;
        }

        String keyId = def.costKeyId();
        int need = Math.max(1, def.costAmount());
        int have = (keyId == null) ? 0 : caseManager.getPlugin().getKeyStorage().get(uuid, keyId);

        if (have >= need) {
            clickLock.remove(uuid);
            caseManager.tryOpenCase(p, def);
            return;
        }

        int buyLevels = Math.max(0, def.buyKeyWithXpLevels());
        boolean canBuy = buyLevels > 0 && p.getLevel() >= buyLevels && keyId != null && !keyId.isEmpty();

        if (canBuy) {
            int give = need - have;
            p.setLevel(p.getLevel() - buyLevels);
            caseManager.getPlugin().getKeyStorage().add(uuid, keyId, give);
            e.getInventory().setItem(22, pane(Material.LIME_STAINED_GLASS_PANE,
                    caseManager.getPlugin().getMessages().get("gui-buy-success"), Collections.emptyList()));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
            scheduleRefresh(p, holder.caseName(), def, uuid, 30L);
            return;
        }

        String title;
        List<String> lore = new ArrayList<>();
        if (buyLevels <= 0) {
            title = caseManager.getPlugin().getMessages().get("gui-buy-unavailable");
            lore.add(caseManager.getPlugin().getMessages().get("gui-keys-balance",
                    "have", String.valueOf(have), "need", String.valueOf(need)));
        } else {
            title = caseManager.getPlugin().getMessages().get("gui-buy-no-levels");
        }

        e.getInventory().setItem(22, pane(Material.RED_STAINED_GLASS_PANE, title, lore));
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
        scheduleRefresh(p, holder.caseName(), def, uuid, 30L);
    }

    private void openAnimationSelectGui(Player p, String caseName) {
        Inventory inv = Bukkit.createInventory(
                new AnimationSelectHolder(caseName),
                27,
                color("&8✦ Выбор анимации")
        );

        ItemStack filler = pane(Material.GRAY_STAINED_GLASS_PANE, " ", Collections.emptyList());
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        AnimationType[] types = AnimationType.values();
        int[] animSlots = {11, 12, 13, 14, 15, 16};
        AnimationType selected = caseManager.getPlayerAnimation(p.getUniqueId());

        for (int i = 0; i < types.length && i < animSlots.length; i++) {
            inv.setItem(animSlots[i], buildAnimationItem(types[i], types[i] == selected));
        }

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bm = back.getItemMeta();
        if (bm != null) {
            bm.setDisplayName(color("&7« Назад"));
            bm.setLore(List.of(color("&8Вернуться к кейсу")));
            back.setItemMeta(bm);
        }
        inv.setItem(22, back);

        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.6f, 1.4f);
    }

    private void handleAnimationSelectClick(Player p, AnimationSelectHolder holder, int slot) {
        if (slot == 22) {
            CaseDefinition def = caseManager.getCaseByName(holder.caseName());
            if (def != null) caseManager.openCaseGui(p, def);
            p.playSound(p.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.6f, 1.2f);
            return;
        }

        int[] animSlots = {11, 12, 13, 14, 15, 16};
        AnimationType[] types = AnimationType.values();

        for (int i = 0; i < types.length && i < animSlots.length; i++) {
            if (slot != animSlots[i]) continue;

            AnimationType chosen = types[i];
            if (caseManager.getPlayerAnimation(p.getUniqueId()) == chosen) return;

            caseManager.setPlayerAnimation(p.getUniqueId(), chosen);

            Inventory inv = p.getOpenInventory().getTopInventory();
            for (int j = 0; j < types.length && j < animSlots.length; j++) {
                inv.setItem(animSlots[j], buildAnimationItem(types[j], types[j] == chosen));
            }

            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.3f);
            p.sendMessage(caseManager.getPlugin().getMessages().get("animation-changed", "animation", chosen.displayName()));
            return;
        }
    }

    private ItemStack buildAnimationItem(AnimationType type, boolean selected) {
        ItemStack it = new ItemStack(type.icon());
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;

        meta.setDisplayName((selected ? "§a✔ " : "§7") + type.displayName());

        List<String> lore = new ArrayList<>();
        lore.add(" ");
        for (String line : type.description().split("\n")) {
            lore.add(color(line));
        }
        lore.add(" ");
        lore.add(selected ? color("&a&l● Выбрана") : color("&7○ Нажми, чтобы выбрать"));

        meta.setLore(lore);

        if (selected) {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        }

        it.setItemMeta(meta);
        return it;
    }

    private void scheduleRefresh(Player p, String caseName, CaseDefinition def, UUID uuid, long delay) {
        new BukkitRunnable() {
            @Override
            public void run() {
                clickLock.remove(uuid);
                if (!p.isOnline()) return;
                if (!(p.getOpenInventory().getTopInventory().getHolder() instanceof CaseGuiHolder h2)) return;
                if (!Objects.equals(h2.caseName(), caseName)) return;
                p.getOpenInventory().getTopInventory().setItem(22, caseManager.buildGuiOpenItem(p, def));
                p.getOpenInventory().getTopInventory().setItem(49, caseManager.buildAnimationSelectorItem(p));
            }
        }.runTaskLater(caseManager.getPlugin(), delay);
    }

    private static ItemStack pane(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) { meta.setDisplayName(name); meta.setLore(lore); it.setItemMeta(meta); }
        return it;
    }

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }
}