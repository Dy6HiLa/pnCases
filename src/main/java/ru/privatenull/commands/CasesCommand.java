package ru.privatenull.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.privatenull.PnCasesPlugin;
import ru.privatenull.cases.CaseManager;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.gui.machine.MachineGuiListener;
import ru.privatenull.update.UpdateChecker;

import java.util.Locale;
import java.util.UUID;

public final class CasesCommand implements CommandExecutor {

    private final PnCasesPlugin plugin;
    private final CaseManager caseManager;
    private final MachineGuiListener machineGui;

    public CasesCommand(PnCasesPlugin plugin, CaseManager caseManager, MachineGuiListener machineGui) {
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

        boolean known = switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> reload(sender);
            case "givekey" -> giveKey(sender, args);
            case "takekey" -> takeKey(sender, args);
            case "delcase" -> deleteCase(sender, args);
            case "createcase" -> createCase(sender, args);
            case "machine" -> openMachine(sender, args);
            case "setcase" -> setCase(sender, args);
            default -> false;
        };
        if (!known) {
            sender.sendMessage(plugin.getMessages().get("unknown-command"));
            sendCommandMenu(sender);
        }
        return true;
    }

    private boolean reload(CommandSender sender) {
        plugin.reloadConfig();
        var validation = plugin.validateCurrentConfig();
        plugin.getMessages().load();
        plugin.getGuiConfig().load();
        caseManager.reloadFromConfig();
        plugin.reloadRuntimeConfig();
        sender.sendMessage(plugin.getMessages().get("config-reloaded"));
        if (validation.hasProblems()) {
            sender.sendMessage("§e[pnCases] Проверка config.yml: §cошибок " + validation.errors()
                    + "§e, предупреждений §6" + validation.warnings()
                    + "§e. Подробности находятся в консоли сервера.");
        }
        return true;
    }

    private boolean giveKey(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(plugin.getMessages().get("givekey-usage"));
            return true;
        }
        OfflinePlayer target = resolveOfflinePlayer(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.getMessages().get("givekey-player-not-found"));
            return true;
        }

        String keyId = args[2].toLowerCase(Locale.ROOT);
        ParsedAmount parsed = parseAmount(args, 3, true);
        if (parsed.amount() <= 0) {
            sender.sendMessage(plugin.getMessages().get("givekey-amount-nan"));
            return true;
        }
        if (!caseManager.keyExists(keyId)) {
            sender.sendMessage(plugin.getMessages().get("givekey-key-not-found", "key", keyId));
            return true;
        }

        plugin.getKeyStorage().add(target.getUniqueId(), keyId, parsed.amount());
        String keyName = caseManager.getKeyDisplayName(keyId);
        Player online = target.getPlayer();
        if (online != null) {
            int balance = plugin.getKeyStorage().get(online.getUniqueId(), keyId);
            sender.sendMessage(plugin.getMessages().get("givekey-success-sender",
                    "amount", String.valueOf(parsed.amount()), "key_name", keyName, "player", online.getName()));
            if (!parsed.silent()) {
                online.sendMessage(plugin.getMessages().get("givekey-success-target",
                        "amount", String.valueOf(parsed.amount()), "key_name", keyName,
                        "balance", String.valueOf(balance)));
            }
        } else {
            sender.sendMessage(plugin.getMessages().get("givekey-offline",
                    "amount", String.valueOf(parsed.amount()), "key_name", keyName,
                    "player", offlineName(target)));
        }
        return true;
    }

    private boolean takeKey(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(plugin.getMessages().get("takekey-usage"));
            return true;
        }
        OfflinePlayer target = resolveOfflinePlayer(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.getMessages().get("givekey-player-not-found"));
            return true;
        }

        Integer amount = parseInteger(args[3]);
        if (amount == null) {
            sender.sendMessage(plugin.getMessages().get("givekey-amount-nan"));
            return true;
        }
        if (amount <= 0) {
            sender.sendMessage(plugin.getMessages().get("givekey-amount-negative"));
            return true;
        }

        String keyId = args[2].toLowerCase(Locale.ROOT);
        if (!caseManager.keyExists(keyId)) {
            sender.sendMessage(plugin.getMessages().get("givekey-key-not-found", "key", keyId));
            return true;
        }

        String keyName = caseManager.getKeyDisplayName(keyId);
        String playerName = offlineName(target);
        if (!plugin.getKeyStorage().take(target.getUniqueId(), keyId, amount)) {
            int have = plugin.getKeyStorage().get(target.getUniqueId(), keyId);
            sender.sendMessage(plugin.getMessages().get("takekey-not-enough",
                    "player", playerName, "key_name", keyName,
                    "have", String.valueOf(have), "amount", String.valueOf(amount)));
            return true;
        }

        sender.sendMessage(plugin.getMessages().get("takekey-success-sender",
                "player", playerName, "amount", String.valueOf(amount), "key_name", keyName));
        Player online = target.getPlayer();
        if (online != null) {
            int balance = plugin.getKeyStorage().get(online.getUniqueId(), keyId);
            online.sendMessage(plugin.getMessages().get("takekey-success-target",
                    "amount", String.valueOf(amount), "key_name", keyName,
                    "balance", String.valueOf(balance)));
        }
        return true;
    }

    private boolean deleteCase(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.getMessages().get("delcase-usage"));
            return true;
        }
        String caseName = normalize(args[1]);
        switch (caseManager.unbindCaseFromBlock(caseName)) {
            case REMOVED -> sender.sendMessage(plugin.getMessages().get("delcase-success", "case", caseName));
            case NOT_BOUND -> sender.sendMessage(plugin.getMessages().get("delcase-not-bound", "case", caseName));
            case NOT_FOUND -> sender.sendMessage(plugin.getMessages().get("delcase-not-found", "case", caseName));
        }
        return true;
    }

    private boolean createCase(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.getMessages().get("createcase-usage"));
            return true;
        }
        String caseName = normalize(args[1]);
        sendCreateResult(sender, caseName, caseManager.createCustomCase(caseName));
        return true;
    }

    private boolean openMachine(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessages().get("only-player"));
            return true;
        }
        CaseDefinition definition = args.length >= 2
                ? caseManager.getCaseByName(normalize(args[1]))
                : caseAtTarget(player, 6);
        if (definition == null) {
            player.sendMessage("§c[pnCases] Посмотри на блок кейса или используй §f/pncases machine <кейс>§c.");
            return true;
        }
        machineGui.openMain(player, definition.name());
        return true;
    }

    private boolean setCase(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessages().get("only-player"));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(plugin.getMessages().get("setcase-usage"));
            return true;
        }

        String caseName = normalize(args[1]);
        Block target = player.getTargetBlockExact(5);
        if (target == null) {
            player.sendMessage(plugin.getMessages().get("setcase-look-at-block"));
            return true;
        }
        if (!ensureCaseExists(player, caseName)) return true;

        caseManager.bindCaseToBlock(caseName, target);
        player.sendMessage(plugin.getMessages().get("setcase-success",
                "case", caseName, "world", target.getWorld().getName(),
                "x", String.valueOf(target.getX()), "y", String.valueOf(target.getY()),
                "z", String.valueOf(target.getZ())));
        return true;
    }

    private boolean ensureCaseExists(CommandSender sender, String caseName) {
        if (caseManager.caseExists(caseName)) return true;
        CaseManager.CreateCaseResult result = caseManager.createCustomCase(caseName);
        if (result == CaseManager.CreateCaseResult.ALREADY_EXISTS) return true;
        sendCreateResult(sender, caseName, result);
        return result == CaseManager.CreateCaseResult.CREATED;
    }

    private void sendCreateResult(CommandSender sender, String caseName, CaseManager.CreateCaseResult result) {
        switch (result) {
            case CREATED -> sender.sendMessage(plugin.getMessages().get("createcase-success", "case", caseName));
            case ALREADY_EXISTS -> sender.sendMessage(plugin.getMessages().get("createcase-exists", "case", caseName));
            case INVALID_ID -> sender.sendMessage(plugin.getMessages().get("createcase-invalid", "case", caseName));
            case SAVE_FAILED -> sender.sendMessage(plugin.getMessages().get("createcase-failed", "case", caseName));
        }
    }

    private void sendCommandMenu(CommandSender sender) {
        String current = plugin.getDescription().getVersion();
        MenuUpdateInfo update = menuUpdateInfo(current);
        plugin.getMessages().getList("command-menu",
                "version", current,
                "discord", plugin.getSupportDiscord(),
                "support", plugin.getSupportDiscord(),
                "update", update.status(),
                "latest", update.latest(),
                "download", update.download()
        ).forEach(sender::sendMessage);
    }

    private MenuUpdateInfo menuUpdateInfo(String current) {
        UpdateChecker checker = plugin.getUpdateChecker();
        if (checker == null || !checker.isCheckCompleted()) {
            return new MenuUpdateInfo("&7проверяется", "неизвестно", "");
        }
        if (checker.isUpdateAvailable()) {
            String latest = knownOrUnknown(checker.getLatestVersion());
            return new MenuUpdateInfo(
                    "&eдоступно &f" + current + " &8→ §x§D§8§D§F§9§D" + latest,
                    latest,
                    knownOrUnknown(checker.getDownloadUrl())
            );
        }
        String error = checker.getLastError();
        if (error != null && !error.isBlank()) {
            String stripped = ChatColor.stripColor(error);
            return new MenuUpdateInfo("&cошибка проверки: &f" + (stripped == null ? error : stripped),
                    knownOrUnknown(checker.getLatestVersion()), "");
        }
        return new MenuUpdateInfo("&aпоследняя версия", knownOrUnknown(checker.getLatestVersion()), "");
    }

    private CaseDefinition caseAtTarget(Player player, int distance) {
        Block target = player.getTargetBlockExact(distance);
        return target == null ? null : caseManager.getCaseByBlock(target);
    }

    private static ParsedAmount parseAmount(String[] args, int start, boolean allowSilent) {
        int amount = 0;
        boolean silent = false;
        for (int index = start; index < args.length; index++) {
            String value = args[index].trim();
            if (allowSilent && (value.equalsIgnoreCase("-s") || value.equalsIgnoreCase("-silent"))) {
                silent = true;
            } else if (amount == 0) {
                Integer parsed = parseInteger(value);
                if (parsed != null) amount = parsed;
            }
        }
        return new ParsedAmount(amount, silent);
    }

    private static Integer parseInteger(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static OfflinePlayer resolveOfflinePlayer(String input) {
        try {
            return Bukkit.getOfflinePlayer(UUID.fromString(input));
        } catch (IllegalArgumentException ignored) {
            return Bukkit.getOfflinePlayerIfCached(input);
        }
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private static String offlineName(OfflinePlayer player) {
        return player.getName() == null ? player.getUniqueId().toString() : player.getName();
    }

    private static String knownOrUnknown(String value) {
        return value == null || value.isBlank() ? "неизвестно" : value;
    }

    private record ParsedAmount(int amount, boolean silent) {
    }

    private record MenuUpdateInfo(String status, String latest, String download) {
    }
}
