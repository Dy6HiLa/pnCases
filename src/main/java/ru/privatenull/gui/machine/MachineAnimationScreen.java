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

import static ru.privatenull.gui.machine.MachineSlots.SLOT_ANIMATION_MODE;
import static ru.privatenull.gui.machine.MachineSlots.SLOT_BACK;

final class MachineAnimationScreen {

    private static final String NEW_TAG = "&#EA3AD2&lɴ&#D744DC&lᴇ&#C34DE5&lᴡ";

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
        inventory.setItem(SLOT_BACK, items.backButton(definition));
        caseManager.getPlugin().getGuiOpenAnimations().open(player, inventory);
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
        String state = legacy ? "&eОграничено версией 1.16.5"
                : fixed == null ? "&aВыбирает игрок" : "&bЗафиксирована для кейса";
        Material icon = fixed == null ? Material.COMPASS : animationIcon(fixed);
        String newTag = fixed == AnimationType.MOB_HUNT || fixed == AnimationType.AQUARIUM
                ? " &r" + NEW_TAG
                : "";
        return items.builtInButton(icon, "&#82DCFF&lРежим &8• &e" + mode + newTag, modeLore(fixed, state, legacy));
    }

    void refreshMode(Inventory inventory, CaseDefinition definition) {
        if (inventory != null && definition != null) {
            inventory.setItem(SLOT_ANIMATION_MODE, modeButton(definition));
        }
    }

    void refreshMode(Player player, CaseDefinition definition) {
        if (definition != null) {
            caseManager.getPlugin().getGuiUpdates().setTopSlot(player, SLOT_ANIMATION_MODE, modeButton(definition));
        }
    }

    private List<String> modeLore(AnimationType fixed, String state, boolean legacy) {
        List<String> lore = new ArrayList<>();
        lore.add("&8Настройка способа открытия кейса");
        lore.add("");
        String animationName = fixed == null ? "&aВыбор игрока" : fixed.displayName();
        lore.add("&b● &fАнимация: " + animationName);
        if (fixed == null) {
            lore.add("&7Игрок выбирает анимацию в меню кейса.");
        } else {
            for (String line : fixed.description().split("\\R")) {
                lore.add(line);
            }
        }
        lore.add("");
        lore.add("&a● &fСтатус: " + state);
        lore.add("");
        if (legacy) {
            lore.add("&eТолько Круг фортуны доступен на 1.16.5.");
        } else {
            lore.add("&6ЛКМ &8• &fСледующий режим");
            lore.add("&6ПКМ &8• &fПредыдущий режим");
        }
        return lore;
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
                " &7- &fВыберите готовый режим открытия.",
                " &7- &fВсе параметры подобраны автоматически.",
                ""
        );
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

}
