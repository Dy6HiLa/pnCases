package ru.privatenull.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.privatenull.cases.CaseManager;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.listeners.MachineGuiListener;
import ru.privatenull.pnCases;
import ru.privatenull.update.UpdateChecker;

import java.util.Locale;
import java.util.UUID;

public class CasesCMD implements CommandExecutor {

    private final pnCases plugin;
    private final CaseManager caseManager;
    private final MachineGuiListener machineGui;

    public CasesCMD(pnCases plugin, CaseManager caseManager, MachineGuiListener machineGui) {
        this.plugin = plugin;
        this.caseManager = caseManager;
        this.machineGui = machineGui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("pncases.admin")) {
            sender.sendMessage(plugin.getMessages().get("no-permission"));
            return true;
        }

        if (args.length == 0) {
            sendCommandMenu(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            var validation = plugin.validateCurrentConfig();
            plugin.getMessages().load();
            caseManager.reloadFromConfig();
            plugin.reloadRuntimeConfig();
            sender.sendMessage(plugin.getMessages().get("config-reloaded"));
            if (validation.hasProblems()) {
                sender.sendMessage("§e[pnCases] Config validation: §cошибок " + validation.errors()
                        + "§e, предупреждений §6" + validation.warnings()
                        + "§e. Подробности в консоли сервера.");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("givekey")) {
            if (args.length < 4) {
                sender.sendMessage(plugin.getMessages().get("givekey-usage"));
                return true;
            }

            OfflinePlayer target = resolveOfflinePlayer(args[1]);
            if (target == null) {
                sender.sendMessage(plugin.getMessages().get("givekey-player-not-found"));
                return true;
            }

            String keyId = args[2].toLowerCase();
            int amount;
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage(plugin.getMessages().get("givekey-amount-nan"));
                return true;
            }

            if (amount <= 0) {
                sender.sendMessage(plugin.getMessages().get("givekey-amount-negative"));
                return true;
            }

            // Флаг -s (silent): выдать ключ скрытно, без уведомления игрока.
            // Удобно при выдаче за покупку на сайте.
            boolean silent = false;
            for (int i = 4; i < args.length; i++) {
                // trim() на случай невидимых символов при вводе с игрового чата
                String flag = args[i].trim();
                if (flag.equalsIgnoreCase("-s") || flag.equalsIgnoreCase("-silent")) {
                    silent = true;
                }
            }

            // DEBUG: вывод в консоль для диагностики
            plugin.getLogger().info("[givekey debug] sender=" + sender.getName()
                    + " args.length=" + args.length
                    + " silent=" + silent
                    + " rawArgs=" + java.util.Arrays.toString(args));

            if (!caseManager.keyExists(keyId)) {
                sender.sendMessage(plugin.getMessages().get("givekey-key-not-found", "key", keyId));
                return true;
            }

            plugin.getKeyStorage().add(target.getUniqueId(), keyId, amount);
            String keyName = caseManager.getKeyDisplayName(keyId);

            if (target.isOnline() && target.getPlayer() != null) {
                Player p = target.getPlayer();
                int bal = plugin.getKeyStorage().get(p.getUniqueId(), keyId);
                sender.sendMessage(plugin.getMessages().get("givekey-success-sender",
                        "amount", String.valueOf(amount),
                        "key_name", keyName,
                        "player", p.getName()));
                if (silent) {
                    sender.sendMessage("[pnCases] Silent flag used: player will not receive chat message.");
                } else {
                    p.sendMessage(plugin.getMessages().get("givekey-success-target",
                            "amount", String.valueOf(amount),
                            "key_name", keyName,
                            "balance", String.valueOf(bal)));
                }
            } else {
                String name = target.getName() != null ? target.getName() : target.getUniqueId().toString();
                sender.sendMessage(plugin.getMessages().get("givekey-offline",
                        "amount", String.valueOf(amount),
                        "key_name", keyName,
                        "player", name));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("takekey")) {
            if (args.length < 4) {
                sender.sendMessage(plugin.getMessages().get("takekey-usage"));
                return true;
            }

            OfflinePlayer target = resolveOfflinePlayer(args[1]);
            if (target == null) {
                sender.sendMessage(plugin.getMessages().get("givekey-player-not-found"));
                return true;
            }

            String keyId = args[2].toLowerCase();
            int amount;
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage(plugin.getMessages().get("givekey-amount-nan"));
                return true;
            }

            if (amount <= 0) {
                sender.sendMessage(plugin.getMessages().get("givekey-amount-negative"));
                return true;
            }

            if (!caseManager.keyExists(keyId)) {
                sender.sendMessage(plugin.getMessages().get("givekey-key-not-found", "key", keyId));
                return true;
            }

            String keyName = caseManager.getKeyDisplayName(keyId);
            String playerName = target.getName() != null ? target.getName() : target.getUniqueId().toString();
            boolean taken = plugin.getKeyStorage().take(target.getUniqueId(), keyId, amount);

            if (!taken) {
                int have = plugin.getKeyStorage().get(target.getUniqueId(), keyId);
                sender.sendMessage(plugin.getMessages().get("takekey-not-enough",
                        "player", playerName,
                        "key_name", keyName,
                        "have", String.valueOf(have),
                        "amount", String.valueOf(amount)));
                return true;
            }

            sender.sendMessage(plugin.getMessages().get("takekey-success-sender",
                    "player", playerName,
                    "amount", String.valueOf(amount),
                    "key_name", keyName));

            if (target.isOnline() && target.getPlayer() != null) {
                Player p = target.getPlayer();
                int bal = plugin.getKeyStorage().get(p.getUniqueId(), keyId);
                p.sendMessage(plugin.getMessages().get("takekey-success-target",
                        "amount", String.valueOf(amount),
                        "key_name", keyName,
                        "balance", String.valueOf(bal)));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("delcase")) {
            if (args.length < 2) {
                sender.sendMessage(plugin.getMessages().get("delcase-usage"));
                return true;
            }

            String caseName = args[1].toLowerCase(Locale.ROOT);
            CaseManager.UnbindCaseResult result = caseManager.unbindCaseFromBlock(caseName);
            switch (result) {
                case REMOVED -> sender.sendMessage(plugin.getMessages().get("delcase-success", "case", caseName));
                case NOT_BOUND -> sender.sendMessage(plugin.getMessages().get("delcase-not-bound", "case", caseName));
                case NOT_FOUND -> sender.sendMessage(plugin.getMessages().get("delcase-not-found", "case", caseName));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("machine")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(plugin.getMessages().get("only-player"));
                return true;
            }

            CaseDefinition def = null;
            if (args.length >= 2) {
                def = caseManager.getCaseByName(args[1].toLowerCase(Locale.ROOT));
            } else {
                Block target = p.getTargetBlockExact(6);
                if (target != null) {
                    def = caseManager.getCaseByBlock(target);
                }
            }

            if (def == null) {
                p.sendMessage("§c[pnCases] Посмотри на блок кейса или используй §f/pncases machine <кейс>§c.");
                return true;
            }

            machineGui.openMain(p, def.name());
            return true;
        }

        if (args[0].equalsIgnoreCase("setcase")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(plugin.getMessages().get("only-player"));
                return true;
            }

            if (args.length < 2) {
                p.sendMessage(plugin.getMessages().get("setcase-usage"));
                return true;
            }

            String caseName = args[1].toLowerCase(Locale.ROOT);
            Block target = p.getTargetBlockExact(5);
            if (target == null) {
                p.sendMessage(plugin.getMessages().get("setcase-look-at-block"));
                return true;
            }

            caseManager.bindCaseToBlock(caseName, target);
            p.sendMessage(plugin.getMessages().get("setcase-success",
                    "case", caseName,
                    "world", target.getWorld().getName(),
                    "x", String.valueOf(target.getX()),
                    "y", String.valueOf(target.getY()),
                    "z", String.valueOf(target.getZ())));
            return true;
        }

        sender.sendMessage(plugin.getMessages().get("unknown-command"));
        sendCommandMenu(sender);
        return true;
    }

