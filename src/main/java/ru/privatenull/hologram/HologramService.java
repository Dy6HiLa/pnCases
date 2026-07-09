package ru.privatenull.hologram;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.util.ColorUtil;
import ru.privatenull.pnCases;
import ru.privatenull.util.ItemFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HologramService {

    private static final Pattern SERVER_VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");
    private static final int FANCY_MIN_MINOR_VERSION = 20;

    private final pnCases plugin;
    private final Set<String> externalNames = new HashSet<>();

    private HologramProvider provider;
    private boolean missingProviderWarningLogged;

    public HologramService(pnCases plugin) {
        this.plugin = plugin;
        reloadProvider();
    }

    public void shutdown() {
        clearManaged();
        provider = null;
    }

    public void clearManaged() {
        clearNativeDisplays();

        HologramProvider currentProvider = provider;
        for (String name : new ArrayList<>(externalNames)) {
            removeExternal(name, currentProvider);
        }

        externalNames.clear();
    }

    public void syncCases(Collection<CaseDefinition> defs) {
        reloadProvider();
        clearManaged();

        for (CaseDefinition def : defs) {
            try {
                if (blockLocations(def).isEmpty()) {
                    continue;
                }
                ConfigurationSection config = getHologramSection(def);
                if (config == null || !config.getBoolean("enabled", false)) {
                    continue;
                }
                for (Location location : blockLocations(def)) {
                    createCaseHologram(def, config, location);
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("Holograms: ошибка создания голограммы для кейса '" + def.name() + "': " + t.getMessage());
            }
        }
    }

    public void hideCase(CaseDefinition def) {
        reloadProvider();
        if (def == null) return;

        String prefix = hologramPrefix(def);
        for (String name : new ArrayList<>(externalNames)) {
            if (name.equals(legacyHologramName(def)) || name.startsWith(prefix + "_")) {
                removeExternal(name, provider);
                externalNames.remove(name);
            }
        }
        removeExternal(legacyHologramName(def), provider);
        clearNativeDisplays(def.name());
    }

    public void hideCase(CaseDefinition def, Location blockLocation) {
        reloadProvider();
        if (def == null || blockLocation == null) return;

        String name = hologramName(def, blockLocation);
        removeExternal(name, provider);
        externalNames.remove(name);
        clearNativeDisplays(def.name());
    }

    public void showCase(CaseDefinition def) {
        reloadProvider();
        if (def == null || blockLocations(def).isEmpty()) return;

        try {
            ConfigurationSection config = getHologramSection(def);
            if (config == null || !config.getBoolean("enabled", false)) {
                return;
            }
            for (Location location : blockLocations(def)) {
                createCaseHologram(def, config, location);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Holograms: ошибка showCase для '" + def.name() + "': " + t.getMessage());
        }
    }

    public void showCase(CaseDefinition def, Location blockLocation) {
        reloadProvider();
        if (def == null || blockLocation == null) return;

        try {
            ConfigurationSection config = getHologramSection(def);
            if (config == null || !config.getBoolean("enabled", false)) {
                return;
            }
            createCaseHologram(def, config, blockLocation);
        } catch (Throwable t) {
            plugin.getLogger().warning("Holograms: showCase error for '" + def.name() + "': " + t.getMessage());
        }
    }

    private void createCaseHologram(CaseDefinition def, ConfigurationSection config, Location blockLocation) {
        HologramSpec spec = buildSpec(def, config, blockLocation);
        removeExternal(spec.name(), provider);
        externalNames.remove(spec.name());

        HologramProvider currentProvider = provider;
        if (currentProvider == null) {
            logMissingProviderOnce();
            return;
        }

        try {
            if (!currentProvider.isAvailable()) {
                throw new IllegalStateException("провайдер выключен");
            }
            currentProvider.create(spec);
            externalNames.add(spec.name());
        } catch (Throwable t) {
            provider = null;
            logMissingProviderOnce();
        }
    }

    private HologramSpec buildSpec(CaseDefinition def, ConfigurationSection config, Location blockLocation) {
        HologramType type = readType(config);
        Location loc = readLocation(blockLocation, config);
        String name = hologramName(def, blockLocation);

        List<String> lines = readLines(def, config, blockLocation);
        ItemStack item = readItem(config);
        Material block = readBlock(config);

        return new HologramSpec(
                name,
                def.name(),
                type,
                loc,
                lines,
                item,
                block,
                readBillboard(config),
                readTextAlignment(config),
                config.contains("text_shadow") ? config.getBoolean("text_shadow") : null,
                config.contains("see_through") ? config.getBoolean("see_through") : null,
                config.contains("background") ? parseColor(config.getString("background", "")) : null,
                config.contains("text_update_interval") ? Math.max(0, config.getInt("text_update_interval", 0)) : null,
                config.contains("shadow_radius") ? (float) config.getDouble("shadow_radius") : null,
                config.contains("shadow_strength") ? (float) config.getDouble("shadow_strength") : null,
                config.contains("visibility_distance") ? Math.max(1, config.getInt("visibility_distance")) : null,
                config.contains("interpolation_duration") ? Math.max(0, config.getInt("interpolation_duration")) : null,
                parseVector(config, "scale"),
                parseVector(config, "translation"),
                readBrightness(config)
        );
    }

    private void removeExternal(String name, HologramProvider currentProvider) {
        if (name == null || name.isBlank()) {
            return;
        }

        if (currentProvider != null) {
            try {
                currentProvider.remove(name);
            } catch (Throwable ignored) {
            }
        }

        if (Bukkit.getPluginManager().isPluginEnabled("FancyHolograms")) {
            HologramProvider fancy = createFancyProvider();
            if (fancy != null && fancy != currentProvider) {
                try {
                    fancy.remove(name);
                } catch (Throwable ignored) {
                }
            }
        }

        if (Bukkit.getPluginManager().isPluginEnabled("DecentHolograms")) {
            HologramProvider decent = createDecentProvider();
            if (decent != null && decent != currentProvider) {
                try {
                    decent.remove(name);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private void clearNativeDisplays() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains("pncases_hologram")) {
                    entity.remove();
                }
            }
        }
    }

    private void clearNativeDisplays(String caseName) {
        if (caseName == null || caseName.isBlank()) {
            return;
        }

        String caseTag = "pncases_" + caseName;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                Set<String> tags = entity.getScoreboardTags();
                if (tags.contains("pncases_hologram") && tags.contains(caseTag)) {
                    entity.remove();
                }
            }
        }
    }

    private void reloadProvider() {
        if (provider != null) {
            try {
                if (provider.isAvailable()) {
                    return;
                }
            } catch (Throwable ignored) {
                provider = null;
            }
        }

        provider = selectProvider();
        if (provider != null) {
            missingProviderWarningLogged = false;
        }
    }

    private void logMissingProviderOnce() {
        if (missingProviderWarningLogged) {
            return;
        }
        missingProviderWarningLogged = true;
        plugin.getLogger().warning("Голограммы: FancyHolograms или DecentHolograms не найдены. Голограммы кейсов отключены.");
    }

    private HologramProvider selectProvider() {
        ProviderMode mode = readProviderMode();

        if (mode == ProviderMode.TEXT) {
            return null;
        }

        boolean fancyAvailable = Bukkit.getPluginManager().isPluginEnabled("FancyHolograms");
        boolean decentAvailable = Bukkit.getPluginManager().isPluginEnabled("DecentHolograms");

        if (mode == ProviderMode.FANCY) {
            return fancyAvailable ? createFancyProvider() : null;
        }
        if (mode == ProviderMode.DECENT) {
            return decentAvailable ? createDecentProvider() : null;
        }

        if (preferDecentForServerVersion()) {
            HologramProvider decent = decentAvailable ? createDecentProvider() : null;
            if (decent != null) return decent;

            return fancyAvailable ? createFancyProvider() : null;
        }

        HologramProvider fancy = fancyAvailable ? createFancyProvider() : null;
        if (fancy != null) return fancy;

        return decentAvailable ? createDecentProvider() : null;
    }

    private HologramProvider createFancyProvider() {
        try {
            return new FancyHologramsProvider();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private HologramProvider createDecentProvider() {
        try {
            return new DecentHologramsProvider();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private ConfigurationSection getHologramSection(CaseDefinition def) {
        ConfigurationSection config = plugin.getCaseManager() == null
                ? null
                : plugin.getCaseManager().getCaseSection(def.name());
        if (config == null) {
            config = plugin.getConfig().getConfigurationSection("cases." + def.name());
        }
        return config == null ? null : config.getConfigurationSection("hologram");
    }

    private static HologramType readType(ConfigurationSection config) {
        String raw = config.getString("type", "TEXT");
        try {
            return HologramType.valueOf(raw == null ? "TEXT" : raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return HologramType.TEXT;
        }
    }

    private static Location readLocation(Location blockLocation, ConfigurationSection config) {
        Location loc = blockLocation.clone().add(0.5, 0.0, 0.5);

        double ox = config.getDouble("x", 0.0);
        double oy = config.contains("height") ? config.getDouble("height") : config.getDouble("y", 1.8);
        double oz = config.getDouble("z", 0.0);

        ConfigurationSection offset = config.getConfigurationSection("offset");
        if (offset != null) {
            ox = offset.getDouble("x", ox);
            oy = offset.contains("height") ? offset.getDouble("height") : offset.getDouble("y", oy);
            oz = offset.getDouble("z", oz);
        }

        return loc.add(ox, oy, oz);
    }

    private static List<String> readLines(CaseDefinition def, ConfigurationSection config, Location blockLocation) {
        List<String> lines = config.getStringList("lines");
        if (lines == null || lines.isEmpty()) {
            String one = config.getString("line", null);
            if (one != null && !one.isBlank()) {
                lines = List.of(one);
            }
        }
        if (lines == null || lines.isEmpty()) {
            lines = List.of("&e" + def.name());
        }

        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(applyPlaceholders(line, def, blockLocation));
        }
        return out;
    }

    private static ItemStack readItem(ConfigurationSection config) {
        ItemStack item = ItemFactory.fromSection(config.getConfigurationSection("item"));
        if (item != null) {
            return item;
        }

        Material material = readMaterial(config.getString("material", "CHEST"), Material.CHEST);
        return new ItemStack(material);
    }

    private static Material readBlock(ConfigurationSection config) {
        return readMaterial(config.getString("block", config.getString("block_material", "CHEST")), Material.CHEST);
    }

    private static String readBillboard(ConfigurationSection config) {
        String raw = config.getString("billboard", "CENTER");
        try {
            String normalized = raw == null ? "CENTER" : raw.toUpperCase(Locale.ROOT);
            return switch (normalized) {
                case "FIXED", "VERTICAL", "HORIZONTAL", "CENTER" -> normalized;
                default -> "CENTER";
            };
        } catch (IllegalArgumentException ignored) {
            return "CENTER";
        }
    }

    private static String readTextAlignment(ConfigurationSection config) {
        String raw = config.getString("text_alignment", "CENTER");
        try {
            String normalized = raw == null ? "CENTER" : raw.toUpperCase(Locale.ROOT);
            return switch (normalized) {
                case "LEFT", "RIGHT", "CENTER" -> normalized;
                default -> "CENTER";
            };
        } catch (IllegalArgumentException ignored) {
            return "CENTER";
        }
    }

    private static HologramSpec.HologramBrightness readBrightness(ConfigurationSection config) {
        ConfigurationSection brightness = config.getConfigurationSection("brightness");
        if (brightness == null || (!brightness.contains("block") && !brightness.contains("sky"))) {
            return null;
        }

        int block = clamp(brightness.getInt("block", 15), 0, 15);
        int sky = clamp(brightness.getInt("sky", 15), 0, 15);
        return new HologramSpec.HologramBrightness(block, sky);
    }

    private static Material readMaterial(String raw, Material fallback) {
        Material material = raw == null ? null : Material.matchMaterial(raw);
        return material == null ? fallback : material;
    }

    private static String applyPlaceholders(String value, CaseDefinition def, Location blockLocation) {
        if (value == null) return "";

        String guiRaw = def.guiTitle() == null ? "" : def.guiTitle();
        String guiPlain = ChatColor.stripColor(ColorUtil.colorize(guiRaw));
        World world = blockLocation.getWorld();

        return value
                .replace("{case}", def.name())
                .replace("{gui_title}", guiRaw)
                .replace("{gui_title_plain}", guiPlain == null ? "" : guiPlain)
                .replace("{world}", world == null ? "world" : world.getName())
                .replace("{x}", String.valueOf(blockLocation.getBlockX()))
                .replace("{y}", String.valueOf(blockLocation.getBlockY()))
                .replace("{z}", String.valueOf(blockLocation.getBlockZ()));
    }

    private static List<Location> blockLocations(CaseDefinition def) {
        if (def == null) {
            return List.of();
        }
        if (!def.blockLocations().isEmpty()) {
            return def.blockLocations();
        }
        return def.blockLocation() == null ? List.of() : List.of(def.blockLocation());
    }

    private static String hologramName(CaseDefinition def, Location location) {
        World world = location.getWorld();
        String worldName = world == null ? "world" : world.getName();
        return hologramPrefix(def)
                + "_" + sanitizeName(worldName)
                + "_" + location.getBlockX()
                + "_" + location.getBlockY()
                + "_" + location.getBlockZ();
    }

    private static String hologramPrefix(CaseDefinition def) {
        return "pncases_" + sanitizeName(def.name());
    }

    private static String legacyHologramName(CaseDefinition def) {
        return "pncases_" + def.name();
    }

    private static String sanitizeName(String value) {
        return (value == null ? "case" : value).replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private static Color parseColor(String value) {
        if (value == null) return null;

        String raw = value.trim();
        if (raw.isEmpty() || raw.equalsIgnoreCase("none") || raw.equalsIgnoreCase("null")) {
            return null;
        }

        try {
            if (raw.startsWith("#")) raw = raw.substring(1);
            if (raw.matches("^[0-9a-fA-F]{6}$")) {
                return Color.fromRGB(Integer.parseInt(raw, 16));
            }
        } catch (Exception ignored) {
        }

        try {
            String[] parts = raw.split("[,; ]+");
            if (parts.length >= 3) {
                return Color.fromRGB(
                        clamp(Integer.parseInt(parts[0]), 0, 255),
                        clamp(Integer.parseInt(parts[1]), 0, 255),
                        clamp(Integer.parseInt(parts[2]), 0, 255)
                );
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private static HologramSpec.HologramVector parseVector(ConfigurationSection root, String key) {
        if (root == null || key == null) return null;

        if (root.isConfigurationSection(key)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) return null;
            if (!section.contains("x") && !section.contains("y") && !section.contains("z")) return null;

            return new HologramSpec.HologramVector(
                    (float) section.getDouble("x", 0.0),
                    (float) section.getDouble("y", 0.0),
                    (float) section.getDouble("z", 0.0)
            );
        }

        if (root.isList(key)) {
            List<?> raw = root.getList(key);
            if (raw == null || raw.size() < 3) return null;

            try {
                return new HologramSpec.HologramVector(
                        (float) Double.parseDouble(String.valueOf(raw.get(0))),
                        (float) Double.parseDouble(String.valueOf(raw.get(1))),
                        (float) Double.parseDouble(String.valueOf(raw.get(2)))
                );
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private ProviderMode readProviderMode() {
        String raw = plugin.getConfig().getString("holograms.provider",
                plugin.getConfig().getString("hologram.provider", "AUTO"));
        try {
            return ProviderMode.valueOf(raw == null ? "AUTO" : raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return ProviderMode.AUTO;
        }
    }

    private static boolean preferDecentForServerVersion() {
        ServerVersion version = readServerVersion();
        return version.major() == 1 && version.minor() <= FANCY_MIN_MINOR_VERSION - 1;
    }

    private static ServerVersion readServerVersion() {
        Matcher matcher = SERVER_VERSION_PATTERN.matcher(Bukkit.getBukkitVersion());
        if (!matcher.find()) {
            return new ServerVersion(1, 21, 0);
        }

        int major = parseInt(matcher.group(1), 1);
        int minor = parseInt(matcher.group(2), 21);
        int patch = parseInt(matcher.group(3), 0);
        return new ServerVersion(major, minor, patch);
    }

    private static int parseInt(String raw, int fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private enum ProviderMode {
        AUTO,
        FANCY,
        DECENT,
        TEXT
    }

    private record ServerVersion(int major, int minor, int patch) {
    }
}
