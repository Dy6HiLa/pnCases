package ru.privatenull.gui.caseview;

import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.privatenull.PnCasesPlugin;
import ru.privatenull.cases.CaseManager;
import ru.privatenull.cases.model.AnimationType;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.cases.model.CaseGuiLayout;
import ru.privatenull.cases.reward.RewardPresentationService;
import ru.privatenull.cases.view.CaseView;
import ru.privatenull.storage.OpenHistoryStorage;
import ru.privatenull.util.ColorUtil;
import ru.privatenull.util.ServerCompatibility;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CaseMenuService implements CaseView {

    private static final DateTimeFormatter HISTORY_TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM HH:mm");

    private final PnCasesPlugin plugin;
    private final CaseManager caseManager;
    private final RewardPresentationService rewardPresentation;

    public CaseMenuService(
            PnCasesPlugin plugin,
            CaseManager caseManager
    ) {
        this.plugin = plugin;
        this.caseManager = caseManager;
        this.rewardPresentation = caseManager.getRewardPresentation();
    }

    @Override
    public ItemStack buildOpenButton(Player player, CaseDefinition definition) {
        ItemStack item = definition.openButton().clone();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        int buyExp = Math.max(0, definition.buyKeyWithXpLevels());
        List<String> lore = meta.hasLore()
                ? new ArrayList<>(Objects.requireNonNull(meta.getLore()))
                : new ArrayList<>();
        int need = Math.max(1, definition.costAmount());
        String keyId = definition.costKeyId();
        int have = definition.costType() == CaseDefinition.CostType.KEY && keyId != null
                ? plugin.getKeyStorage().get(player.getUniqueId(), keyId)
                : 0;

        lore.addAll(extraLore(definition, have, need, buyExp));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public ItemStack buildAnimationButton(Player player, CaseDefinition definition) {
        AnimationType current = caseManager.getPlayerAnimation(player.getUniqueId());
        ItemStack item = definition != null && definition.guiLayout().animationItem() != null
                ? definition.guiLayout().animationItem().clone()
                : new ItemStack(AnimationType.FORTUNE_RING.icon());
        item.setAmount(1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String[] replacements = {
                "animation", current.displayName(),
                "case", definition == null ? "" : color(definition.displayName()),
                "case_id", definition == null ? "" : definition.name(),
                "case-id", definition == null ? "" : definition.name()
        };
        meta.setDisplayName(plugin.getGuiConfig().text("case.animation-button.name",
                "&fАнимация: {animation}", replacements));
        meta.setLore(plugin.getGuiConfig().list("case.animation-button.lore", List.of(
                "",
                "&7Текущая: {animation}",
                "&7Нажмите, чтобы выбрать другую",
                ""
        ), replacements));
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public ItemStack buildPreviewButton(CaseDefinition definition) {
        if (definition.guiLayout().previewItem() != null) return definition.guiLayout().previewItem().clone();

        ItemStack item = new ItemStack(Material.ENDER_EYE);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        String[] replacements = {
                "case", color(definition.displayName()),
                "case_id", definition.name(),
                "case-id", definition.name(),
                "rewards", String.valueOf(definition.rewards().size())
        };
        meta.setDisplayName(plugin.getGuiConfig().text("case.preview-button.name",
                "&#429F91▸ &fСодержимое кейса", replacements));
        meta.setLore(plugin.getGuiConfig().list("case.preview-button.lore", List.of(
                "",
                "&#A0EFA1 «Предпросмотр»",
                " &7- &fКейс: &#429F91{case}",
                " &7- &fНаград: &#429F91{rewards}",
                " &7- &fПоказаны шансы и редкость",
                "",
                "&#429F91▸ &fНажмите, чтобы открыть"
        ), replacements));
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public void fill(Inventory inventory, Player player, CaseDefinition definition) {
        inventory.clear();
        CaseGuiLayout layout = definition.guiLayout();
        fillDecor(inventory, layout);
        inventory.setItem(layout.openSlot(), buildOpenButton(player, definition));
        fillHistory(inventory, definition);
        if (definition.fixedAnimation() == null && !ServerCompatibility.useMinecraft1165AnimationMode()) {
            inventory.setItem(layout.animationSlot(), buildAnimationButton(player, definition));
        }
    }

    @Override
    public void open(Player player, CaseDefinition definition) {
        Inventory inventory = Bukkit.createInventory(
                CaseGuiHolder.caseGui(definition.name()),
                definition.guiLayout().size(),
                color(definition.guiTitle())
        );
        fill(inventory, player, definition);
        player.openInventory(inventory);
    }

    private List<String> extraLore(CaseDefinition definition, int have, int need, int buyExp) {
        String keyId = definition.costKeyId() == null ? "" : definition.costKeyId();
        String keyName = keyId.isBlank() ? "" : caseManager.getKeyDisplayName(keyId);
        String keysBalance = definition.costType() == CaseDefinition.CostType.KEY
                ? plugin.getGuiConfig().text("case.button.keys-balance",
                plugin.getMessages().getOr("gui.case-button.keys-balance", "gui-keys-balance",
                        "have", String.valueOf(have), "need", String.valueOf(need),
                        "key", keyId, "key_name", keyName, "key-name", keyName),
                "have", String.valueOf(have), "need", String.valueOf(need),
                "key", keyId, "key_name", keyName, "key-name", keyName)
                : "";
        String buyHint = plugin.getGuiConfig().text("case.button.buy-xp-hint",
                plugin.getMessages().getOr("gui.case-button.buy-xp-hint", "gui-buy-xp-hint",
                        "levels", String.valueOf(buyExp)),
                "levels", String.valueOf(buyExp));
        String previewLeftHint = plugin.getGuiConfig().text("case.button.preview-left-hint",
                plugin.getMessages().getOr("gui.case-button.preview-left-hint", "gui.case-button.buy-xp-disabled"));
        String openHint = plugin.getGuiConfig().text("case.button.open-hint",
                plugin.getMessages().getOr("gui.case-button.open-hint", "gui-open-hint"));
        String previewHint = buyExp > 0
                ? plugin.getGuiConfig().text("case.button.preview-hint",
                plugin.getMessages().getOr("gui.case-button.preview-hint", "gui.case-button.preview-hint"))
                : "";
        if (previewHint.startsWith("§c[missing:")) previewHint = color("&7СКМ &8— &bпосмотреть содержимое");

        String[] replacements = {
                "case", color(definition.displayName()), "case_id", definition.name(), "case-id", definition.name(),
                "title", definition.guiTitle(), "material", definition.openButton().getType().getKey().toString(),
                "key", keyId, "key_name", keyName, "key-name", keyName,
                "have", String.valueOf(have), "need", String.valueOf(need), "levels", String.valueOf(buyExp),
                "keys-balance", keysBalance, "buy-xp-hint", buyHint, "preview-left-hint", previewLeftHint,
                "left-click", buyExp > 0 ? buyHint : previewLeftHint, "open-hint", openHint,
                "right-click", openHint, "preview-hint", previewHint, "middle-click", previewHint
        };
        List<String> fallback = plugin.getMessages().getList("gui.case-button.extra-lore", replacements);
        List<String> lines = plugin.getGuiConfig().list("case.button.extra-lore", fallback, replacements);
        trimTrailingEmptyLines(lines);
        return lines;
    }

    private void fillDecor(Inventory inventory, CaseGuiLayout layout) {
        ItemStack pane = layout.decorItem() == null
                ? new ItemStack(Material.GRAY_STAINED_GLASS_PANE)
                : layout.decorItem().clone();
        for (int slot : layout.decorSlots()) {
            if (slot >= 0 && slot < inventory.getSize()) inventory.setItem(slot, pane.clone());
        }
    }

    private void fillHistory(Inventory inventory, CaseDefinition definition) {
        List<OpenHistoryStorage.Entry> history = caseManager.getOpenHistory(definition.name());
        List<Integer> slots = definition.guiLayout().historySlots();
        for (int index = 0; index < slots.size(); index++) {
            int slot = slots.get(index);
            if (slot < 0 || slot >= inventory.getSize()) continue;
            inventory.setItem(slot, index < history.size()
                    ? historyItem(definition, history.get(index))
                    : emptyHistoryItem(definition));
        }
    }

    private ItemStack historyItem(CaseDefinition definition, OpenHistoryStorage.Entry entry) {
        ItemStack item = historyRewardItem(definition, entry.rewardName());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        String[] replacements = {
                "case", color(definition.displayName()), "case_id", definition.name(), "case-id", definition.name(),
                "player", entry.playerName(), "reward", color(entry.rewardName()), "time", formatTime(entry.openedAt())
        };
        meta.setDisplayName(plugin.getGuiConfig().text("case.history.item.name", "&#A0EFA1◆ {reward}", replacements));
        meta.setLore(plugin.getGuiConfig().list("case.history.item.lore", List.of(
                "", "&#A0EFA1 «Детали открытия»", " &7- &fИгрок: &#FBCA08{player}",
                " &7- &fНаграда: {reward}", "", "&#C096AB «Время»", " &7- &f{time}", ""
        ), replacements));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack historyRewardItem(CaseDefinition definition, String rewardName) {
        ItemStack item = rewardPresentation.findDisplayItem(definition, rewardName);
        if (item != null) return item;

        ItemStack fallback = new ItemStack(Material.CLOCK);
        ItemMeta meta = fallback.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(rewardName == null || rewardName.isBlank() ? "&fНаграда" : rewardName));
            fallback.setItemMeta(meta);
        }
        return fallback;
    }

    private ItemStack emptyHistoryItem(CaseDefinition definition) {
        ItemStack configured = definition.guiLayout().emptyHistoryItem();
        ItemStack item = configured == null ? new ItemStack(Material.BARRIER) : configured.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.hasDisplayName() || meta.hasLore()) return item;

        String[] replacements = {
                "case", color(definition.displayName()), "case_id", definition.name(), "case-id", definition.name()
        };
        meta.setDisplayName(plugin.getGuiConfig().text("case.history.empty.name", "&8История пуста", replacements));
        meta.setLore(plugin.getGuiConfig().list("case.history.empty.lore", List.of(
                "", "&#A0EFA1 «История кейса»", " &7- &fПоследние открытия",
                " &7- &fбудут отображаться здесь", ""
        ), replacements));
        item.setItemMeta(meta);
        return item;
    }

    private String formatTime(long epochSeconds) {
        if (epochSeconds <= 0L) return "неизвестно";
        long diff = System.currentTimeMillis() / 1000L - epochSeconds;
        if (diff < 60) return "только что";
        if (diff < 3600) {
            int minutes = (int) (diff / 60);
            return minutes + " " + wordForm(minutes, "минуту", "минуты", "минут") + " назад";
        }
        if (diff < 86400) {
            int hours = (int) (diff / 3600);
            return hours + " " + wordForm(hours, "час", "часа", "часов") + " назад";
        }
        int days = (int) (diff / 86400);
        if (days < 7) return days + " " + wordForm(days, "день", "дня", "дней") + " назад";
        LocalDateTime time = LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.systemDefault());
        return HISTORY_TIME_FORMAT.format(time);
    }

    private String wordForm(int number, String one, String few, String many) {
        int normalized = Math.abs(number) % 100;
        int last = normalized % 10;
        if (normalized > 10 && normalized < 20) return many;
        if (last > 1 && last < 5) return few;
        return last == 1 ? one : many;
    }

    private void trimTrailingEmptyLines(List<String> lines) {
        while (!lines.isEmpty()) {
            String last = ChatColor.stripColor(lines.get(lines.size() - 1));
            if (last != null && !last.isBlank()) return;
            lines.remove(lines.size() - 1);
        }
    }

    private String color(String value) {
        return ColorUtil.colorize(value);
    }
}
