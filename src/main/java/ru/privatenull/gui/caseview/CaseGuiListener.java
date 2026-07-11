package ru.privatenull.gui.caseview;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import ru.privatenull.cases.CaseManager;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.util.ColorUtil;
import ru.privatenull.util.InventoryViewCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CaseGuiListener implements Listener {

    private final CaseManager caseManager;
    private final RewardPreviewMenu rewardPreviewMenu;
    private final AnimationSelectionMenu animationMenu;
    private final Set<UUID> clickLock = ConcurrentHashMap.newKeySet();

    public CaseGuiListener(CaseManager caseManager) {
        this.caseManager = caseManager;
        this.rewardPreviewMenu = new RewardPreviewMenu(caseManager);
        this.animationMenu = new AnimationSelectionMenu(caseManager);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (event.getInventory().getHolder() instanceof AnimationSelectHolder holder) {
            event.setCancelled(true);
            animationMenu.handleClick(player, holder, event.getRawSlot());
            return;
        }
        if (!(event.getInventory().getHolder() instanceof CaseGuiHolder holder)) return;

        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (holder.type() == CaseGuiHolder.Type.PREVIEW) {
            rewardPreviewMenu.handleClick(player, holder, slot);
            return;
        }
        if (holder.type() != CaseGuiHolder.Type.CASE) return;

        CaseDefinition definition = caseManager.getCaseByName(holder.caseName());
        if (definition == null) return;
        if (slot == definition.guiLayout().animationSlot() && definition.fixedAnimation() == null) {
            animationMenu.open(player, holder.caseName());
            return;
        }
        if (slot != definition.guiLayout().openSlot()) return;

        if (event.getClick() == ClickType.MIDDLE
                || (event.getClick().isLeftClick() && definition.buyKeyWithXpLevels() <= 0)) {
            rewardPreviewMenu.open(player, holder.caseName(), 0);
            return;
        }
        openOrBuy(player, event, holder, definition);
    }

    private void openOrBuy(Player player, InventoryClickEvent event, CaseGuiHolder holder, CaseDefinition definition) {
        UUID playerId = player.getUniqueId();
        if (!clickLock.add(playerId)) return;

        if (definition.costType() != CaseDefinition.CostType.KEY) {
            clickLock.remove(playerId);
            caseManager.tryOpenCase(player, definition);
            return;
        }

        String keyId = definition.costKeyId();
        int need = Math.max(1, definition.costAmount());
        if (keyId == null || keyId.isEmpty() || !caseManager.keyExists(keyId)) {
            showInvalidKey(player, event.getInventory(), definition);
            scheduleRefresh(player, holder.caseName(), definition, playerId, 30L);
            return;
        }

        int have = caseManager.getPlugin().getKeyStorage().get(playerId, keyId);
        if (have >= need) {
            clickLock.remove(playerId);
            caseManager.tryOpenCase(player, definition);
            return;
        }

        int buyLevels = Math.max(0, definition.buyKeyWithXpLevels());
        boolean wantsBuy = event.getClick().isLeftClick();
        if (wantsBuy && buyLevels > 0 && player.getLevel() >= buyLevels) {
            buyKeys(player, event.getInventory(), definition, keyId, need - have, buyLevels);
        } else {
            showInsufficientPayment(player, event.getInventory(), definition, wantsBuy, have, need, buyLevels);
        }
        scheduleRefresh(player, holder.caseName(), definition, playerId, 30L);
    }

    private void showInvalidKey(Player player, Inventory inventory, CaseDefinition definition) {
        String title = caseManager.getPlugin().getMessages().getOr("gui.open.key-not-configured", "key-not-configured");
        inventory.setItem(definition.guiLayout().openSlot(), pane(Material.BARRIER, title, List.of(
                "&7У этого кейса не выбран рабочий ключ.",
                "&7Проверь настройку &fcost.key&7."
        )));
        player.sendMessage(caseManager.getPlugin().getMessages().get("key-not-configured"));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.24f, 1.0f);
    }

    private void buyKeys(
            Player player,
            Inventory inventory,
            CaseDefinition definition,
            String keyId,
            int amount,
            int levels
    ) {
        player.setLevel(player.getLevel() - levels);
        caseManager.getPlugin().getKeyStorage().add(player.getUniqueId(), keyId, amount);
        inventory.setItem(definition.guiLayout().openSlot(), pane(
                Material.LIME_STAINED_GLASS_PANE,
                caseManager.getPlugin().getMessages().getOr("gui.buy.success", "gui-buy-success"),
                Collections.emptyList()
        ));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.28f, 1.2f);
    }

    private void showInsufficientPayment(
            Player player,
            Inventory inventory,
            CaseDefinition definition,
            boolean wantsBuy,
            int have,
            int need,
            int buyLevels
    ) {
        String title;
        List<String> lore = new ArrayList<>();
        if (!wantsBuy || buyLevels <= 0) {
            title = caseManager.getPlugin().getMessages().getOr("gui.open.no-keys", "not-enough-keys",
                    "have", String.valueOf(have), "need", String.valueOf(need));
            String balance = caseManager.getPlugin().getMessages().getOr(
                    "gui.case-button.keys-balance", "gui-keys-balance",
                    "have", String.valueOf(have), "need", String.valueOf(need));
            lore.addAll(caseManager.getPlugin().getMessages().getList("gui.open.no-keys-lore",
                    "have", String.valueOf(have), "need", String.valueOf(need), "keys-balance", balance));
            player.sendMessage(caseManager.getPlugin().getMessages().get("not-enough-keys",
                    "have", String.valueOf(have), "need", String.valueOf(need)));
        } else {
            title = caseManager.getPlugin().getMessages().getOr("gui.buy.no-levels", "gui-buy-no-levels");
        }
        inventory.setItem(definition.guiLayout().openSlot(), pane(Material.BARRIER, title, lore));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.24f, 1.0f);
    }

    private void scheduleRefresh(Player player, String caseName, CaseDefinition definition, UUID playerId, long delay) {
        new BukkitRunnable() {
            @Override
            public void run() {
                clickLock.remove(playerId);
                if (!player.isOnline()) return;
                Inventory top = InventoryViewCompat.topInventory(player);
                if (top == null || !(top.getHolder() instanceof CaseGuiHolder holder)) return;
                if (holder.type() != CaseGuiHolder.Type.CASE || !Objects.equals(holder.caseName(), caseName)) return;
                caseManager.fillCaseGui(top, player, definition);
            }
        }.runTaskLater(caseManager.getPlugin(), delay);
    }

    private static ItemStack pane(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.colorize(name));
            meta.setLore(lore.stream().map(ColorUtil::colorize).toList());
            item.setItemMeta(meta);
        }
        return item;
    }
}
