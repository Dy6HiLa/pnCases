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

final class MachineMenuScreen {

    private final CaseManager caseManager;
    private final MachineItemFactory items;

    MachineMenuScreen(CaseManager caseManager, MachineItemFactory items) {
        this.caseManager = caseManager;
        this.items = items;
    }

    void open(Player player, String caseName) {
        CaseDefinition definition = caseManager.getCaseByName(caseName);
        if (definition == null) return;
        if (MachineScreenRefresh.refreshIfOpen(
                caseManager, player, MachineGuiHolder.Type.MENU, definition.name(),
                inventory -> fill(inventory, definition))) {
            return;
        }
        Inventory inventory = Bukkit.createInventory(
                MachineGuiHolder.menu(caseName), 54,
                caseManager.getPlugin().getGuiConfig().text(
                        "machine.titles.menu", "&8Настройка меню кейса", items.replacements(definition))
        );
        fill(inventory, definition);
        caseManager.getPlugin().getGuiOpenAnimations().open(player, inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.18f, 1.25f);
    }

    private void fill(Inventory inventory, CaseDefinition definition) {
        items.fill(inventory, items.pane(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        inventory.setItem(4, items.configuredButton(
                "machine.menu.header", Material.CRAFTING_TABLE,
                "&x&4&2&9&F&9&1Раздел: Меню кейса", List.of(
                        "", "&6«Оформление»",
                        "&f- Размер и заголовок игрового меню",
                        "&f- Раскладка, декор и пустая история", "",
                        "&8Положите предмет на курсор и нажмите нужную иконку."), definition));
        inventory.setItem(SLOT_GUI_SIZE, size(definition));
        inventory.setItem(SLOT_GUI_TITLE, title(definition));
        inventory.setItem(SLOT_LAYOUT, items.configuredButton(
                "machine.menu.layout", Material.MAP,
                "&x&4&2&9&F&9&1Расставить слоты", List.of(
                        "", "&7Открывает сетку меню кейса.",
                        "&7ЛКМ/ПКМ меняют роль слота.",
                        "&7Предмет на курсоре заменяет предмет роли.", "",
                        "&7ЛКМ &8— &fоткрыть"), definition));
        inventory.setItem(SLOT_OPEN_ITEM, items.copyItemButton(
                "machine.menu.open-item", definition.openButton(), Material.CHEST, definition,
                "&x&4&2&9&F&9&1Кнопка кейса", "Предмет, по которому игрок открывает кейс."));
        inventory.setItem(SLOT_DECOR_ITEM, items.copyItemButton(
                "machine.menu.decor-item", definition.guiLayout().decorItem(),
                Material.GRAY_STAINED_GLASS_PANE, definition,
                "&x&4&2&9&F&9&1Декор меню", "Предмет, которым заполняются декоративные слоты."));
        inventory.setItem(SLOT_HISTORY_EMPTY_ITEM, items.copyItemButton(
                "machine.menu.history-empty-item", definition.guiLayout().emptyHistoryItem(),
                Material.BARRIER, definition,
                "&x&4&2&9&F&9&1Пустая история",
                "Предмет, который показывается, когда открытий ещё нет."));
        inventory.setItem(SLOT_PREVIEW_CASE, items.configuredButton(
                "machine.menu.preview-case", Material.ENDER_EYE,
                "&x&4&2&9&F&9&1Предпросмотр меню",
                List.of("", "&7Открывает обычное меню кейса.", "", "&7ЛКМ &8— &fпосмотреть"),
                definition));
        inventory.setItem(SLOT_BACK, items.backButton(definition));
    }

    private ItemStack size(CaseDefinition definition) {
        int rows = definition.guiLayout().size() / 9;
        return items.configuredButton("machine.menu.size", Material.CHEST,
                "&x&4&2&9&F&9&1Размер меню", List.of(
                        "", "&7Текущий размер: &f" + definition.guiLayout().size() + " слотов",
                        "&7Рядов: &f" + rows, "",
                        "&7ЛКМ &8— &f+1 ряд", "&7ПКМ &8— &f-1 ряд",
                        "&7Shift &8— &fшаг 2 ряда"), definition,
                "value", String.valueOf(definition.guiLayout().size()),
                "rows", String.valueOf(rows));
    }

    private ItemStack title(CaseDefinition definition) {
        return items.configuredButton("machine.menu.title", Material.NAME_TAG,
                "&x&4&2&9&F&9&1Название меню", List.of(
                        "", "&7Сейчас:", "&f" + definition.guiTitle(), "",
                        "&7ЛКМ &8— &fнаписать новое название в чат"), definition,
                "value", definition.guiTitle());
    }

}
