package ru.privatenull.gui.machine;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.cases.CaseManager;
import ru.privatenull.cases.model.CaseDefinition;

import java.util.List;

import static ru.privatenull.gui.machine.MachineSlots.*;

final class MachinePurchaseScreen {

    private final CaseManager caseManager;
    private final MachineCaseState state;
    private final MachineItemFactory items;

    MachinePurchaseScreen(CaseManager caseManager, MachineCaseState state, MachineItemFactory items) {
        this.caseManager = caseManager;
        this.state = state;
        this.items = items;
    }

    void open(Player player, String caseName) {
        CaseDefinition definition = caseManager.getCaseByName(caseName);
        if (definition == null) return;
        Inventory inventory = Bukkit.createInventory(
                MachineGuiHolder.purchase(caseName), 54,
                caseManager.getPlugin().getGuiConfig().text(
                        "machine.titles.purchase", "&8Покупка за опыт", items.replacements(definition))
        );
        items.fill(inventory, items.pane(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        inventory.setItem(4, items.configuredButton(
                "machine.purchase.header", Material.EXPERIENCE_BOTTLE,
                "&x&4&2&9&F&9&1Раздел: Покупка за опыт", List.of(
                        "", "&7Если включено, игрок может купить ключ",
                        "&7за уровни опыта прямо в меню кейса.", ""), definition));
        inventory.setItem(SLOT_XP_BUY, toggle(definition));
        inventory.setItem(SLOT_XP_LEVELS, levels(definition));
        inventory.setItem(SLOT_BACK, items.backButton(definition));
        caseManager.getPlugin().getGuiOpenAnimations().open(player, inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.18f, 1.25f);
    }

    private ItemStack toggle(CaseDefinition definition) {
        boolean enabled = state.xpBuyEnabled(definition);
        int levels = Math.max(0, definition.buyKeyWithXpLevels());
        return items.configuredButton("machine.purchase.toggle",
                enabled ? Material.EXPERIENCE_BOTTLE : Material.GLASS_BOTTLE,
                "&x&4&2&9&F&9&1Покупка за опыт", List.of(
                        "", "&7Статус: " + (enabled ? "&aвключена" : "&cвыключена"),
                        "&7Цена: &f" + levels + " уровней", "",
                        "&7ЛКМ &8— &fпереключить"), definition,
                "status", enabled ? "&aвключена" : "&cвыключена",
                "action", enabled ? "выключить" : "включить",
                "levels", String.valueOf(levels));
    }

    private ItemStack levels(CaseDefinition definition) {
        boolean enabled = state.xpBuyEnabled(definition);
        int levels = Math.max(0, definition.buyKeyWithXpLevels());
        return items.configuredButton("machine.purchase.levels", Material.EXPERIENCE_BOTTLE,
                "&x&4&2&9&F&9&1Цена покупки за опыт", List.of(
                        "", "&7Текущая цена: &f" + levels + " уровней",
                        "&7Статус покупки: " + (enabled ? "&aвключена" : "&cвыключена"), "",
                        "&7ЛКМ &8— &f+1 уровень", "&7ПКМ &8— &f-1 уровень",
                        "&7Shift &8— &fшаг 5 уровней"), definition,
                "levels", String.valueOf(levels),
                "status", enabled ? "&aвключена" : "&cвыключена");
    }
}
