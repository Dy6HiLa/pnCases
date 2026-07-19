package ru.privatenull.gui.machine;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import ru.privatenull.cases.CaseManager;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.pnlibrary.text.ColorUtil;
import ru.privatenull.util.ServerCompatibility;

import java.util.List;

import static ru.privatenull.gui.machine.MachineSlots.*;

final class MachineMainScreen {

    private final CaseManager caseManager;
    private final MachineCaseState state;
    private final MachineItemFactory items;

    MachineMainScreen(CaseManager caseManager, MachineCaseState state, MachineItemFactory items) {
        this.caseManager = caseManager;
        this.state = state;
        this.items = items;
    }

    void open(Player player, String caseName) {
        CaseDefinition definition = caseManager.getCaseByName(caseName);
        if (definition == null) {
            player.sendMessage(caseManager.getPlugin().getMessages().get(
                    "machine-case-not-found", "case", caseName));
            return;
        }
        if (MachineScreenRefresh.refreshIfOpen(
                caseManager, player, MachineGuiHolder.Type.MAIN, definition.name(),
                inventory -> fill(inventory, definition))) {
            return;
        }
        Inventory inventory = Bukkit.createInventory(
                MachineGuiHolder.main(definition.name()),
                54,
                ColorUtil.colorize("&#429F91Настройка кейса &8• &f" + caseName(definition))
        );
        fill(inventory, definition);
        caseManager.getPlugin().getGuiOpenAnimations().open(player, inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_BARREL_OPEN, 0.22f, 1.15f);
    }

    void fill(Inventory inventory, CaseDefinition definition) {
        items.fill(inventory, items.pane(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        inventory.setItem(4, items.builtInButton(Material.CHEST,
                "&x&4&2&9&F&9&1Информация о кейсе",
                List.of(
                        "",
                        "&6«Основное»",
                        "&f- Кейс: &e" + caseName(definition),
                        "&f- ID: &e" + definition.name(),
                        "&f- Настраивается отдельно от других кейсов",
                        "",
                        "&6«Подсказка»",
                        "&fНаграды, цена и оформление",
                        "&fзадаются в конфиге этого кейса.",
                        "",
                        "&8Изменения в других кейсах не затрагиваются."
                )));

        inventory.setItem(SLOT_MAIN_ANIMATION, items.sectionButton(
                "machine.main.animation", machineIcon(Material.NETHER_STAR, Material.CLOCK),
                "&x&4&2&9&F&9&1Анимация", List.of(
                        "&7Выберите готовую анимацию открытия",
                        "&7или разрешите игроку выбирать её самому.", "",
                        "&7Сейчас: " + state.currentAnimationLabel(definition), "",
                        "&7ЛКМ &8— &fоткрыть раздел"), definition));
        inventory.setItem(SLOT_MAIN_MENU, items.sectionButton(
                "machine.main.menu", Material.CRAFTING_TABLE,
                "&x&4&2&9&F&9&1Меню кейса", List.of(
                        "&7Оформление игрового меню: размер, заголовок,",
                        "&7раскладка, декор и пустая история.", "",
                        "&7Размер: &f" + definition.guiLayout().size() + " слотов", "",
                        "&7ЛКМ &8— &fоткрыть раздел"), definition));
        inventory.setItem(SLOT_MAIN_TOGGLES, items.sectionButton(
                "machine.main.toggles", Material.LEVER,
                "&x&4&2&9&F&9&1Быстрые настройки", List.of(
                        "&7Один раздел для включения и выключения.",
                        "&7Голограмма, витрина, эффекты и покупка.", "",
                        "&7Голограмма: " + status(state.hologramEnabled(definition), "включена", "выключена"),
                        "&7Витрина: " + status(definition.idleParticles().enabled(), "включена", "выключена"),
                        "&7Эффекты: " + status(definition.idleParticles().effectsEnabled(), "включены", "выключены"),
                        "&7Покупка за опыт: " + status(state.xpBuyEnabled(definition), "включена", "выключена"),
                        "", "&7ЛКМ &8— &fоткрыть переключатели"), definition));
        inventory.setItem(SLOT_MAIN_HOLOGRAM, items.sectionButton(
                "machine.main.hologram", machineIcon(Material.ARMOR_STAND, Material.OAK_SIGN),
                "&x&4&2&9&F&9&1Голограмма", List.of(
                        "&7Текст над кейсом и высота.", "",
                        "&7Статус: " + status(state.hologramEnabled(definition), "включена", "выключена"),
                        "&7Высота: &f" + state.hologramHeight(definition), "",
                        "&7ЛКМ &8— &fоткрыть раздел"), definition));
        inventory.setItem(SLOT_MAIN_PARTICLES, items.sectionButton(
                "machine.main.showcase", Material.ITEM_FRAME,
                "&x&4&2&9&F&9&1Витрина кейса", List.of(
                        "&7Предмет над кейсом и аккуратные эффекты вокруг.",
                        "&7Витрина скрывается, когда кейс открывают.", "",
                        "&7Статус: " + status(definition.idleParticles().enabled(), "включена", "выключена"),
                        "&7Эффекты: " + status(definition.idleParticles().effectsEnabled(), "включены", "выключены"),
                        "&7Стиль: &f" + definition.idleParticles().style().displayName(),
                        "&7Тема: &f" + definition.idleParticles().theme().displayName(), "",
                        "&7ЛКМ &8— &fоткрыть раздел"), definition));
        inventory.setItem(SLOT_MAIN_PURCHASE, items.sectionButton(
                "machine.main.purchase", Material.EXPERIENCE_BOTTLE,
                "&x&4&2&9&F&9&1Покупка за опыт", List.of(
                        "&7Можно включить покупку ключа за уровни.", "",
                        "&7Статус: " + status(state.xpBuyEnabled(definition), "включена", "выключена"),
                        "&7Цена: &f" + Math.max(0, definition.buyKeyWithXpLevels()) + " уровней", "",
                        "&7ЛКМ &8— &fоткрыть раздел"), definition));
        inventory.setItem(SLOT_MAIN_CLOSE, items.configuredButton(
                "machine.main.close", Material.BARRIER, "&cЗакрыть",
                List.of("", "&7ЛКМ &8— &fзакрыть настройку"), definition));
    }

    /**
     * A case is named for administrators by the key that opens it.  This keeps
     * the Machine screen in sync with the name configured under keys.<id>.name.
     */
    private String caseName(CaseDefinition definition) {
        return definition.displayName();
    }

    private static String status(boolean enabled, String enabledText, String disabledText) {
        return enabled ? "&a" + enabledText : "&c" + disabledText;
    }

    private static Material machineIcon(Material modern, Material legacy) {
        return ServerCompatibility.useMinecraft1165AnimationMode() ? legacy : modern;
    }
}
