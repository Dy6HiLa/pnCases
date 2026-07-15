package ru.privatenull.gui.machine;

import org.bukkit.configuration.ConfigurationSection;
import ru.privatenull.cases.CaseManager;
import ru.privatenull.cases.model.AnimationType;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.cases.model.CaseGuiLayout;
import ru.privatenull.util.ServerCompatibility;

import java.util.ArrayList;
import java.util.List;

final class MachineCaseState {

    private final CaseManager caseManager;

    MachineCaseState(CaseManager caseManager) {
        this.caseManager = caseManager;
    }

    SlotRole roleAt(CaseDefinition definition, int slot) {
        CaseGuiLayout layout = definition.guiLayout();
        if (slot == layout.openSlot()) return SlotRole.OPEN;
        if (slot == layout.previewSlot()) return SlotRole.PREVIEW;
        if (slot == layout.animationSlot()) return SlotRole.ANIMATION;
        if (layout.historySlots().contains(slot)) return SlotRole.HISTORY;
        if (layout.decorSlots().contains(slot)) return SlotRole.DECOR;
        return SlotRole.EMPTY;
    }

    SlotRole stepRole(SlotRole role, boolean backwards) {
        SlotRole next = backwards ? role.previous() : role.next();
        return next;
    }

    String currentAnimationLabel(CaseDefinition definition) {
        if (ServerCompatibility.useMinecraft1165AnimationMode()) {
            return AnimationType.FORTUNE_RING.displayName() + " &8(режим 1.16.5)";
        }
        AnimationType fixed = definition.fixedAnimation();
        return fixed == null ? "&aвыбор игрока" : fixed.displayName();
    }

    double hologramHeight(CaseDefinition definition) {
        ConfigurationSection hologram = section(definition, "hologram");
        return hologram == null ? 1.5
                : round1(hologram.getDouble("y", hologram.getDouble("height", 1.5)));
    }

    List<String> hologramLines(CaseDefinition definition) {
        ConfigurationSection hologram = section(definition, "hologram");
        if (hologram == null) return List.of();
        List<String> lines = hologram.getStringList("lines");
        if (!lines.isEmpty()) return lines;
        String line = hologram.getString("line");
        return line == null || line.isBlank() ? List.of() : List.of(line);
    }

    List<String> parseHologramLines(String message) {
        List<String> lines = new ArrayList<>();
        for (String part : message.split("\\|")) {
            String line = part.trim();
            if (!line.isEmpty()) lines.add(line);
        }
        return lines;
    }

    boolean hologramEnabled(CaseDefinition definition) {
        ConfigurationSection hologram = section(definition, "hologram");
        return hologram != null && hologram.getBoolean("enabled", false);
    }

    boolean xpBuyEnabled(CaseDefinition definition) {
        ConfigurationSection cost = section(definition, "cost");
        if (cost == null) return false;
        if (cost.contains("buy_xp_enabled")) {
            return cost.getBoolean("buy_xp_enabled", definition.buyKeyWithXpLevels() > 0);
        }
        if (cost.contains("buy-xp-enabled")) {
            return cost.getBoolean("buy-xp-enabled", definition.buyKeyWithXpLevels() > 0);
        }
        return definition.buyKeyWithXpLevels() > 0;
    }

    private ConfigurationSection section(CaseDefinition definition, String path) {
        ConfigurationSection root = caseManager.getCaseSection(definition.name());
        return root == null ? null : root.getConfigurationSection(path);
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
