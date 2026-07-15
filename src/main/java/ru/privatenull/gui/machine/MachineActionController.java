package ru.privatenull.gui.machine;

import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.cases.CaseManager;
import ru.privatenull.cases.animation.AnimationWarnings;
import ru.privatenull.cases.model.AnimationType;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.cases.model.IdleParticleSettings;
import ru.privatenull.cases.model.CaseGuiLayoutRules;
import ru.privatenull.util.ServerCompatibility;

import static ru.privatenull.config.ConfigSections.section;
import static ru.privatenull.gui.machine.MachineSlots.*;
import static ru.privatenull.util.ItemFactory.isRealItem;
import static ru.privatenull.util.ItemFactory.writeExactItem;
import static ru.privatenull.util.ItemFactory.writeItem;

final class MachineActionController {

    private final CaseManager caseManager;
    private final MachineCaseState state;
    private final MachineConfigEditor configEditor;
    private final MachineLayoutEditor layoutEditor;
    private final MachineMainScreen mainScreen;
    private final MachineTogglesScreen togglesScreen;
    private final MachineHologramScreen hologramScreen;
    private final MachinePurchaseScreen purchaseScreen;
    private final MachineMenuScreen menuScreen;
    private final MachineAnimationScreen animationScreen;
    private final MachineShowcaseScreen showcaseScreen;
    private final MachineTextEditor textEditor;

    MachineActionController(
            CaseManager caseManager,
            MachineCaseState state,
            MachineConfigEditor configEditor,
            MachineLayoutEditor layoutEditor,
            MachineMainScreen mainScreen,
            MachineTogglesScreen togglesScreen,
            MachineHologramScreen hologramScreen,
            MachinePurchaseScreen purchaseScreen,
            MachineMenuScreen menuScreen,
            MachineAnimationScreen animationScreen,
            MachineShowcaseScreen showcaseScreen,
            MachineTextEditor textEditor
    ) {
        this.caseManager = caseManager;
        this.state = state;
        this.configEditor = configEditor;
        this.layoutEditor = layoutEditor;
        this.mainScreen = mainScreen;
        this.togglesScreen = togglesScreen;
        this.hologramScreen = hologramScreen;
        this.purchaseScreen = purchaseScreen;
        this.menuScreen = menuScreen;
        this.animationScreen = animationScreen;
        this.showcaseScreen = showcaseScreen;
        this.textEditor = textEditor;
    }

    void handle(MachineGuiHolder.Type type, Player player, InventoryClickEvent event, CaseDefinition definition, int slot) {
        switch (type) {
            case MAIN -> handleMain(player, event, definition, slot);
            case ANIMATION -> handleAnimation(player, event, definition, slot);
            case LAYOUT -> layoutEditor.handleClick(player, event, definition, slot);
            case HOLOGRAM -> handleHologram(player, event, definition, slot);
            case PARTICLES -> handleShowcase(player, event, definition, slot);
            case MENU -> handleMenu(player, event, definition, slot);
            case PURCHASE -> handlePurchase(player, event, definition, slot);
            case TOGGLES -> handleToggles(player, definition, slot);
        }
    }

