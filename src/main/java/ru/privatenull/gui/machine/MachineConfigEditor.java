package ru.privatenull.gui.machine;

import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import ru.privatenull.cases.CaseManager;

import java.util.function.Consumer;

final class MachineConfigEditor {

    private final CaseManager caseManager;

    MachineConfigEditor(CaseManager caseManager) {
        this.caseManager = caseManager;
    }

    boolean update(Player player, String caseName, Consumer<ConfigurationSection> updater) {
        return update(player, caseName, updater, true, true);
    }

    boolean updateAnimation(Player player, String caseName, Consumer<ConfigurationSection> updater) {
        return update(player, caseName, updater, false, false);
    }

    private boolean update(Player player, String caseName, Consumer<ConfigurationSection> updater,
                           boolean refreshHologram, boolean refreshShowcase) {
        boolean saved = caseManager.updateCaseConfig(caseName, updater, refreshHologram, refreshShowcase);
        player.playSound(player.getLocation(),
                saved ? Sound.UI_BUTTON_CLICK : Sound.ENTITY_VILLAGER_NO,
                0.18f,
                saved ? 1.35f : 1.0f);
        if (!saved) {
            player.sendMessage(caseManager.getPlugin().getMessages().get("machine-save-failed"));
        }
        return saved;
    }
}
