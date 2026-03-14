package ru.privatenull.commands;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.privatenull.cases.CaseManager;
import ru.privatenull.pnCases;

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
            sender.sendMessage(plugin.getMessages().get("givekey-usage"));
            sender.sendMessage(plugin.getMessages().get("setcase-usage"));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            plugin.getMessages().load();
            caseManager.reloadFromConfig();
            sender.sendMessage(plugin.getMessages().get("config-reloaded"));
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
        return true;
    }

    private static OfflinePlayer resolveOfflinePlayer(String input) {
        try {
            return Bukkit.getOfflinePlayer(UUID.fromString(input));
        } catch (IllegalArgumentException ignored) {
        }
        return Bukkit.getOfflinePlayerIfCached(input);
    }
}