package ru.privatenull.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import ru.privatenull.cases.CaseManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CasesCommandCompleter implements TabCompleter {

    private final CaseManager caseManager;

    public CasesCommandCompleter(CaseManager caseManager) {
        this.caseManager = caseManager;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> candidates = new ArrayList<>();
        if (!sender.hasPermission("pncases.admin")) return candidates;
        if (args.length == 1) {
            return filter(List.of("createcase", "setcase", "delcase", "machine", "givekey", "takekey", "reload"), args[0]);
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if ((subcommand.equals("delcase") || subcommand.equals("machine")) && args.length == 2) {
            return filter(caseManager.getConfiguredCaseNames(), args[1]);
        }
        if (subcommand.equals("givekey") || subcommand.equals("takekey")) {
            if (args.length == 2) {
                Bukkit.getOnlinePlayers().forEach(player -> candidates.add(player.getName()));
                return filter(candidates, args[1]);
            }
            if (args.length == 3) return filter(caseManager.getKeyNames(), args[2]);
            if (args.length == 4) return filter(List.of("1", "2", "3", "5", "10", "16", "32", "64"), args[3]);
            if (args.length == 5 && subcommand.equals("givekey")) return filter(List.of("-s"), args[4]);
        }
        return candidates;
    }

    private List<String> filter(List<String> values, String prefix) {
        String normalized = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized))
                .toList();
    }
}
