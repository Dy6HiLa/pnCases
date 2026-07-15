package ru.privatenull.gui.caseview;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.privatenull.cases.CaseManager;
import ru.privatenull.cases.animation.AnimationWarnings;
import ru.privatenull.cases.model.AnimationType;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.pnlibrary.text.ColorUtil;
import ru.privatenull.util.EnchantmentCompat;
import ru.privatenull.util.GuiItemFlags;
import ru.privatenull.util.InventoryViewCompat;
import ru.privatenull.util.ServerCompatibility;
import ru.privatenull.util.SoundCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class AnimationSelectionMenu {

    private static final int BACK_SLOT = 40;
    private static final int[] ANIMATION_SLOTS = {11, 12, 13, 20, 21, 22, 29, 30, 31};

    private final CaseManager caseManager;

    AnimationSelectionMenu(CaseManager caseManager) {
        this.caseManager = caseManager;
    }

    void open(Player player, String caseName) {
        CaseDefinition definition = caseManager.getCaseByName(caseName);
        Inventory inventory = Bukkit.createInventory(
                new AnimationSelectHolder(caseName),
                45,
                ColorUtil.colorize("&#82DCFFВыбор анимации")
        );
        ItemStack filler = pane(Material.GRAY_STAINED_GLASS_PANE, " ", Collections.emptyList());
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);

        AnimationType[] types = availableTypes();
        AnimationType selected = caseManager.getPlayerAnimation(player.getUniqueId());
        for (int index = 0; index < types.length && index < ANIMATION_SLOTS.length; index++) {
            inventory.setItem(ANIMATION_SLOTS[index], animationItem(definition, types[index], types[index] == selected));
        }
        inventory.setItem(BACK_SLOT, backItem(definition));
        caseManager.getPlugin().getGuiOpenAnimations().open(player, inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_BARREL_OPEN, 0.28f, 1.15f);
    }

    void handleClick(Player player, AnimationSelectHolder holder, int slot) {
        if (slot == BACK_SLOT) {
            CaseDefinition definition = caseManager.getCaseByName(holder.caseName());
            if (definition != null) caseManager.openCaseGui(player, definition);
            player.playSound(player.getLocation(), Sound.BLOCK_BARREL_CLOSE, 0.24f, 0.95f);
            return;
        }

        AnimationType[] types = availableTypes();
        for (int index = 0; index < types.length && index < ANIMATION_SLOTS.length; index++) {
            if (slot != ANIMATION_SLOTS[index]) continue;
            select(player, holder, types, types[index]);
            return;
        }
    }

    private void select(Player player, AnimationSelectHolder holder, AnimationType[] types, AnimationType chosen) {
        if (caseManager.getPlayerAnimation(player.getUniqueId()) == chosen) return;
        caseManager.setPlayerAnimation(player.getUniqueId(), chosen);

        Inventory inventory = InventoryViewCompat.topInventory(player);
        CaseDefinition definition = caseManager.getCaseByName(holder.caseName());
        if (inventory != null) {
            for (int index = 0; index < types.length && index < ANIMATION_SLOTS.length; index++) {
                inventory.setItem(ANIMATION_SLOTS[index], animationItem(definition, types[index], types[index] == chosen));
            }
        }
        playSelectSound(player, chosen);
        player.sendMessage(caseManager.getPlugin().getMessages().get(
                "animation-changed",
                "animation", chosen.displayName()
        ));
        AnimationWarnings.warnIfMobBased(caseManager.getPlugin(), player, chosen);
    }

    private ItemStack backItem(CaseDefinition definition) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        String[] replacements = replacements(definition);
        meta.setDisplayName(caseManager.getPlugin().getGuiConfig().text(
                "animation-select.back.name", "&#A0EFA1← &fНазад", replacements));
        meta.setLore(caseManager.getPlugin().getGuiConfig().list("animation-select.back.lore", List.of(
                "", "&#A0EFA1 «Навигация»", " &7- &fВернуться к кейсу", ""
        ), replacements));
        GuiItemFlags.hideAttributes(meta);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack animationItem(CaseDefinition definition, AnimationType type, boolean selected) {
        ItemStack item = new ItemStack(type.icon());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String prefix = selected ? "&#A0EFA1◆ " : "&#8A8A8A◇ ";
        String[] replacements = replacements(
                definition,
                "prefix", prefix,
                "animation", animationTitle(type),
                "description", shortDescription(type),
                "status", selected ? "&#55C874Выбрана сейчас" : "&fНажмите, чтобы выбрать"
        );
        List<String> lore = defaultLore(type, selected);
        String configPath = "animation-select.selection-card-v2";
        meta.setDisplayName(caseManager.getPlugin().getGuiConfig().text(configPath + ".name",
                "{prefix}{animation}", replacements));
        meta.setLore(caseManager.getPlugin().getGuiConfig().list(configPath + ".lore", lore, replacements));
        GuiItemFlags.hideAttributes(meta);
        if (selected) {
            var unbreaking = EnchantmentCompat.unbreaking();
            if (unbreaking != null) meta.addEnchant(unbreaking, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(meta);
        return item;
    }

    private List<String> defaultLore(AnimationType type, boolean selected) {
        return List.of(
                "",
                "&#C096AB «Как выглядит»",
                " &7- &f" + shortDescription(type),
                "",
                "&#A0EFA1 «Статус»",
                selected ? " &7- &#55C874Выбрана сейчас" : " &7- &#FFD75AНажмите, чтобы выбрать",
                ""
        );
    }

    private String shortDescription(AnimationType type) {
        String description = type.description();
        if (description == null || description.isBlank()) return "Анимация открытия кейса.";
        return description.split("\\R", 2)[0];
    }

    private String animationTitle(AnimationType type) {
        return type.displayName();
    }

    private void playSelectSound(Player player, AnimationType type) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.22f, 1.35f);
        switch (type) {
            case ANVIL -> player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.16f, 1.45f);
            case DYNAMITE -> player.playSound(player.getLocation(), Sound.ENTITY_TNT_PRIMED, 0.15f, 1.75f);
            case PORTAL -> player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_AMBIENT, 0.12f, 1.65f);
            case POISON -> player.playSound(player.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 0.15f, 1.6f);
            case CAULDRON -> {
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.13f, 1.65f);
                SoundCompat.play(player, new String[]{"BLOCK_AMETHYST_BLOCK_CHIME", "BLOCK_NOTE_BLOCK_PLING"}, 0.15f, 1.85f);
            }
            case FORTUNE_RING -> {
                SoundCompat.play(player, new String[]{"BLOCK_AMETHYST_BLOCK_CHIME", "BLOCK_NOTE_BLOCK_PLING"}, 0.14f, 1.55f);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.10f, 1.85f);
            }
            case PILLAGER_RAID -> {
                SoundCompat.play(player, new String[]{"ENTITY_PILLAGER_AMBIENT", "ENTITY_VINDICATOR_AMBIENT"}, 0.13f, 0.85f);
                player.playSound(player.getLocation(), Sound.BLOCK_WOOD_PLACE, 0.12f, 0.75f);
            }
            case MOB_HUNT -> {
                player.playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_AMBIENT, 0.16f, 0.85f);
                player.playSound(player.getLocation(), Sound.ENTITY_SKELETON_AMBIENT, 0.14f, 1.10f);
            }
        }
    }

    private String[] replacements(CaseDefinition definition, String... extra) {
        List<String> values = new ArrayList<>();
        Collections.addAll(values,
                "case", definition == null ? "" : color(definition.displayName()),
                "case_id", definition == null ? "" : definition.name(),
                "case-id", definition == null ? "" : definition.name());
        for (int index = 0; index + 1 < extra.length; index += 2) {
            values.add(extra[index]);
            values.add(extra[index + 1]);
        }
        return values.toArray(String[]::new);
    }

    private AnimationType[] availableTypes() {
        if (ServerCompatibility.useMinecraft1165AnimationMode()) {
            return new AnimationType[]{AnimationType.FORTUNE_RING};
        }
        if (!ServerCompatibility.useModernAnimations()) {
            return Arrays.stream(AnimationType.values())
                    .filter(type -> type != AnimationType.PILLAGER_RAID)
                    .toArray(AnimationType[]::new);
        }
        return AnimationType.values();
    }

    private ItemStack pane(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            meta.setLore(lore.stream().map(this::color).toList());
            GuiItemFlags.hideAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String color(String value) {
        return ColorUtil.colorize(value);
    }
}
