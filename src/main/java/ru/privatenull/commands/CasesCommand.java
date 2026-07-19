package ru.privatenull.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import ru.privatenull.PnCasesPlugin;
import ru.privatenull.cases.CaseManager;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.gui.machine.MachineGuiListener;
import ru.privatenull.pnlibrary.update.UpdateChecker;
import ru.privatenull.pnlibrary.text.ColorUtil;

import java.util.Locale;
import java.util.UUID;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class CasesCommand implements CommandExecutor {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

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
        boolean casesReloaded = caseManager.reloadFromConfig();
        plugin.reloadRuntimeConfig();
        if (casesReloaded) {
            sender.sendMessage(plugin.getMessages().get("config-reloaded"));
        } else {
            sender.sendMessage(plugin.getMessages().get("cases-reload-failed"));
        }
        if (validation.hasProblems()) {
            sender.sendMessage(plugin.getMessages().get("config-validation-result",
                    "errors", String.valueOf(validation.errors()),
                    "warnings", String.valueOf(validation.warnings()),
                    "patches", String.valueOf(validation.patches())));
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
        switch (caseManager.deleteCase(caseName)) {
            case DELETED -> sender.sendMessage(plugin.getMessages().get("delcase-success", "case", caseName));
            case NOT_FOUND -> sender.sendMessage(plugin.getMessages().get("delcase-not-found", "case", caseName));
            case DELETE_FAILED -> sender.sendMessage(plugin.getMessages().get("delcase-save-failed", "case", caseName));
            case RELOAD_FAILED -> sender.sendMessage(plugin.getMessages().get("delcase-refresh-failed", "case", caseName));
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
            player.sendMessage(plugin.getMessages().get("machine-look-at-case"));
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
        if (!caseManager.caseExists(caseName)) {
            player.sendMessage(plugin.getMessages().get("setcase-not-found", "case", caseName));
            return true;
        }

        switch (caseManager.bindCaseToBlock(caseName, target)) {
            case BOUND -> {
                player.sendMessage(plugin.getMessages().get("setcase-success",
                        "case", caseName, "world", target.getWorld().getName(),
                        "x", String.valueOf(target.getX()), "y", String.valueOf(target.getY()),
                        "z", String.valueOf(target.getZ())));
                player.sendMessage(plugin.getMessages().get("setcase-machine-hint", "case", caseName));
            }
            case ALREADY_BOUND -> player.sendMessage(plugin.getMessages().get("setcase-already-bound", "case", caseName));
            case NOT_FOUND -> player.sendMessage(plugin.getMessages().get("setcase-not-found", "case", caseName));
            case BLOCK_OCCUPIED -> player.sendMessage(plugin.getMessages().get("setcase-block-occupied"));
            case SAVE_FAILED -> player.sendMessage(plugin.getMessages().get("setcase-save-failed", "case", caseName));
            case REFRESH_FAILED -> player.sendMessage(plugin.getMessages().get("setcase-refresh-failed", "case", caseName));
        }
        return true;
    }

    private void sendCreateResult(CommandSender sender, String caseName, CaseManager.CreateCaseResult result) {
        switch (result) {
            case CREATED -> sender.sendMessage(plugin.getMessages().get("createcase-success", "case", caseName));
            case ALREADY_EXISTS -> sender.sendMessage(plugin.getMessages().get("createcase-exists", "case", caseName));
            case INVALID_ID -> sender.sendMessage(plugin.getMessages().get("createcase-invalid", "case", caseName));
            case SAVE_FAILED -> sender.sendMessage(plugin.getMessages().get("createcase-failed", "case", caseName));
            case RELOAD_FAILED -> sender.sendMessage(plugin.getMessages().get("createcase-reload-failed", "case", caseName));
        }
    }

    private void sendCommandMenu(CommandSender sender) {
        String current = plugin.getDescription().getVersion();
        MenuUpdateInfo update = menuUpdateInfo(current);
        String prefix = plugin.getMessages().get("prefix");
        String information = "&fПанель администратора\n\n"
                + "&#429F91▸ &fВерсия: &#D8DF9D" + current + "\n"
                + "&#429F91▸ &fСерверы: &#D8DF9D1.16.5 - 1.21.11\n"
                + "&#429F91▸ &fОбновление: " + update.status() + "\n"
                + "&#429F91▸ &fПоддержка: &#D8DF9D" + plugin.getSupportDiscord();

        sender.sendMessage(menuComponent("&#429F91&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        sender.sendMessage(menuComponent(prefix + " &8| &fПанель администратора &8(наведите для информации)")
                .hoverEvent(HoverEvent.showText(menuComponent(information))));
        sender.sendMessage(menuComponent(""));
        sender.sendMessage(menuComponent("&#429F91Команды"));
        sender.sendMessage(menuComponent("&#429F91▸ &#D8DF9D/pncases &8— &7показать это меню"));
        sender.sendMessage(menuComponent("&#429F91▸ &#D8DF9D/pncases reload &8— &7перезагрузить настройки"));
        sender.sendMessage(menuComponent("&#429F91▸ &#D8DF9D/pncases machine <кейс> &8— &7настроить кейс через GUI"));
        sender.sendMessage(menuComponent("&#429F91▸ &#D8DF9D/pncases createcase <id> &8— &7создать новый кейс"));
        sender.sendMessage(menuComponent("&#429F91▸ &#D8DF9D/pncases setcase <кейс> &8— &7поставить кейс на блок"));
        sender.sendMessage(menuComponent("&#429F91▸ &#D8DF9D/pncases delcase <кейс> &8— &7удалить кейс"));
        sender.sendMessage(menuComponent("&#429F91▸ &#D8DF9D/pncases givekey <игрок> <ключ> <кол-во> &8— &7выдать ключи"));
        sender.sendMessage(menuComponent("&#429F91▸ &#D8DF9D/pncases takekey <игрок> <ключ> <кол-во> &8— &7забрать ключи"));
        sender.sendMessage(menuComponent("&#429F91&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
    }

    private static Component menuComponent(String text) {
        return LEGACY.deserialize(ColorUtil.colorize(text));
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
