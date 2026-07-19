package ru.privatenull.gui.machine;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.cases.CaseManager;
import ru.privatenull.cases.model.CaseDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static ru.privatenull.config.ConfigSections.section;
import static ru.privatenull.util.ItemFactory.isRealItem;
import static ru.privatenull.util.ItemFactory.writeExactItem;
import static ru.privatenull.util.ItemNames.readableItemName;

final class MachineLayoutEditor {

    private final CaseManager caseManager;
    private final MachineCaseState state;
    private final MachineItemFactory items;
    private final MachineConfigEditor configEditor;

    MachineLayoutEditor(CaseManager caseManager, MachineCaseState state, MachineItemFactory items,
                        MachineConfigEditor configEditor) {
        this.caseManager = caseManager;
        this.state = state;
        this.items = items;
        this.configEditor = configEditor;
    }

    void open(Player player, String caseName) {
        CaseDefinition definition = caseManager.getCaseByName(caseName);
        if (definition == null) return;
        if (MachineScreenRefresh.refreshIfOpen(
                caseManager, player, MachineGuiHolder.Type.LAYOUT, definition.name(),
                inventory -> fill(inventory, definition))) {
            return;
        }
        Inventory inventory = Bukkit.createInventory(
                MachineGuiHolder.layout(caseName),
                54,
                caseManager.getPlugin().getGuiConfig().text(
                        "machine.titles.layout", "&8Разметка меню кейса", items.replacements(definition))
        );
        fill(inventory, definition);
        caseManager.getPlugin().getGuiOpenAnimations().open(player, inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.18f, 1.15f);
    }

