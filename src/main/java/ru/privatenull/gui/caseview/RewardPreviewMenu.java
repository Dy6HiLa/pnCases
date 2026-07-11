package ru.privatenull.gui.caseview;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.privatenull.cases.CaseManager;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.cases.model.Reward;
import ru.privatenull.cases.model.CaseGuiLayoutRules;
import ru.privatenull.util.ColorUtil;
import ru.privatenull.util.EnchantmentCompat;
import ru.privatenull.util.SoundCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class RewardPreviewMenu {

    private static final int[] DEFAULT_REWARD_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final int DEFAULT_PREVIOUS_SLOT = 45;
    private static final int DEFAULT_BACK_SLOT = 49;
    private static final int DEFAULT_NEXT_SLOT = 53;

    private final CaseManager caseManager;

    public RewardPreviewMenu(CaseManager caseManager) {
        this.caseManager = caseManager;
    }

    public void open(Player player, String caseName, int page) {
        open(player, caseName, page, false);
    }

    public void handleClick(Player player, CaseGuiHolder holder, int slot) {
        PreviewLayout layout = resolveLayout();
        if (slot == layout.backSlot()) {
            CaseDefinition definition = caseManager.getCaseByName(holder.caseName());
            if (definition != null) {
                caseManager.openCaseGui(player, definition);
            }
            playSound(player, "close", "BLOCK_BARREL_CLOSE", 0.2f, 0.95f);
            return;
        }

        if (slot == layout.previousSlot()) {
            open(player, holder.caseName(), holder.page() - 1, true);
            return;
        }

        if (slot == layout.nextSlot()) {
            open(player, holder.caseName(), holder.page() + 1, true);
        }
    }

    private void open(Player player, String caseName, int page, boolean pageChange) {
        CaseDefinition definition = caseManager.getCaseByName(caseName);
        if (definition == null) return;

        PreviewLayout layout = resolveLayout();
        List<Reward> rewards = definition.rewards();
        int pageSize = layout.rewardSlots().size();
        int pages = Math.max(1, (int) Math.ceil(rewards.size() / (double) pageSize));
        int safePage = Math.max(0, Math.min(page, pages - 1));
        String[] replacements = replacements(definition, safePage, pages,
                "rewards", String.valueOf(rewards.size()));

        Inventory inventory = org.bukkit.Bukkit.createInventory(
                CaseGuiHolder.previewGui(caseName, safePage),
                layout.size(),
                caseManager.getPlugin().getGuiConfig().text(
                        "preview.title", "&7Содержимое кейса", replacements)
        );

        fillDecor(inventory, replacements);

        int totalChance = rewards.stream().mapToInt(Reward::chance).sum();
        int start = safePage * pageSize;
        for (int index = 0; index < pageSize; index++) {
            int rewardIndex = start + index;
            if (rewardIndex >= rewards.size()) break;
            inventory.setItem(layout.rewardSlots().get(index),
                    caseManager.getRewardPresentation().buildPreviewItem(
                            definition, rewards.get(rewardIndex), totalChance));
        }

        inventory.setItem(layout.infoSlot(), buildInfoItem(definition, safePage, pages, rewards.size()));
        inventory.setItem(layout.backSlot(), buildButton("preview.buttons.back", Material.ARROW,
                "&eНазад", List.of("", "&6 «Действие»", "&f- ЛКМ - вернуться к кейсу", ""),
                definition, safePage, pages));

        if (safePage > 0) {
            inventory.setItem(layout.previousSlot(), buildButton("preview.buttons.previous", Material.SPECTRAL_ARROW,
                    "&eПредыдущая страница", List.of("", "&f- Страница: &e{prev_page}&7/&e{pages}", ""),
                    definition, safePage, pages));
        }
        if (safePage + 1 < pages) {
            inventory.setItem(layout.nextSlot(), buildButton("preview.buttons.next", Material.SPECTRAL_ARROW,
                    "&eСледующая страница", List.of("", "&f- Страница: &e{next_page}&7/&e{pages}", ""),
                    definition, safePage, pages));
        }

        player.openInventory(inventory);
        playSound(player, pageChange ? "page" : "open",
                pageChange ? "UI_BUTTON_CLICK" : "BLOCK_BARREL_OPEN",
                pageChange ? 0.18f : 0.22f,
                pageChange ? 1.2f : 1.25f);
    }

    private void fillDecor(Inventory inventory, String[] replacements) {
        ItemStack filler = buildConfiguredItem("preview.decor", Material.GRAY_STAINED_GLASS_PANE,
                " ", Collections.emptyList(), replacements);
        if (caseManager.getPlugin().getGuiConfig().bool("preview.decor.fill-empty", true)) {
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                inventory.setItem(slot, filler);
            }
            return;
        }

        List<Integer> configuredSlots = caseManager.getPlugin().getGuiConfig().integerList(
                "preview.decor.slots", List.of());
        for (int slot : CaseGuiLayoutRules.filterSlots(configuredSlots, inventory.getSize())) {
            inventory.setItem(slot, filler);
        }
    }

    private ItemStack buildInfoItem(CaseDefinition definition, int page, int pages, int rewardCount) {
        String[] replacements = replacements(definition, page, pages,
                "rewards", String.valueOf(rewardCount));
        return buildConfiguredItem("preview.info", Material.COMPASS, "&eСодержимое кейса", List.of(
                "",
                "&6 «Основное»",
                "&f- Кейс: &e{case}",
                "&f- Наград: &e{rewards}",
                "&f- Страница: &e{page}&7/&e{pages}",
                ""
        ), replacements);
    }

    private ItemStack buildButton(String path, Material material, String name, List<String> lore,
                                  CaseDefinition definition, int page, int pages) {
        return buildConfiguredItem(path, material, name, lore, replacements(definition, page, pages));
    }

    private ItemStack buildConfiguredItem(String path, Material fallbackMaterial, String fallbackName,
                                          List<String> fallbackLore, String... replacements) {
        Material material = configuredMaterial(path + ".material", fallbackMaterial);
        int amount = Math.max(1, Math.min(material.getMaxStackSize(),
                caseManager.getPlugin().getGuiConfig().integer(path + ".amount", 1)));
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(caseManager.getPlugin().getGuiConfig().text(
                path + ".name", fallbackName, replacements));
        meta.setLore(caseManager.getPlugin().getGuiConfig().list(
                path + ".lore", fallbackLore, replacements));

        int customModelData = caseManager.getPlugin().getGuiConfig().integer(path + ".custom-model-data", 0);
        if (customModelData > 0) {
            meta.setCustomModelData(customModelData);
        }
        if (caseManager.getPlugin().getGuiConfig().bool(path + ".hide-attributes", true)) {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        }
        if (caseManager.getPlugin().getGuiConfig().bool(path + ".glow", false)) {
            var enchantment = EnchantmentCompat.unbreaking();
            if (enchantment != null) {
                meta.addEnchant(enchantment, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
        }
        item.setItemMeta(meta);
        return item;
    }

    private PreviewLayout resolveLayout() {
        int size = CaseGuiLayoutRules.normalizeSize(
                caseManager.getPlugin().getGuiConfig().integer("preview.layout.size", 54));
        Set<Integer> reserved = new LinkedHashSet<>();
        int infoSlot = configuredSlot("preview.layout.info-slot", 4, size, reserved);
        int backSlot = configuredSlot("preview.layout.back-slot",
                DEFAULT_BACK_SLOT < size ? DEFAULT_BACK_SLOT : size - 5, size, reserved);
        int previousSlot = configuredSlot("preview.layout.previous-slot",
                DEFAULT_PREVIOUS_SLOT < size ? DEFAULT_PREVIOUS_SLOT : size - 9, size, reserved);
        int nextSlot = configuredSlot("preview.layout.next-slot",
                DEFAULT_NEXT_SLOT < size ? DEFAULT_NEXT_SLOT : size - 1, size, reserved);

        List<Integer> defaults = java.util.Arrays.stream(DEFAULT_REWARD_SLOTS).boxed().toList();
        List<Integer> rewardSlots = new ArrayList<>(CaseGuiLayoutRules.filterSlots(
                caseManager.getPlugin().getGuiConfig().integerList("preview.layout.reward-slots", defaults), size));
        rewardSlots.removeIf(reserved::contains);
        if (rewardSlots.isEmpty()) {
            for (int slot = 0; slot < size; slot++) {
                if (!reserved.contains(slot)) rewardSlots.add(slot);
            }
        }
        return new PreviewLayout(size, List.copyOf(rewardSlots), infoSlot, previousSlot, backSlot, nextSlot);
    }

    private int configuredSlot(String path, int fallback, int size, Set<Integer> reserved) {
        int safeFallback = CaseGuiLayoutRules.clampSlot(fallback, size, size - 1);
        int requested = caseManager.getPlugin().getGuiConfig().integer(path, safeFallback);
        return CaseGuiLayoutRules.reserveSlot(requested, safeFallback, size, reserved);
    }

    private Material configuredMaterial(String path, Material fallback) {
        String raw = caseManager.getPlugin().getGuiConfig().raw(path, fallback.name());
        if (raw == null || raw.isBlank()) return fallback;
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("MINECRAFT:")) {
            normalized = normalized.substring("MINECRAFT:".length());
        }
        Material material = Material.matchMaterial(normalized);
        return material == null || material.isAir() ? fallback : material;
    }

    private void playSound(Player player, String key, String fallbackName, float fallbackVolume, float fallbackPitch) {
        String path = "preview.sounds." + key;
        String sound = caseManager.getPlugin().getGuiConfig().raw(path + ".name", fallbackName);
        float volume = (float) Math.max(0.0, Math.min(2.0,
                caseManager.getPlugin().getGuiConfig().decimal(path + ".volume", fallbackVolume)));
        float pitch = (float) Math.max(0.5, Math.min(2.0,
                caseManager.getPlugin().getGuiConfig().decimal(path + ".pitch", fallbackPitch)));
        SoundCompat.play(player, new String[]{sound, fallbackName}, volume, pitch);
    }

    private String[] replacements(CaseDefinition definition, int page, int pages, String... extra) {
        List<String> replacements = new ArrayList<>();
        addReplacement(replacements, "case", ColorUtil.colorize(definition.displayName()));
        addReplacement(replacements, "case_id", definition.name());
        addReplacement(replacements, "case-id", definition.name());
        addReplacement(replacements, "page", String.valueOf(page + 1));
        addReplacement(replacements, "pages", String.valueOf(pages));
        addReplacement(replacements, "prev_page", String.valueOf(Math.max(1, page)));
        addReplacement(replacements, "prev-page", String.valueOf(Math.max(1, page)));
        addReplacement(replacements, "next_page", String.valueOf(Math.min(pages, page + 2)));
        addReplacement(replacements, "next-page", String.valueOf(Math.min(pages, page + 2)));
        for (int index = 0; index + 1 < extra.length; index += 2) {
            addReplacement(replacements, extra[index], extra[index + 1]);
        }
        return replacements.toArray(String[]::new);
    }

    private static void addReplacement(List<String> replacements, String key, String value) {
        replacements.add(key);
        replacements.add(value);
    }

    private record PreviewLayout(
            int size,
            List<Integer> rewardSlots,
            int infoSlot,
            int previousSlot,
            int backSlot,
            int nextSlot
    ) {
    }
}
