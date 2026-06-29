package ru.privatenull.listeners;

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
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

        if (e.getInventory().getHolder() instanceof AnimationSelectHolder holder) {
            e.setCancelled(true);
            handleAnimationSelectClick(p, holder, e.getRawSlot());
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
                    caseManager.getPlugin().getMessages().getOr("gui.buy.success", "gui-buy-success"), Collections.emptyList()));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
            scheduleRefresh(p, holder.caseName(), def, uuid, 30L);
            return;
        }

        String title;
        List<String> lore = new ArrayList<>();
        if (buyLevels <= 0) {
            title = caseManager.getPlugin().getMessages().getOr("gui.buy.unavailable", "gui-buy-unavailable");
            lore.add(caseManager.getPlugin().getMessages().getOr("gui.case-button.keys-balance", "gui-keys-balance",
                    "have", String.valueOf(have), "need", String.valueOf(need)));
        } else {
            title = caseManager.getPlugin().getMessages().getOr("gui.buy.no-levels", "gui-buy-no-levels");
        }

        e.getInventory().setItem(22, pane(Material.RED_STAINED_GLASS_PANE, title, lore));
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
        scheduleRefresh(p, holder.caseName(), def, uuid, 30L);
    }

    private void openAnimationSelectGui(Player p, String caseName) {
        Inventory inv = org.bukkit.Bukkit.createInventory(
                new AnimationSelectHolder(caseName),
                27,
                color("&8Выбор анимации")
        );

        ItemStack filler = pane(Material.GRAY_STAINED_GLASS_PANE, " ", Collections.emptyList());
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }

        AnimationType[] types = AnimationType.values();
        int[] slots = {10, 12, 14, 16};
        AnimationType selected = caseManager.getPlayerAnimation(p.getUniqueId());

        for (int i = 0; i < types.length && i < slots.length; i++) {
            inv.setItem(slots[i], buildAnimationItem(types[i], types[i] == selected));
        }

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta meta = back.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§x§A§0§E§F§A§1← §fНазад");
            meta.setLore(List.of(
                    "",
                    "§x§A§0§E§F§A§1 «Навигация»",
                    " §7- §fВернуться к кейсу",
                    ""
            ));
            back.setItemMeta(meta);
        }
        inv.setItem(22, back);

        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_BARREL_OPEN, 0.7f, 1.15f);
    }

    private void handleAnimationSelectClick(Player p, AnimationSelectHolder holder, int slot) {
        if (slot == 22) {
            CaseDefinition def = caseManager.getCaseByName(holder.caseName());
            if (def != null) {
                caseManager.openCaseGui(p, def);
            }
            p.playSound(p.getLocation(), Sound.BLOCK_BARREL_CLOSE, 0.7f, 0.95f);
            return;
        }

        int[] slots = {10, 12, 14, 16};
        AnimationType[] types = AnimationType.values();

        for (int i = 0; i < types.length && i < slots.length; i++) {
            if (slot != slots[i]) continue;

            AnimationType chosen = types[i];
            if (caseManager.getPlayerAnimation(p.getUniqueId()) == chosen) return;

            caseManager.setPlayerAnimation(p.getUniqueId(), chosen);

            Inventory inv = p.getOpenInventory().getTopInventory();
            for (int j = 0; j < types.length && j < slots.length; j++) {
                inv.setItem(slots[j], buildAnimationItem(types[j], types[j] == chosen));
            }

            playAnimationSelectSound(p, chosen);
            p.sendMessage(caseManager.getPlugin().getMessages().get("animation-changed", "animation", chosen.displayName()));
            return;
        }
    }

    private void playAnimationSelectSound(Player p, AnimationType type) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.35f, 1.35f);

        switch (type) {
            case ANVIL -> p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.28f, 1.45f);
            case DYNAMITE -> p.playSound(p.getLocation(), Sound.ENTITY_TNT_PRIMED, 0.25f, 1.75f);
            case PORTAL -> p.playSound(p.getLocation(), Sound.BLOCK_PORTAL_AMBIENT, 0.18f, 1.65f);
            case POISON -> p.playSound(p.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 0.25f, 1.6f);
        }
    }

    private ItemStack buildAnimationItem(AnimationType type, boolean selected) {
        ItemStack item = new ItemStack(type.icon());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName((selected ? "§x§A§0§E§F§A§1◆ " : "§x§8§A§8§A§8§A◇ ") + type.displayName());

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§x§A§0§E§F§A§1 «Анимация»");
        lore.add(" §7- §fНазвание: " + type.displayName());
        lore.add("");
        lore.add("§x§C§0§9§6§A§B «Описание»");
        for (String line : type.description().split("\n")) {
            lore.add(" §7- " + color(line));
        }
        lore.add("");
        lore.add("§x§A§0§E§F§A§1 «Статус»");
        lore.add(selected
                ? " §7- §x§5§5§C§8§7§4Выбрана сейчас"
                : " §7- §fНажмите, чтобы выбрать");
        lore.add("");
        meta.setLore(lore);

        if (selected) {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        }

        item.setItemMeta(meta);
        return item;
    }

    private void scheduleRefresh(Player p, String caseName, CaseDefinition def, UUID uuid, long delay) {
        new BukkitRunnable() {
            @Override
            public void run() {
                clickLock.remove(uuid);
                if (!p.isOnline()) return;
                Inventory top = p.getOpenInventory().getTopInventory();
                if (!(top.getHolder() instanceof CaseGuiHolder h2)) return;
                if (h2.type() != CaseGuiHolder.Type.CASE) return;
                if (!Objects.equals(h2.caseName(), caseName)) return;
                caseManager.fillCaseGui(top, p, def);
            }
        }.runTaskLater(caseManager.getPlugin(), delay);
    }

    private static ItemStack pane(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            it.setItemMeta(meta);
        }
        return it;
    }

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }
}
