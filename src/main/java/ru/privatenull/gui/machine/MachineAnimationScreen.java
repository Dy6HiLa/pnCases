package ru.privatenull.gui.machine;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.cases.CaseManager;
import ru.privatenull.cases.model.AnimationType;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.util.ServerCompatibility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static ru.privatenull.gui.machine.MachineSlots.SLOT_ANIMATION_CYCLE;
import static ru.privatenull.gui.machine.MachineSlots.SLOT_ANIMATION_DURATION;
import static ru.privatenull.gui.machine.MachineSlots.SLOT_ANIMATION_MODE;
import static ru.privatenull.gui.machine.MachineSlots.SLOT_ANIMATION_RISE;
import static ru.privatenull.gui.machine.MachineSlots.SLOT_ANIMATION_SPIN;
import static ru.privatenull.gui.machine.MachineSlots.SLOT_BACK;

final class MachineAnimationScreen {

    private final CaseManager caseManager;
    private final MachineItemFactory items;

    MachineAnimationScreen(CaseManager caseManager, MachineItemFactory items) {
        this.caseManager = caseManager;
        this.items = items;
    }

    void open(Player player, String caseName) {
        CaseDefinition definition = caseManager.getCaseByName(caseName);
        if (definition == null) return;

        Inventory inventory = Bukkit.createInventory(
                MachineGuiHolder.animation(caseName),
                54,
                caseManager.getPlugin().getGuiConfig().text(
                        "machine.titles.animation",
                        "&8Настройка анимации",
                        items.replacements(definition)
                )
        );
        items.fill(inventory, items.pane(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        inventory.setItem(4, items.configuredButton(
                "machine.animation.header",
                machineIcon(Material.NETHER_STAR, Material.CLOCK),
                "&#82DCFF&lНастройка анимации",
                headerLore(),
                definition
        ));
        inventory.setItem(SLOT_ANIMATION_MODE, modeButton(definition));
        inventory.setItem(SLOT_ANIMATION_DURATION, number(
                definition, "machine.animation.duration", Material.REPEATER,
                "&#429F91Длительность", definition.durationTicks(), "тиков",
                List.of("&7Сколько длится открытие кейса.", "",
                        "&7ЛКМ &8— &f+5", "&7ПКМ &8— &f-5", "&7Shift &8— &fшаг 20")
        ));
        inventory.setItem(SLOT_ANIMATION_CYCLE, number(
                definition, "machine.animation.cycle", Material.COMPARATOR,
                "&#429F91Смена предметов", definition.cycleEveryTicks(), "тиков",
                List.of("&7Меньше значение &8— &fбыстрее рулетка.", "",
                        "&7ЛКМ &8— &f+1", "&7ПКМ &8— &f-1")
        ));
        inventory.setItem(SLOT_ANIMATION_RISE, number(
                definition, "machine.animation.rise", Material.FEATHER,
                "&#429F91Высота подъёма", round1(definition.riseBlocks()), "блоков",
                List.of("&7Насколько высоко поднимается награда.", "",
                        "&7ЛКМ &8— &f+0.1", "&7ПКМ &8— &f-0.1", "&7Shift &8— &fшаг 0.5")
        ));
        inventory.setItem(SLOT_ANIMATION_SPIN, number(
                definition, "machine.animation.spin", Material.ENDER_EYE,
                "&#429F91Вращение", round1(definition.spinDegreesPerTick()), "град./тик",
                List.of("&7Как быстро крутится награда.", "",
                        "&7ЛКМ &8— &f+1", "&7ПКМ &8— &f-1", "&7Shift &8— &fшаг 10")
        ));
        inventory.setItem(SLOT_BACK, items.backButton(definition));
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.18f, 1.25f);
    }

    AnimationType[] availableTypes() {
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

    AnimationType nextMode(AnimationType current, boolean backwards) {
        return AnimationModeCycle.next(current, availableTypes(), backwards);
    }

    private ItemStack modeButton(CaseDefinition definition) {
        boolean legacy = ServerCompatibility.useMinecraft1165AnimationMode();
        AnimationType fixed = legacy ? AnimationType.FORTUNE_RING : definition.fixedAnimation();
        String mode = fixed == null ? "&aВыбор игрока" : fixed.displayName();
        String description = fixed == null
                ? "&7Игрок выбирает анимацию в меню кейса."
                : fixed.description().replace('\n', ' ');
        String state = legacy ? "&eОграничено версией 1.16.5"
                : fixed == null ? "&aВыбирает игрок" : "&bЗафиксирована для кейса";
        Material icon = fixed == null ? Material.COMPASS : animationIcon(fixed);
        return items.configuredButton(
                "machine.animation.mode",
                icon,
                "&#82DCFF&lРежим: {mode}",
                List.of(
                        "",
                        "&#82DCFF «Текущий режим»",
                        " &7- &f{mode}",
                        " &7- {description}",
                        "",
                        "&#9AEF8A «Состояние»",
                        " &7- {state}",
                        "",
                        "&#FFC67A «Действие»",
                        legacy ? " &7- &fНа 1.16.5 доступен только Круг фортуны"
                                : " &7- &fЛКМ &8— &fследующий режим",
                        legacy ? "" : " &7- &fПКМ &8— &fпредыдущий режим"
                ),
                definition,
                "mode", mode,
                "animation", mode,
                "description", description,
                "state", state,
                "status", state
        );
    }

    private List<String> headerLore() {
        if (ServerCompatibility.useMinecraft1165AnimationMode()) {
            return List.of(
                    "",
                    "&#82DCFF «Совместимость»",
                    " &7- &fДля Minecraft 1.16.5 доступен",
                    " &7- &fтолько Круг фортуны.",
                    ""
            );
        }
        return List.of(
                "",
                "&#82DCFF «Основное»",
                " &7- &fОдна кнопка переключает режим",
                " &7- &fоткрытия этого кейса.",
                ""
        );
    }

    private ItemStack number(
            CaseDefinition definition,
            String path,
            Material material,
            String name,
            Number value,
            String unit,
            List<String> controls
    ) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("&7Значение: &f" + value + " " + unit);
        lore.add("");
        lore.addAll(controls);
        return items.configuredButton(path, material, name, lore, definition,
                "value", String.valueOf(value), "unit", unit == null ? "" : unit);
    }

    private static Material animationIcon(AnimationType type) {
        if (ServerCompatibility.useMinecraft1165AnimationMode() && type == AnimationType.FORTUNE_RING) {
            return Material.CLOCK;
        }
        return type.icon();
    }

    private static Material machineIcon(Material modern, Material legacy) {
        return ServerCompatibility.useMinecraft1165AnimationMode() ? legacy : modern;
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
