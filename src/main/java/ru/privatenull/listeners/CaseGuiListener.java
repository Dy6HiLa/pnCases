package ru.privatenull.listeners;

import org.bukkit.ChatColor;
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
import ru.privatenull.cases.animation.AnimationType;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.cases.model.Reward;
import ru.privatenull.util.EnchantmentCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CaseGuiListener implements Listener {

    private static final int[] ANIMATION_SLOTS = {10, 11, 12, 14, 15, 16};
    private static final int[] PREVIEW_REWARD_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final int PREVIEW_PREV_SLOT = 45;
    private static final int PREVIEW_BACK_SLOT = 49;
    private static final int PREVIEW_NEXT_SLOT = 53;

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
        if (holder.type() == CaseGuiHolder.Type.PREVIEW) {
            handlePreviewClick(p, holder, slot);
            return;
        }

        if (holder.type() != CaseGuiHolder.Type.CASE) {
            return;
        }

        CaseDefinition def = caseManager.getCaseByName(holder.caseName());
        if (def == null) return;

        if (slot == def.guiLayout().animationSlot() && def.fixedAnimation() == null) {
            openAnimationSelectGui(p, holder.caseName());
            return;
        }

        if (slot != def.guiLayout().openSlot()) return;

        if (e.getClick() == ClickType.MIDDLE) {
            openRewardPreviewGui(p, holder.caseName(), 0);
            return;
        }

        if (e.getClick().isLeftClick() && Math.max(0, def.buyKeyWithXpLevels()) <= 0) {
            openRewardPreviewGui(p, holder.caseName(), 0);
            return;
        }

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
        if (keyId == null || keyId.isEmpty() || !caseManager.keyExists(keyId)) {
            String title = caseManager.getPlugin().getMessages().getOr("gui.open.key-not-configured", "key-not-configured");
            e.getInventory().setItem(def.guiLayout().openSlot(), pane(Material.BARRIER, title, List.of(
                    "&7У этого кейса не выбран рабочий ключ.",
                    "&7Проверь настройку &fcost.key&7."
            )));
            p.sendMessage(caseManager.getPlugin().getMessages().get("key-not-configured"));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.24f, 1.0f);
            scheduleRefresh(p, holder.caseName(), def, uuid, 30L);
            return;
        }

        int have = (keyId == null) ? 0 : caseManager.getPlugin().getKeyStorage().get(uuid, keyId);

        if (have >= need) {
            clickLock.remove(uuid);
            caseManager.tryOpenCase(p, def);
            return;
        }

        int buyLevels = Math.max(0, def.buyKeyWithXpLevels());
        boolean wantsBuy = e.getClick().isLeftClick();
        boolean canBuy = wantsBuy && buyLevels > 0 && p.getLevel() >= buyLevels;

        if (canBuy) {
            int give = need - have;
            p.setLevel(p.getLevel() - buyLevels);
            caseManager.getPlugin().getKeyStorage().add(uuid, keyId, give);
            e.getInventory().setItem(def.guiLayout().openSlot(), pane(Material.LIME_STAINED_GLASS_PANE,
                    caseManager.getPlugin().getMessages().getOr("gui.buy.success", "gui-buy-success"), Collections.emptyList()));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.28f, 1.2f);
            scheduleRefresh(p, holder.caseName(), def, uuid, 30L);
            return;
        }

        String title;
        List<String> lore = new ArrayList<>();
        if (!wantsBuy || buyLevels <= 0) {
            title = caseManager.getPlugin().getMessages().getOr("gui.open.no-keys", "not-enough-keys",
                    "have", String.valueOf(have), "need", String.valueOf(need));
            String keysBalance = caseManager.getPlugin().getMessages().getOr("gui.case-button.keys-balance", "gui-keys-balance",
                    "have", String.valueOf(have), "need", String.valueOf(need));
            lore.addAll(caseManager.getPlugin().getMessages().getList("gui.open.no-keys-lore",
                    "have", String.valueOf(have),
                    "need", String.valueOf(need),
                    "keys-balance", keysBalance));
            p.sendMessage(caseManager.getPlugin().getMessages().get("not-enough-keys",
                    "have", String.valueOf(have), "need", String.valueOf(need)));
        } else {
            title = caseManager.getPlugin().getMessages().getOr("gui.buy.no-levels", "gui-buy-no-levels");
        }

        e.getInventory().setItem(def.guiLayout().openSlot(), pane(Material.BARRIER, title, lore));
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.24f, 1.0f);
        scheduleRefresh(p, holder.caseName(), def, uuid, 30L);
    }

    private void openRewardPreviewGui(Player p, String caseName, int page) {
        CaseDefinition def = caseManager.getCaseByName(caseName);
        if (def == null) return;

        List<Reward> rewards = def.rewards();
        int pageSize = PREVIEW_REWARD_SLOTS.length;
        int pages = Math.max(1, (int) Math.ceil(rewards.size() / (double) pageSize));
        int safePage = Math.max(0, Math.min(page, pages - 1));

        Inventory inv = org.bukkit.Bukkit.createInventory(
                CaseGuiHolder.previewGui(caseName, safePage),
                54,
                color("&8Содержимое кейса")
        );

        ItemStack filler = pane(Material.GRAY_STAINED_GLASS_PANE, " ", Collections.emptyList());
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }

        int totalChance = rewards.stream().mapToInt(Reward::chance).sum();
        int start = safePage * pageSize;
        for (int i = 0; i < pageSize; i++) {
            int rewardIndex = start + i;
            if (rewardIndex >= rewards.size()) break;
            inv.setItem(PREVIEW_REWARD_SLOTS[i], caseManager.buildRewardPreviewItem(def, rewards.get(rewardIndex), totalChance));
        }

        inv.setItem(4, buildPreviewInfoItem(def, safePage, pages, rewards.size()));
        inv.setItem(PREVIEW_BACK_SLOT, buildPreviewButton(Material.ARROW,
                "§x§A§0§E§F§A§1← §fНазад",
                List.of("", " §7- §fВернуться к кейсу", "")));

        if (safePage > 0) {
            inv.setItem(PREVIEW_PREV_SLOT, buildPreviewButton(Material.SPECTRAL_ARROW,
                    "§x§A§0§E§F§A§1← §fПредыдущая",
                    List.of("", " §7- §fСтраница " + safePage + " из " + pages, "")));
        }

        if (safePage + 1 < pages) {
            inv.setItem(PREVIEW_NEXT_SLOT, buildPreviewButton(Material.SPECTRAL_ARROW,
                    "§x§A§0§E§F§A§1→ §fСледующая",
                    List.of("", " §7- §fСтраница " + (safePage + 2) + " из " + pages, "")));
        }

        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_BARREL_OPEN, 0.22f, 1.25f);
    }

    private void handlePreviewClick(Player p, CaseGuiHolder holder, int slot) {
        if (slot == PREVIEW_BACK_SLOT) {
            CaseDefinition def = caseManager.getCaseByName(holder.caseName());
            if (def != null) {
                caseManager.openCaseGui(p, def);
            }
            p.playSound(p.getLocation(), Sound.BLOCK_BARREL_CLOSE, 0.2f, 0.95f);
            return;
        }

        if (slot == PREVIEW_PREV_SLOT) {
            openRewardPreviewGui(p, holder.caseName(), holder.page() - 1);
            return;
        }

        if (slot == PREVIEW_NEXT_SLOT) {
            openRewardPreviewGui(p, holder.caseName(), holder.page() + 1);
        }
    }

    private ItemStack buildPreviewInfoItem(CaseDefinition def, int page, int pages, int rewardCount) {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(color("&x&4&2&9&F&9&1▸ &fСодержимое кейса"));
        meta.setLore(List.of(
                "",
                color("&x&A&0&E&F&A&1 «Информация»"),
                color(" &7- &fКейс: &x&4&2&9&F&9&1" + def.name()),
                color(" &7- &fНаград: &x&4&2&9&F&9&1" + rewardCount),
                color(" &7- &fСтраница: &x&4&2&9&F&9&1" + (page + 1) + "&7/&x&4&2&9&F&9&1" + pages),
                ""
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildPreviewButton(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(color(name));
        meta.setLore(lore.stream().map(CaseGuiListener::color).toList());
        item.setItemMeta(meta);
        return item;
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
        AnimationType selected = caseManager.getPlayerAnimation(p.getUniqueId());

        for (int i = 0; i < types.length && i < ANIMATION_SLOTS.length; i++) {
            inv.setItem(ANIMATION_SLOTS[i], buildAnimationItem(types[i], types[i] == selected));
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
        p.playSound(p.getLocation(), Sound.BLOCK_BARREL_OPEN, 0.28f, 1.15f);
    }

    private void handleAnimationSelectClick(Player p, AnimationSelectHolder holder, int slot) {
        if (slot == 22) {
            CaseDefinition def = caseManager.getCaseByName(holder.caseName());
            if (def != null) {
                caseManager.openCaseGui(p, def);
            }
            p.playSound(p.getLocation(), Sound.BLOCK_BARREL_CLOSE, 0.24f, 0.95f);
            return;
        }

        AnimationType[] types = AnimationType.values();

        for (int i = 0; i < types.length && i < ANIMATION_SLOTS.length; i++) {
            if (slot != ANIMATION_SLOTS[i]) continue;

            AnimationType chosen = types[i];
            if (caseManager.getPlayerAnimation(p.getUniqueId()) == chosen) return;

            caseManager.setPlayerAnimation(p.getUniqueId(), chosen);

            Inventory inv = p.getOpenInventory().getTopInventory();
            for (int j = 0; j < types.length && j < ANIMATION_SLOTS.length; j++) {
                inv.setItem(ANIMATION_SLOTS[j], buildAnimationItem(types[j], types[j] == chosen));
            }

            playAnimationSelectSound(p, chosen);
            p.sendMessage(caseManager.getPlugin().getMessages().get("animation-changed", "animation", chosen.displayName()));
            return;
        }
    }

    private void playAnimationSelectSound(Player p, AnimationType type) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.22f, 1.35f);

        switch (type) {
            case ANVIL -> p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.16f, 1.45f);
            case DYNAMITE -> p.playSound(p.getLocation(), Sound.ENTITY_TNT_PRIMED, 0.15f, 1.75f);
            case PORTAL -> p.playSound(p.getLocation(), Sound.BLOCK_PORTAL_AMBIENT, 0.12f, 1.65f);
            case POISON -> p.playSound(p.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 0.15f, 1.6f);
            case CAULDRON -> {
                p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.13f, 1.65f);
                p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.15f, 1.85f);
            }
            case FORTUNE_RING -> {
                p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.14f, 1.55f);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.10f, 1.85f);
            }
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
            var unbreaking = EnchantmentCompat.unbreaking();
            if (unbreaking != null) {
                meta.addEnchant(unbreaking, 1, true);
            }
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
            meta.setDisplayName(color(name));
            meta.setLore(lore.stream().map(CaseGuiListener::color).toList());
            it.setItemMeta(meta);
        }
        return it;
    }

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }
}
