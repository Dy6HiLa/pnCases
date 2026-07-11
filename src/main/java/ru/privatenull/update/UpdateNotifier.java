package ru.privatenull.update;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import ru.privatenull.PnCasesPlugin;

import java.util.List;

final class UpdateNotifier {

    private final PnCasesPlugin plugin;

    UpdateNotifier(PnCasesPlugin plugin) {
        this.plugin = plugin;
    }

    String consoleMessage(String latestVersion, String downloadUrl) {
        return """
                ==================== pnCases Обновление ====================
                Доступна новая версия pnCases.
                Установлена: %s
                Новая:       %s
                Скачать:     %s
                Поддержка:   %s
                После замены JAR перезапустите сервер.
                ============================================================
                """.formatted(currentVersion(), latestVersion, downloadUrl, plugin.getSupportDiscord());
    }

    void send(Player player, String latestVersion, String downloadUrl) {
        for (String line : lines(latestVersion)) player.sendMessage(line);
        player.sendMessage(link(
                "§x§4§2§9§F§9§1▸ §fСкачать обновление: §x§D§8§D§F§9§D§n" + downloadUrl,
                downloadUrl,
                "§fНажмите, чтобы открыть ссылку"
        ));
        player.sendMessage(link(
                "§x§4§2§9§F§9§1▸ §fПоддержка Discord: §x§D§8§D§F§9§D§n" + plugin.getSupportDiscord(),
                plugin.getSupportDiscord(),
                "§fНажмите, чтобы открыть Discord"
        ));
        player.sendTitle(
                "§x§4§2§9§F§9§1§lpnCases",
                "§fВышло обновление §x§D§8§D§F§9§D" + latestVersion,
                10, 80, 20
        );
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.45f, 1.6f);
    }

    private Component link(String label, String url, String hover) {
        return Component.text(label)
                .clickEvent(ClickEvent.openUrl(url))
                .hoverEvent(HoverEvent.showText(Component.text(hover)));
    }

    private List<String> lines(String latestVersion) {
        return List.of(
                "",
                "§8§m                                                  ",
                "§x§4§2§9§F§9§1§lᴘ§x§5§B§A§A§9§3§lɴ§x§7§4§B§4§9§5§lᴄ§x§8§D§B§F§9§7§lᴀ§x§A§6§C§A§9§9§lꜱ§x§B§F§D§4§9§B§lᴇ§x§D§8§D§F§9§D§lꜱ §8| §fВышло обновление",
                "",
                "§x§4§2§9§F§9§1▸ §fУстановлена: §7" + currentVersion(),
                "§x§4§2§9§F§9§1▸ §fНовая версия: §x§D§8§D§F§9§D" + latestVersion,
                "§x§4§2§9§F§9§1▸ §fЗамените JAR и перезапустите сервер.",
                "§x§4§2§9§F§9§1▸ §fПоддержка: §x§D§8§D§F§9§D" + plugin.getSupportDiscord(),
                "",
                "§x§4§2§9§F§9§1▸ §7Ссылка ниже кликабельная:",
                "§8§m                                                  ",
                ""
        );
    }

    private String currentVersion() {
        return plugin.getDescription().getVersion();
    }
}