    private void handleMain(Player player, InventoryClickEvent event, CaseDefinition definition, int slot) {
        if (slot == SLOT_MAIN_CLOSE) {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.BLOCK_BARREL_CLOSE, 0.2f, 0.95f);
        } else if (slot == 4) {
            switchTemplate(player, definition, event.isRightClick());
        } else if (slot == SLOT_MAIN_ANIMATION) {
            animationScreen.open(player, definition.name());
        } else if (slot == SLOT_MAIN_MENU) {
            menuScreen.open(player, definition.name());
        } else if (slot == SLOT_MAIN_TOGGLES) {
            togglesScreen.open(player, definition.name());
        } else if (slot == SLOT_MAIN_HOLOGRAM) {
            hologramScreen.open(player, definition.name());
        } else if (slot == SLOT_MAIN_PARTICLES) {
            showcaseScreen.open(player, definition.name());
        } else if (slot == SLOT_MAIN_PURCHASE) {
            purchaseScreen.open(player, definition.name());
        }
    }

    private void switchTemplate(Player player, CaseDefinition definition, boolean backwards) {
        java.util.List<String> templates = caseManager.getBaseTemplateNames();
        if (templates.isEmpty()) return;
        String current = caseManager.getCaseTemplate(definition.name());
        int index = templates.indexOf(current);
        int next = backwards
                ? (index - 1 + templates.size()) % templates.size()
                : (index + 1 + templates.size()) % templates.size();
        if (index < 0) next = backwards ? templates.size() - 1 : 0;
        String template = templates.get(next);
        if (!caseManager.applyCaseTemplate(definition.name(), template)) {
            player.sendMessage(caseManager.getPlugin().getMessages().get("machine-save-failed"));
            return;
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.2f, 1.3f);
        mainScreen.open(player, definition.name());
    }

    private void handleToggles(Player player, CaseDefinition definition, int slot) {
        if (backToMain(player, definition, slot)) return;

        if (slot == SLOT_TOGGLES_HOLOGRAM) {
            update(player, definition, root -> section(root, "hologram").set("enabled", !state.hologramEnabled(definition)));
            togglesScreen.open(player, definition.name());
        } else if (slot == SLOT_TOGGLES_SHOWCASE) {
            update(player, definition, root -> section(root, "idle-particles").set("enabled", !definition.idleParticles().enabled()));
            togglesScreen.open(player, definition.name());
        } else if (slot == SLOT_TOGGLES_EFFECTS) {
            update(player, definition, root -> section(root, "idle-particles").set("effects", !definition.idleParticles().effectsEnabled()));
            togglesScreen.open(player, definition.name());
        } else if (slot == SLOT_TOGGLES_XP_BUY) {
            update(player, definition, root -> section(root, "cost").set("buy_xp_enabled", !state.xpBuyEnabled(definition)));
            togglesScreen.open(player, definition.name());
        } else if (slot == SLOT_TOGGLES_PARTICLES_MENU) {
            showcaseScreen.open(player, definition.name());
        } else if (slot == SLOT_TOGGLES_HOLOGRAM_MENU) {
            hologramScreen.open(player, definition.name());
        } else if (slot == SLOT_TOGGLES_PURCHASE_MENU) {
            purchaseScreen.open(player, definition.name());
        }
    }

    private void handleAnimation(Player player, InventoryClickEvent event, CaseDefinition definition, int slot) {
        if (backToMain(player, definition, slot)) return;

        if (slot == SLOT_ANIMATION_MODE) {
            if (ServerCompatibility.useMinecraft1165AnimationMode()) {
                player.sendMessage(caseManager.getPlugin().getMessages().get("animation-legacy-only"));
                return;
            }
            AnimationType selected = animationScreen.nextMode(definition.fixedAnimation(), event.isRightClick());
            configEditor.updateAnimation(player, definition.name(), root -> section(root, "animation").set(
                    "fixed", selected == null ? null : selected.name()
            ));
            AnimationWarnings.warnIfMobBased(caseManager.getPlugin(), player, selected);
            animationScreen.refreshMode(player, caseManager.getCaseByName(definition.name()));
            return;
        }
        // Only ready-made animation modes are exposed in the GUI.
    }

    private void handleHologram(Player player, InventoryClickEvent event, CaseDefinition definition, int slot) {
        if (backToMain(player, definition, slot)) return;

        if (slot == SLOT_HOLOGRAM_TOGGLE) {
            update(player, definition, root -> section(root, "hologram").set("enabled", !state.hologramEnabled(definition)));
            hologramScreen.open(player, definition.name());
        } else if (slot == SLOT_HOLOGRAM_HEIGHT) {
            double next = boundedStep(state.hologramHeight(definition), event, 0.1, 1.0, -5.0, 10.0, 1);
            update(player, definition, root -> {
                ConfigurationSection hologram = section(root, "hologram");
                hologram.set("enabled", true);
                hologram.set("y", next);
            });
            hologramScreen.open(player, definition.name());
        } else if (slot == SLOT_HOLOGRAM_LINES) {
            textEditor.start(player, definition, MachineTextField.HOLOGRAM_LINES);
        }
    }

    private void handleShowcase(Player player, InventoryClickEvent event, CaseDefinition definition, int slot) {
        if (backToMain(player, definition, slot)) return;

        IdleParticleSettings settings = definition.idleParticles();
        ItemStack cursor = event.getCursor();
        if (slot == SLOT_PARTICLES_ITEM && isRealItem(cursor)) {
            update(player, definition, root -> writeExactItem(section(root, "idle-particles"), "item", cursor));
        } else if (slot == SLOT_PARTICLES_TOGGLE) {
            update(player, definition, root -> section(root, "idle-particles").set("enabled", !settings.enabled()));
        } else if (slot == SLOT_PARTICLES_EFFECTS) {
            update(player, definition, root -> section(root, "idle-particles").set("effects", !settings.effectsEnabled()));
        } else if (slot == SLOT_PARTICLES_ITEM && event.isRightClick()) {
            update(player, definition, root -> section(root, "idle-particles").set("item", null));
        } else if (slot == SLOT_PARTICLES_STYLE) {
            IdleParticleSettings.Style next = nextEnum(settings.style(), IdleParticleSettings.Style.values(), event.isRightClick());
            update(player, definition, root -> section(root, "idle-particles").set("style", next.name()));
        } else if (slot == SLOT_PARTICLES_THEME) {
            IdleParticleSettings.Theme next = nextEnum(settings.theme(), IdleParticleSettings.Theme.values(), event.isRightClick());
            update(player, definition, root -> section(root, "idle-particles").set("theme", next.name()));
        } else {
            return;
        }
        showcaseScreen.open(player, definition.name());
    }

    private void handleMenu(Player player, InventoryClickEvent event, CaseDefinition definition, int slot) {
        if (backToMain(player, definition, slot)) return;

        if (slot == SLOT_GUI_SIZE) {
            int step = event.isShiftClick() ? 18 : 9;
            int candidate = definition.guiLayout().size() + (event.isRightClick() ? -step : step);
            int next = CaseGuiLayoutRules.normalizeSlotCount(candidate);
            update(player, definition, root -> section(root, "gui").set("size", next));
            menuScreen.open(player, definition.name());
        } else if (slot == SLOT_GUI_TITLE) {
            textEditor.start(player, definition, MachineTextField.GUI_TITLE);
        } else if (slot == SLOT_LAYOUT) {
            layoutEditor.open(player, definition.name());
        } else if (slot == SLOT_PREVIEW_CASE) {
            openCasePreview(player, definition);
        } else {
            replaceMenuItem(player, event, definition, slot);
        }
    }

    private void replaceMenuItem(Player player, InventoryClickEvent event, CaseDefinition definition, int slot) {
        ItemStack cursor = event.getCursor();
        if (!isRealItem(cursor)) return;

        if (slot == SLOT_OPEN_ITEM) {
            update(player, definition, root -> writeItem(section(root, "gui"), "open-item", cursor));
        } else if (slot == SLOT_DECOR_ITEM) {
            update(player, definition, root -> writeItem(section(section(root, "gui"), "decor"), "item", cursor));
        } else if (slot == SLOT_HISTORY_EMPTY_ITEM) {
            update(player, definition, root -> writeItem(section(section(root, "gui"), "history"), "empty-item", cursor));
        } else {
            return;
        }
        menuScreen.open(player, definition.name());
    }

    private void handlePurchase(Player player, InventoryClickEvent event, CaseDefinition definition, int slot) {
        if (backToMain(player, definition, slot)) return;

        if (slot == SLOT_XP_BUY) {
            update(player, definition, root -> section(root, "cost").set("buy_xp_enabled", !state.xpBuyEnabled(definition)));
            purchaseScreen.open(player, definition.name());
        } else if (slot == SLOT_XP_LEVELS) {
            int next = Math.max(0, definition.buyKeyWithXpLevels()
                    + signedStep(event, 1, 5));
            update(player, definition, root -> section(root, "cost").set("buy_xp_levels", next));
            purchaseScreen.open(player, definition.name());
        }
    }

    private boolean backToMain(Player player, CaseDefinition definition, int slot) {
        if (slot != SLOT_BACK) return false;
        mainScreen.open(player, definition.name());
        return true;
    }

    private void openCasePreview(Player player, CaseDefinition definition) {
        caseManager.openCaseGui(player, definition);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.18f, 1.25f);
    }

    private void update(Player player, CaseDefinition definition, java.util.function.Consumer<ConfigurationSection> updater) {
        configEditor.update(player, definition.name(), updater);
    }

    private static int boundedStep(int current, InventoryClickEvent event, int normal, int shifted, int min, int max) {
        return Math.max(min, Math.min(max, current + signedStep(event, normal, shifted)));
    }

    private static int signedStep(InventoryClickEvent event, int normal, int shifted) {
        int step = event.isShiftClick() ? shifted : normal;
        return event.isRightClick() ? -step : step;
    }

    private static double boundedStep(
            double current,
            InventoryClickEvent event,
            double normal,
            double shifted,
            double min,
            double max,
            int scale
    ) {
        double step = event.isShiftClick() ? shifted : normal;
        if (event.isRightClick()) step = -step;
        double multiplier = Math.pow(10.0, scale);
        double rounded = Math.round((current + step) * multiplier) / multiplier;
        return Math.max(min, Math.min(max, rounded));
    }

    private static <T extends Enum<T>> T nextEnum(T current, T[] values, boolean backwards) {
        if (values.length == 0) return current;
        int index = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) {
                index = i;
                break;
            }
        }
        int next = backwards
                ? (index - 1 + values.length) % values.length
                : (index + 1) % values.length;
        return values[next];
    }
}
