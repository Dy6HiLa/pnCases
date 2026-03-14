package ru.privatenull.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import ru.privatenull.cases.CaseManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CasesCMDTabCompliter implements TabCompleter {

    private final CaseManager caseManager;

    public CasesCMDTabCompliter(CaseManager caseManager) {
        this.caseManager = caseManager;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();

        if (!sender.hasPermission("pncases.admin")) return out;

        if (args.length == 1) {
            return filter(List.of("setcase", "givekey", "reload"), args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("setcase")) {
            if (args.length == 2) {
                return filter(caseManager.getCaseNames(), args[1]);
            }
        }

        if (sub.equals("givekey")) {
            if (args.length == 2) {
                Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
                return filter(out, args[1]);
            }
            if (args.length == 3) {
                return filter(caseManager.getKeyNames(), args[2]);
            }
            if (args.length == 4) {
                return filter(List.of("1", "2", "3", "5", "10", "16", "32", "64"), args[3]);
            }
        }

        return out;
    }

    private List<String> filter(List<String> values, String prefix) {
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> res = new ArrayList<>();
        for (String v : values) {
            if (v.toLowerCase(Locale.ROOT).startsWith(p)) res.add(v);
        }
        return res;
    }
}