    void handleClick(Player player, InventoryClickEvent event, CaseDefinition definition, int slot) {
        SlotRole current = state.roleAt(definition, slot);
        ItemStack cursor = event.getCursor();
        if (isRealItem(cursor)) {
            applyCursorItem(player, definition, slot, current, cursor);
            return;
        }

        SlotRole next = state.stepRole(current, event.isRightClick());
        if (isRequired(current) && isRequired(next)) {
            swapRequiredRoles(player, definition, slot, current, next);
            return;
        }
        if (isRequired(current) && next != current) {
            player.sendMessage(caseManager.getPlugin().getMessages().get(
                    "machine-layout-required-slot", "slot", current.displayName));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.18f, 1.0f);
            return;
        }
        if (current == SlotRole.ANIMATION && next != current) {
            configEditor.update(player, definition.name(), root -> {
                ConfigurationSection gui = section(root, "gui");
                gui.set("animation_slot", -1);
                setRole(gui, definition, slot, next);
            });
            refresh(player, definition.name());
            return;
        }
        applyRole(player, definition, slot, next);
    }

    private void fill(Inventory inventory, CaseDefinition definition) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, slotItem(definition, slot));
        }
    }

    private void refresh(Player player, String caseName) {
        CaseDefinition updated = caseManager.getCaseByName(caseName);
        if (updated == null) {
            player.closeInventory();
            return;
        }
        if (!MachineScreenRefresh.refreshIfOpen(
                caseManager, player, MachineGuiHolder.Type.LAYOUT, caseName,
                inventory -> fill(inventory, updated))) {
            open(player, caseName);
        }
    }

    private void applyCursorItem(Player player, CaseDefinition definition, int slot,
                                 SlotRole role, ItemStack cursor) {
        if (role == SlotRole.EMPTY) {
            configEditor.update(player, definition.name(), root -> {
                ConfigurationSection gui = section(root, "gui");
                writeExactItem(section(gui, "decor"), "item", cursor);
                setRole(gui, definition, slot, SlotRole.DECOR);
            });
        } else if (role == SlotRole.DECOR) {
            configEditor.update(player, definition.name(), root ->
                    writeExactItem(section(section(root, "gui"), "decor"), "item", cursor));
        } else if (role == SlotRole.OPEN) {
            configEditor.update(player, definition.name(), root ->
                    writeExactItem(section(root, "gui"), "open-item", cursor));
        } else if (role == SlotRole.ANIMATION) {
            configEditor.update(player, definition.name(), root ->
                    writeExactItem(section(root, "gui"), "animation-item", cursor));
        } else if (role == SlotRole.HISTORY) {
            configEditor.update(player, definition.name(), root ->
                    writeExactItem(section(section(root, "gui"), "history"), "empty-item", cursor));
        }
        refresh(player, definition.name());
    }

    private void swapRequiredRoles(Player player, CaseDefinition definition, int clickedSlot,
                                   SlotRole current, SlotRole next) {
        int nextSlot = requiredSlot(definition, next);
        configEditor.update(player, definition.name(), root -> {
            ConfigurationSection gui = section(root, "gui");
            Set<Integer> decor = readSlots(gui.getConfigurationSection("decor"), definition.guiLayout().decorSlots());
            Set<Integer> history = readSlots(gui.getConfigurationSection("history"), definition.guiLayout().historySlots());
            decor.remove(clickedSlot);
            decor.remove(nextSlot);
            history.remove(clickedSlot);
            history.remove(nextSlot);
            setRequiredSlot(gui, next, clickedSlot);
            setRequiredSlot(gui, current, nextSlot);
            section(gui, "decor").set("slots", new ArrayList<>(decor));
            section(gui, "history").set("slots", new ArrayList<>(history));
        });
        refresh(player, definition.name());
    }

    private void applyRole(Player player, CaseDefinition definition, int slot, SlotRole role) {
        configEditor.update(player, definition.name(), root ->
                setRole(section(root, "gui"), definition, slot, role));
        refresh(player, definition.name());
    }

    private void setRole(ConfigurationSection gui, CaseDefinition definition, int slot, SlotRole role) {
        Set<Integer> decor = readSlots(gui.getConfigurationSection("decor"), definition.guiLayout().decorSlots());
        Set<Integer> history = readSlots(gui.getConfigurationSection("history"), definition.guiLayout().historySlots());
        decor.remove(slot);
        history.remove(slot);

        switch (role) {
            case OPEN, ANIMATION -> setRequiredSlot(gui, role, slot);
            case DECOR -> decor.add(slot);
            case HISTORY -> history.add(slot);
            case EMPTY -> { }
        }
        section(gui, "decor").set("slots", new ArrayList<>(decor));
        section(gui, "history").set("slots", new ArrayList<>(history));
    }

    private static boolean isRequired(SlotRole role) {
        return role == SlotRole.OPEN;
    }

    private static void setRequiredSlot(ConfigurationSection gui, SlotRole role, int slot) {
        switch (role) {
            case OPEN -> gui.set("open_slot", slot);
            case ANIMATION -> gui.set("animation_slot", slot);
            default -> throw new IllegalArgumentException("Not a required slot role: " + role);
        }
    }

    private static int requiredSlot(CaseDefinition definition, SlotRole role) {
        return switch (role) {
            case OPEN -> definition.guiLayout().openSlot();
            case ANIMATION -> definition.guiLayout().animationSlot();
            default -> throw new IllegalArgumentException("Not a required slot role: " + role);
        };
    }

    private Set<Integer> readSlots(ConfigurationSection section, List<Integer> fallback) {
        if (section == null || !section.isList("slots")) return new TreeSet<>(fallback);
        Set<Integer> slots = new TreeSet<>();
        for (Object raw : section.getList("slots", List.of())) {
            if (raw instanceof Number number) {
                slots.add(number.intValue());
                continue;
            }
            try {
                slots.add(Integer.parseInt(String.valueOf(raw)));
            } catch (NumberFormatException ex) {
                caseManager.getPlugin().getLogger().warning(
                        "Machine GUI: пропущен неверный номер слота: " + raw);
            }
        }
        slots.removeIf(slot -> slot == null || slot < 0 || slot >= 54);
        return slots;
    }

    private ItemStack slotItem(CaseDefinition definition, int slot) {
        SlotRole role = state.roleAt(definition, slot);
        ItemStack item = roleItem(definition, role);
        String roleName = roleName(role);
        String itemName = role == SlotRole.EMPTY ? "" : readableItemName(item);

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("&7Слот: &f" + slot);
        lore.add("&7Роль: &f" + roleName);
        if (role != SlotRole.EMPTY) lore.add("&7Предмет: &f" + itemName);
        if (slot >= definition.guiLayout().size()) {
            lore.add("&eЭтот слот сохранится, но появится только");
            lore.add("&eкогда размер меню будет больше.");
        }
        if (role == SlotRole.ANIMATION && definition.fixedAnimation() != null) {
            lore.add("&cКнопка скрыта, потому что анимация кейса зафиксирована.");
        }
        if (role == SlotRole.ANIMATION) {
            lore.add("&eПереключите роль слота, чтобы убрать кнопку.");
        }
        lore.add("");
        lore.add("&7ЛКМ &8— &fследующая роль");
        lore.add("&7ПКМ &8— &fпредыдущая роль");
        lore.add("&7Предмет на курсоре &8— &fзаменить предмет роли");
        return items.configuredButton("machine.layout.slot", item, role.color + roleName, lore, definition,
                "slot", String.valueOf(slot),
                "role", roleName,
                "role_color", role.color,
                "role-color", role.color,
                "item", itemName,
                "available", slot >= definition.guiLayout().size() ? "&cнет" : "&aда");
    }

    private String roleName(SlotRole role) {
        String key = role.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        return caseManager.getPlugin().getGuiConfig().text(
                "machine.layout.roles." + key + ".name", role.displayName);
    }

    private ItemStack roleItem(CaseDefinition definition, SlotRole role) {
        ItemStack item = switch (role) {
            case OPEN -> definition.openButton();
            case ANIMATION -> definition.guiLayout().animationItem() == null
                    ? new ItemStack(Material.CLOCK) : definition.guiLayout().animationItem();
            case HISTORY -> definition.guiLayout().emptyHistoryItem();
            case DECOR -> definition.guiLayout().decorItem();
            case EMPTY -> new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        };
        Material fallback = switch (role) {
            case OPEN -> Material.CHEST;
            case ANIMATION -> Material.CLOCK;
            case HISTORY -> Material.BARRIER;
            case DECOR -> Material.GRAY_STAINED_GLASS_PANE;
            case EMPTY -> Material.BLACK_STAINED_GLASS_PANE;
        };
        if (item == null || item.getType().isAir()) return new ItemStack(fallback);
        ItemStack clone = item.clone();
        clone.setAmount(1);
        return clone;
    }

}
