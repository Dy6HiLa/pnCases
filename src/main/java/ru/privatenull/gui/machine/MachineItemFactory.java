package ru.privatenull.gui.machine;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.privatenull.cases.CaseManager;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.pnlibrary.text.ColorUtil;
import ru.privatenull.util.EnchantmentCompat;
import ru.privatenull.util.GuiItemFlags;

import java.util.ArrayList;
import java.util.List;

import static ru.privatenull.util.ItemNames.readableItemName;

final class MachineItemFactory {

    private final CaseManager caseManager;
    private final MachineCaseState state;

    MachineItemFactory(CaseManager caseManager, MachineCaseState state) {
        this.caseManager = caseManager;
        this.state = state;
    }

    ItemStack selectable(String path, Material material, boolean selected, String name, List<String> lore,
                         CaseDefinition definition, String... extra) {
        String prefix = selected ? "&a◆ " : "&7◇ ";
        List<String> replacements = new ArrayList<>(List.of(
                "prefix", prefix,
                "selected_prefix", prefix,
                "selected-prefix", prefix,
                "selected", selected ? "&aда" : "&7нет",
                "name", name,
                "display", name,
                "display_name", name,
                "display-name", name));
        append(replacements, extra);
        String[] values = definition == null
                ? replacements.toArray(String[]::new)
                : replacements(definition, replacements.toArray(String[]::new));
        ItemStack item = button(material,
                caseManager.getPlugin().getGuiConfig().text(path + ".name", prefix + name, values),
                caseManager.getPlugin().getGuiConfig().list(path + ".lore", lore, values));
        if (selected) addGlow(item);
        return item;
    }

    ItemStack copyItemButton(String path, ItemStack source, Material fallback, CaseDefinition definition,
                             String title, String hint) {
        ItemStack item = source == null || source.getType().isAir() ? new ItemStack(fallback) : source.clone();
        item.setAmount(1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        List<String> lore = List.of(
                "",
                "&7" + hint,
                "&7Чтобы заменить, возьми новый предмет на курсор",
                "&7и нажми им по этой кнопке.",
                "",
                "&7Сейчас: &f" + readableItemName(item)
        );
        String[] replacements = replacements(definition,
                "hint", hint,
                "item", readableItemName(item));
        meta.setDisplayName(caseManager.getPlugin().getGuiConfig().text(path + ".name", title, replacements));
        meta.setLore(caseManager.getPlugin().getGuiConfig().list(path + ".lore", lore, replacements));
        GuiItemFlags.hideAttributes(meta);
        item.setItemMeta(meta);
        return item;
    }

    ItemStack sectionButton(String path, Material material, String name, List<String> lore,
                            CaseDefinition definition, String... extra) {
        List<String> formatted = new ArrayList<>(lore.size() + 2);
        formatted.add("");
        formatted.addAll(lore);
        formatted.add("");
        return button(material,
                caseManager.getPlugin().getGuiConfig().text(path + ".name", name,
                        replacements(definition, extra)),
                caseManager.getPlugin().getGuiConfig().list(path + ".lore", formatted,
                        replacements(definition, extra)));
    }

    ItemStack configuredButton(String path, Material material, String name, List<String> lore,
                               CaseDefinition definition, String... extra) {
        return button(material,
                caseManager.getPlugin().getGuiConfig().text(path + ".name", name,
                        replacements(definition, extra)),
                caseManager.getPlugin().getGuiConfig().list(path + ".lore", lore,
                        replacements(definition, extra)));
    }

    ItemStack configuredButton(String path, ItemStack source, String name, List<String> lore,
                               CaseDefinition definition, String... extra) {
        return button(source,
                caseManager.getPlugin().getGuiConfig().text(path + ".name", name,
                        replacements(definition, extra)),
                caseManager.getPlugin().getGuiConfig().list(path + ".lore", lore,
                        replacements(definition, extra)));
    }

    ItemStack builtInButton(Material material, String name, List<String> lore) {
        return button(material, name, lore);
    }

    ItemStack backButton(CaseDefinition definition) {
        return configuredButton("machine.buttons.back", Material.ARROW, "&fНазад",
                List.of("", "&7ЛКМ &8— &fв главное меню"), definition);
    }

    String[] replacements(CaseDefinition definition, String... extra) {
        List<String> replacements = new ArrayList<>();
        add(replacements, "case", color(definition.displayName()));
        add(replacements, "case_id", definition.name());
        add(replacements, "case-id", definition.name());
        add(replacements, "title", definition.guiTitle());
        add(replacements, "size", String.valueOf(definition.guiLayout().size()));
        add(replacements, "rows", String.valueOf(definition.guiLayout().size() / 9));
        String hologram = state.hologramEnabled(definition) ? "&aвключена" : "&cвыключена";
        add(replacements, "hologram_status", hologram);
        add(replacements, "hologram-status", hologram);
        add(replacements, "hologram_height", String.valueOf(state.hologramHeight(definition)));
        add(replacements, "hologram-height", String.valueOf(state.hologramHeight(definition)));
        String showcase = definition.idleParticles().enabled() ? "&aвключена" : "&cвыключена";
        add(replacements, "showcase_status", showcase);
        add(replacements, "showcase-status", showcase);
        String effects = definition.idleParticles().effectsEnabled() ? "&aвключены" : "&cвыключены";
        add(replacements, "effects_status", effects);
        add(replacements, "effects-status", effects);
        add(replacements, "style", definition.idleParticles().style().displayName());
        add(replacements, "theme", definition.idleParticles().theme().displayName());
        String xp = state.xpBuyEnabled(definition) ? "&aвключена" : "&cвыключена";
        add(replacements, "xp_status", xp);
        add(replacements, "xp-status", xp);
        add(replacements, "xp_levels", String.valueOf(Math.max(0, definition.buyKeyWithXpLevels())));
        add(replacements, "xp-levels", String.valueOf(Math.max(0, definition.buyKeyWithXpLevels())));
        add(replacements, "animation", state.currentAnimationLabel(definition));
        append(replacements, extra);
        return replacements.toArray(String[]::new);
    }

    ItemStack pane(Material material, String name, List<String> lore) {
        return button(material, name, lore);
    }

    void fill(Inventory inventory, ItemStack item) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, item.clone());
        }
    }

    private static ItemStack button(Material material, String name, List<String> lore) {
        return button(new ItemStack(material), name, lore);
    }

    private static ItemStack button(ItemStack source, String name, List<String> lore) {
        ItemStack item = source == null || source.getType().isAir()
                ? new ItemStack(Material.STONE_BUTTON) : source.clone();
        item.setAmount(1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(color(name));
        meta.setLore(lore.stream().map(MachineItemFactory::color).toList());
        GuiItemFlags.hideAttributes(meta);
        item.setItemMeta(meta);
        return item;
    }

    private static void addGlow(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        var unbreaking = EnchantmentCompat.unbreaking();
        if (unbreaking != null) meta.addEnchant(unbreaking, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
    }

    private static void append(List<String> values, String... extra) {
        for (int index = 0; index + 1 < extra.length; index += 2) {
            add(values, extra[index], extra[index + 1]);
        }
    }

    private static void add(List<String> values, String key, String value) {
        values.add(key);
        values.add(value);
    }

    private static String color(String value) {
        return ColorUtil.colorize(value);
    }
}
