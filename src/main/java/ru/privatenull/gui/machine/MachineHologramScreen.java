package ru.privatenull.gui.machine;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.cases.CaseManager;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.util.ServerCompatibility;

import java.util.ArrayList;
import java.util.List;

import static ru.privatenull.gui.machine.MachineSlots.*;

final class MachineHologramScreen {

    private final CaseManager caseManager;
    private final MachineCaseState state;
    private final MachineItemFactory items;

    MachineHologramScreen(CaseManager caseManager, MachineCaseState state, MachineItemFactory items) {
        this.caseManager = caseManager;
        this.state = state;
        this.items = items;
    }

    void open(Player player, String caseName) {
        CaseDefinition definition = caseManager.getCaseByName(caseName);
        if (definition == null) return;
        Inventory inventory = Bukkit.createInventory(
                MachineGuiHolder.hologram(caseName), 54,
                caseManager.getPlugin().getGuiConfig().text(
                        "machine.titles.hologram", "&8Настройка голограммы", items.replacements(definition))
        );
        items.fill(inventory, items.pane(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        inventory.setItem(4, items.configuredButton(
                "machine.hologram.header", machineIcon(Material.ARMOR_STAND, Material.OAK_SIGN),
                "&x&4&2&9&F&9&1Раздел: Голограмма",
                List.of("", "&7Настрой текст над кейсом и его высоту.", ""), definition));
        inventory.setItem(SLOT_HOLOGRAM_TOGGLE, toggle(definition));
        inventory.setItem(SLOT_HOLOGRAM_HEIGHT, height(definition));
        inventory.setItem(SLOT_HOLOGRAM_LINES, lines(definition));
        inventory.setItem(SLOT_BACK, items.backButton(definition));
        caseManager.getPlugin().getGuiOpenAnimations().open(player, inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.18f, 1.25f);
    }

    private ItemStack toggle(CaseDefinition definition) {
        boolean enabled = state.hologramEnabled(definition);
        return items.configuredButton("machine.hologram.toggle",
                enabled ? Material.LIME_DYE : Material.GRAY_DYE,
                "&x&4&2&9&F&9&1Голограмма", List.of(
                        "", "&7Статус: " + (enabled ? "&aвключена" : "&cвыключена"),
                        "&7Высота: &f" + state.hologramHeight(definition), "",
                        "&7ЛКМ &8— &fпереключить"), definition,
                "status", enabled ? "&aвключена" : "&cвыключена",
                "action", enabled ? "выключить" : "включить");
    }

    private ItemStack height(CaseDefinition definition) {
        return items.configuredButton("machine.hologram.height", Material.ARMOR_STAND,
                "&x&4&2&9&F&9&1Высота голограммы", List.of(
                        "", "&7Текущая высота: &f" + state.hologramHeight(definition), "",
                        "&7ЛКМ &8— &f+0.1", "&7ПКМ &8— &f-0.1",
                        "&7Shift + ЛКМ &8— &f+1.0", "&7Shift + ПКМ &8— &f-1.0"), definition,
                "value", String.valueOf(state.hologramHeight(definition)));
    }

    private ItemStack lines(CaseDefinition definition) {
        List<String> configured = state.hologramLines(definition);
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("&7Сейчас:");
        if (configured.isEmpty()) {
            lore.add("&8строк нет");
        } else {
            configured.forEach(line -> lore.add("&f" + line));
        }
        lore.add("");
        lore.add("&7ЛКМ &8— &fнаписать строки в чат");
        lore.add("&7Разделяй строки знаком &b|");
        String visible = configured.isEmpty() ? "&8строк нет" : String.join("|", configured);
        return items.configuredButton("machine.hologram.lines", Material.OAK_SIGN,
                "&x&4&2&9&F&9&1Текст голограммы", lore, definition, "lines", visible);
    }

    private static Material machineIcon(Material modern, Material legacy) {
        return ServerCompatibility.useMinecraft1165AnimationMode() ? legacy : modern;
    }
}
