package ru.privatenull.gui.machine;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.cases.CaseManager;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.cases.model.IdleParticleSettings;
import ru.privatenull.util.ServerCompatibility;

import java.util.ArrayList;
import java.util.List;

import static ru.privatenull.gui.machine.MachineSlots.*;

final class MachineTogglesScreen {

    private final CaseManager caseManager;
    private final MachineCaseState state;
    private final MachineItemFactory items;

    MachineTogglesScreen(CaseManager caseManager, MachineCaseState state, MachineItemFactory items) {
        this.caseManager = caseManager;
        this.state = state;
        this.items = items;
    }

    void open(Player player, String caseName) {
        CaseDefinition definition = caseManager.getCaseByName(caseName);
        if (definition == null) return;
        if (MachineScreenRefresh.refreshIfOpen(
                caseManager, player, MachineGuiHolder.Type.TOGGLES, definition.name(),
                inventory -> fill(inventory, definition))) {
            return;
        }
        Inventory inventory = Bukkit.createInventory(
                MachineGuiHolder.toggles(caseName),
                54,
                caseManager.getPlugin().getGuiConfig().text(
                        "machine.titles.toggles", "&8Быстрые настройки", items.replacements(definition))
        );
        fill(inventory, definition);
        caseManager.getPlugin().getGuiOpenAnimations().open(player, inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.18f, 1.25f);
    }

    private void fill(Inventory inventory, CaseDefinition definition) {
        IdleParticleSettings settings = definition.idleParticles();
        items.fill(inventory, items.pane(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        inventory.setItem(4, items.configuredButton("machine.toggles.header", Material.LEVER,
                "&x&4&2&9&F&9&1Раздел: Быстрые настройки", List.of(
                        "", "&7Здесь можно быстро включить или выключить",
                        "&7то, что игрок видит около кейса и в меню.", ""), definition));
        inventory.setItem(SLOT_TOGGLES_HOLOGRAM, toggle("machine.toggles.hologram",
                state.hologramEnabled(definition) ? Material.LIME_DYE : Material.GRAY_DYE,
                "&x&4&2&9&F&9&1Голограмма над кейсом", state.hologramEnabled(definition),
                List.of("&7Показывает текст над блоком кейса.",
                        "&7Если выключить, текста над кейсом не будет."), definition));
        inventory.setItem(SLOT_TOGGLES_SHOWCASE, toggle("machine.toggles.showcase",
                settings.enabled() ? Material.ITEM_FRAME : Material.GRAY_DYE,
                "&x&4&2&9&F&9&1Витрина над кейсом", settings.enabled(),
                List.of("&7Показывает предмет над свободным кейсом.",
                        "&7Во время открытия витрина скрывается."), definition));
        inventory.setItem(SLOT_TOGGLES_EFFECTS, toggle("machine.toggles.effects",
                settings.effectsEnabled() ? Material.GLOWSTONE_DUST : Material.GRAY_DYE,
                "&x&4&2&9&F&9&1Эффекты витрины", settings.effectsEnabled(),
                List.of("&7Добавляет аккуратные частицы вокруг витрины.",
                        "&7Если выключить, останется только предмет."), definition));
        inventory.setItem(SLOT_TOGGLES_XP_BUY, toggle("machine.toggles.xp-buy",
                state.xpBuyEnabled(definition) ? Material.EXPERIENCE_BOTTLE : Material.GLASS_BOTTLE,
                "&x&4&2&9&F&9&1Покупка ключа за опыт", state.xpBuyEnabled(definition),
                List.of("&7Игрок сможет купить ключ за уровни опыта.",
                        "&7Если выключить, кейс можно открыть только ключом."), definition));

        inventory.setItem(SLOT_TOGGLES_PARTICLES_MENU, items.sectionButton(
                "machine.toggles.details-showcase", Material.ENDER_EYE,
                "&x&4&2&9&F&9&1Подробнее: Витрина", List.of(
                        "&7Предмет витрины, стиль, тема, радиус,",
                        "&7высота, скорость и частота.", "",
                        "&7ЛКМ &8— &fоткрыть подробные настройки"), definition));
        inventory.setItem(SLOT_TOGGLES_HOLOGRAM_MENU, items.sectionButton(
                "machine.toggles.details-hologram", machineIcon(Material.ARMOR_STAND, Material.OAK_SIGN),
                "&x&4&2&9&F&9&1Подробнее: Голограмма", List.of(
                        "&7Текст над кейсом и высота.", "",
                        "&7ЛКМ &8— &fоткрыть подробные настройки"), definition));
        inventory.setItem(SLOT_TOGGLES_PURCHASE_MENU, items.sectionButton(
                "machine.toggles.details-purchase", Material.EXPERIENCE_BOTTLE,
                "&x&4&2&9&F&9&1Подробнее: Покупка", List.of(
                        "&7Цена покупки ключа за уровни опыта.", "",
                        "&7ЛКМ &8— &fоткрыть подробные настройки"), definition));
        inventory.setItem(SLOT_BACK, items.backButton(definition));
    }

    private ItemStack toggle(String path, Material material, String name, boolean enabled,
                             List<String> description, CaseDefinition definition) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("&7Статус: " + (enabled ? "&aвключена" : "&cвыключена"));
        lore.add("");
        lore.addAll(description);
        lore.add("");
        lore.add(enabled ? "&7ЛКМ &8— &fвыключить" : "&7ЛКМ &8— &fвключить");
        return items.configuredButton(path, material, name, lore, definition,
                "status", enabled ? "&aвключена" : "&cвыключена",
                "action", enabled ? "выключить" : "включить");
    }

    private static Material machineIcon(Material modern, Material legacy) {
        return ServerCompatibility.useMinecraft1165AnimationMode() ? legacy : modern;
    }
}
