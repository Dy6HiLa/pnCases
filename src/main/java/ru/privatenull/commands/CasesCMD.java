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
import ru.privatenull.pnCases;
import ru.privatenull.update.UpdateChecker;

import java.util.UUID;

public class CasesCMD implements CommandExecutor {

    private final pnCases plugin;
    private final CaseManager caseManager;

    public CasesCMD(pnCases plugin, CaseManager caseManager) {
        this.plugin = plugin;
        this.caseManager = caseManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("pncases.admin")) {
            sender.sendMessage(plugin.getMessages().get("no-permission"));
            return true;
        }

        if (args.length == 0) {
            sendVersionInfo(sender);
            plugin.getMessages().getList("command-help").forEach(sender::sendMessage);
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
                p.sendMessage(plugin.getMessages().get("givekey-success-target",
                        "amount", String.valueOf(amount),
                        "key_name", keyName,
                        "balance", String.valueOf(bal)));
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

        if (args[0].equalsIgnoreCase("setcase")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(plugin.getMessages().get("only-player"));
                return true;
            }

            if (args.length < 2) {
                p.sendMessage(plugin.getMessages().get("setcase-usage"));
                return true;
            }

            String caseName = args[1].toLowerCase();
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
        sendVersionInfo(sender);
        plugin.getMessages().getList("command-help").forEach(sender::sendMessage);
        return true;
    }

    private void sendVersionInfo(CommandSender sender) {
        String current = plugin.getDescription().getVersion();
        sender.sendMessage(plugin.getMessages().get("command-version", "version", current));

        UpdateChecker checker = plugin.getUpdateChecker();
        if (checker == null || !checker.isCheckCompleted()) {
            sender.sendMessage(plugin.getMessages().get("command-update-checking"));
            return;
        }

        if (checker.isUpdateAvailable()) {
            sender.sendMessage(plugin.getMessages().get("command-update-available",
                    "current", current,
                    "latest", nullToUnknown(checker.getLatestVersion())));
            sender.sendMessage(plugin.getMessages().get("command-update-download",
                    "url", checker.getDownloadUrl()));
            return;
        }

        String error = checker.getLastError();
        if (error != null && !error.isBlank()) {
            sender.sendMessage(plugin.getMessages().get("command-update-error",
                    "error", ChatColor.stripColor(error) == null ? error : ChatColor.stripColor(error)));
            return;
        }

        sender.sendMessage(plugin.getMessages().get("command-update-latest",
                "latest", nullToUnknown(checker.getLatestVersion())));
    }

    private static String nullToUnknown(String value) {
        return value == null || value.isBlank() ? "неизвестно" : value;
    }

    private static OfflinePlayer resolveOfflinePlayer(String input) {
        try {
            return Bukkit.getOfflinePlayer(UUID.fromString(input));
        } catch (IllegalArgumentException ignored) {
        }
        return Bukkit.getOfflinePlayerIfCached(input);
    }
}
