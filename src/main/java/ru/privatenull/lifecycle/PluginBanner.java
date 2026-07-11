package ru.privatenull.lifecycle;

import org.bukkit.plugin.java.JavaPlugin;

public final class PluginBanner {

    private static final String[] LOGO = createLogo();

    private PluginBanner() {
    }

    private static String[] createLogo() {
        String[] lines = {
                " ________  ________  ___  ___      ___ ________  _________  _______   ________   ___  ___  ___       ___",
                "|@   __  @|@   __  @|@  @|@  @    /  /|@   __  @|@___   ___@@  ___ @ |@   ___  @|@  @|@  @|@  @     |@  @",
                "@ @  @|@  @ @  @|@  @ @  @ @  @  /  / | @  @|@  @|___ @  @_@ @   __/|@ @  @@ @  @ @  @@@  @ @  @    @ @  @",
                " @ @   ____@ @   _  _@ @  @ @  @/  / / @ @   __  @   @ @  @ @ @  @_|/_@ @  @@ @  @ @  @@@  @ @  @    @ @  @",
                "  @ @  @___|@ @  @@  @@ @  @ @    / /   @ @  @ @  @   @ @  @ @ @  @_|@ @ @  @@ @  @ @  @@@  @ @  @____@ @  @____",
                "   @ @__@    @ @__@@ _@@ @__@ @__/ /     @ @__@ @__@   @ @__@ @ @_______@ @__@@ @__@ @_______@ @_______@ @_______@",
                "    @|__|     @|__|@|__|@|__|@|__|/       @|__|@|__|    @|__|  @|_______|@|__| @|__|@|_______|@|_______|@|_______|"
        };
        for (int index = 0; index < lines.length; index++) {
            lines[index] = lines[index].replace('@', '\\');
        }
        return lines;
    }

    public static void enabled(JavaPlugin plugin, String supportUrl) {
        print(plugin, "ENABLED", supportUrl);
    }

    public static void disabled(JavaPlugin plugin, String supportUrl) {
        print(plugin, "DISABLED", supportUrl);
    }

    private static void print(JavaPlugin plugin, String state, String supportUrl) {
        plugin.getLogger().info(" ");
        for (String line : LOGO) plugin.getLogger().info(line);
        plugin.getLogger().info(" ");
        plugin.getLogger().info("pnCases v" + plugin.getDescription().getVersion() + " | " + state);
        if (supportUrl != null && !supportUrl.trim().isEmpty()) {
            plugin.getLogger().info("Support: " + supportUrl);
        }
        plugin.getLogger().info(" ");
    }
}