    private void sendCommandMenu(CommandSender sender) {
        String current = plugin.getDescription().getVersion();
        UpdateInfo updateInfo = buildUpdateInfo(current);
        plugin.getMessages().getList("command-menu",
                "version", current,
                "discord", plugin.getSupportDiscord(),
                "support", plugin.getSupportDiscord(),
                "update", updateInfo.status(),
                "latest", updateInfo.latest(),
                "download", updateInfo.download()
        ).forEach(sender::sendMessage);
    }

    private UpdateInfo buildUpdateInfo(String current) {
        UpdateChecker checker = plugin.getUpdateChecker();
        if (checker == null || !checker.isCheckCompleted()) {
            return new UpdateInfo("&7проверяется", "неизвестно", "");
        }

        if (checker.isUpdateAvailable()) {
            String latest = nullToUnknown(checker.getLatestVersion());
            return new UpdateInfo("&eдоступно &f" + current + " &8→ §x§D§8§D§F§9§D" + latest,
                    latest,
                    nullToUnknown(checker.getDownloadUrl()));
        }

        String error = checker.getLastError();
        if (error != null && !error.isBlank()) {
            String cleanError = ChatColor.stripColor(error) == null ? error : ChatColor.stripColor(error);
            return new UpdateInfo("&cошибка проверки: &f" + cleanError, nullToUnknown(checker.getLatestVersion()), "");
        }

        return new UpdateInfo("&aпоследняя версия", nullToUnknown(checker.getLatestVersion()), "");
    }

    private static String nullToUnknown(String value) {
        return value == null || value.isBlank() ? "неизвестно" : value;
    }

    private record UpdateInfo(String status, String latest, String download) {
    }

    private static OfflinePlayer resolveOfflinePlayer(String input) {
        try {
            return Bukkit.getOfflinePlayer(UUID.fromString(input));
        } catch (IllegalArgumentException ignored) {
        }
        return Bukkit.getOfflinePlayerIfCached(input);
    }
}
