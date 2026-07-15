package ru.privatenull.cases.animation;

import org.bukkit.entity.Player;
import ru.privatenull.PnCasesPlugin;
import ru.privatenull.cases.model.AnimationType;

public final class AnimationWarnings {
    private AnimationWarnings() {
    }

    public static void warnIfMobBased(PnCasesPlugin plugin, Player player, AnimationType type) {
        if (plugin == null || player == null
                || (type != AnimationType.MOB_HUNT && type != AnimationType.PILLAGER_RAID)) return;
        if (!player.hasPermission("pncases.admin")) return;
        player.sendMessage(plugin.getMessages().get("animation-mob-warning-self",
                "animation", type.displayName()));
    }
}
