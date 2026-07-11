package ru.privatenull.cases.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.privatenull.PnCasesPlugin;
import ru.privatenull.cases.model.AnimationType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

public final class CaseConfigRepository {

    public enum CreateResult {
        CREATED,
        ALREADY_EXISTS,
        INVALID_ID,
        SAVE_FAILED
    }

    private static final List<String> DEFAULT_RESOURCES = List.of(
            "cases/money.yml",
            "cases/playerpoints.yml",
            "cases/items.yml",
            "cases/luckperms.yml"
    );

    private final PnCasesPlugin plugin;

    public CaseConfigRepository(PnCasesPlugin plugin) {
        this.plugin = plugin;
    }

    public List<String> configuredNames(Collection<String> fallback) {
        Set<String> names = new TreeSet<>();
        ConfigurationSection cases = plugin.getConfig().getConfigurationSection("cases");
        if (cases != null) names.addAll(cases.getKeys(false));

        File[] files = directory().listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                names.add(name.substring(0, name.length() - 4));
            }
        }
        if (names.isEmpty() && fallback != null) names.addAll(fallback);
        return new ArrayList<>(names);
    }

    public boolean exists(String caseName) {
        return isValidId(caseName) && writable(caseName, false) != null;
    }

    public boolean update(String caseName, Consumer<ConfigurationSection> updater) {
        if (caseName == null || caseName.isBlank() || updater == null) return false;
        Writable writable = writable(caseName, false);
        if (writable == null || writable.section() == null) return false;
        updater.accept(writable.section());
        return save(writable);
    }

    public CreateResult create(String caseName) {
        if (!isValidId(caseName)) return CreateResult.INVALID_ID;
        String normalized = normalizeName(caseName);
        if (writable(normalized, false) != null) return CreateResult.ALREADY_EXISTS;

        File directory = directory();
        if (!directory.exists() && !directory.mkdirs()) {
            plugin.getLogger().severe("Не удалось создать папку кейсов: " + directory.getPath());
            return CreateResult.SAVE_FAILED;
        }

        File target = file(normalized);
        YamlConfiguration yaml = new YamlConfiguration();
        writeNewCaseDefaults(yaml, normalized);
        try {
            yaml.save(target);
            return CreateResult.CREATED;
        } catch (IOException ex) {
            plugin.getLogger().severe("Не удалось создать файл кейса " + target.getName() + ": " + ex.getMessage());
            return CreateResult.SAVE_FAILED;
        }
    }

    public List<Source> loadSources() {
        Map<String, Source> sources = new LinkedHashMap<>();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("cases");
        if (root != null) {
            for (String caseName : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(caseName);
                if (section == null) continue;
                String normalized = normalizeName(caseName);
                sources.put(normalized, new Source(normalized, section, false));
            }
        }

        File[] files = directory().listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            for (File file : files) {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                String fallbackName = file.getName().substring(0, file.getName().length() - 4);
                ConfigurationSection section = caseSection(yaml);
                String explicitId = firstString(section, "id", "case_id", "case-id");
                String caseName = normalizeName(explicitId == null || explicitId.isBlank() ? fallbackName : explicitId);
                sources.put(caseName, new Source(caseName, section, true));
            }
        }
        return new ArrayList<>(sources.values());
    }

    public Writable writable(String caseName, boolean create) {
        String normalized = normalizeName(caseName);
        File caseFile = file(normalized);
        if (caseFile.isFile()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(caseFile);
            return new Writable(caseSection(yaml), yaml, caseFile, true);
        }

        ConfigurationSection cases = plugin.getConfig().getConfigurationSection("cases");
        if (cases == null) {
            if (!create) return null;
            cases = plugin.getConfig().createSection("cases");
        }
        ConfigurationSection section = cases.getConfigurationSection(normalized);
        if (section == null && create) section = cases.createSection(normalized);
        return section == null ? null : new Writable(section, null, null, false);
    }

    public boolean save(Writable writable) {
        if (writable == null) return false;
        if (writable.fileBacked()) {
            try {
                writable.yaml().save(writable.file());
                return true;
            } catch (IOException ex) {
                plugin.getLogger().severe("Не удалось сохранить файл кейса "
                        + writable.file().getName() + ": " + ex.getMessage());
                return false;
            }
        }
        plugin.saveConfig();
        return true;
    }

    public void exportMainCasesIfMissing() {
        if (!plugin.getConfig().getBoolean("case-files.auto-export", true)) return;
        File directory = directory();
        File[] existing = directory.listFiles((file, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (existing != null && existing.length > 0) {
            removeMainCasesWhenExported();
            return;
        }

        ConfigurationSection root = plugin.getConfig().getConfigurationSection("cases");
        if (!ensureDirectory(directory)) return;
        if (root == null || root.getKeys(false).isEmpty()) {
            saveBundledCases();
            return;
        }

        int exported = 0;
        for (String caseName : root.getKeys(false)) {
            ConfigurationSection source = root.getConfigurationSection(caseName);
            if (source == null) continue;
            YamlConfiguration yaml = new YamlConfiguration();
            copySection(source, yaml);
            try {
                yaml.save(file(caseName));
                exported++;
            } catch (IOException ex) {
                plugin.getLogger().warning("Не удалось создать отдельный конфиг кейса "
                        + caseName + ": " + ex.getMessage());
            }
        }
        if (exported > 0) {
            removeMainCasesWhenExported();
            plugin.getLogger().info("Созданы отдельные конфиги кейсов: plugins/pnCases/cases/*.yml ("
                    + exported + ").");
        }
    }

    public static String normalizeName(String caseName) {
        return (caseName == null ? "" : caseName.trim()).toLowerCase(Locale.ROOT);
    }

    public static boolean isValidId(String caseName) {
        return caseName != null && caseName.matches("[a-z0-9_-]{1,64}");
    }

    private boolean ensureDirectory(File directory) {
        if (directory.exists() || directory.mkdirs()) return true;
        plugin.getLogger().warning("Не удалось создать папку cases для отдельных конфигов кейсов.");
        return false;
    }

    private void removeMainCasesWhenExported() {
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("cases");
        if (root == null || root.getKeys(false).isEmpty()) return;

        Set<String> fileCases = new HashSet<>();
        File[] files = directory().listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null || files.length == 0) return;
        for (File file : files) {
            String name = file.getName();
            fileCases.add(normalizeName(name.substring(0, name.length() - 4)));
        }
        for (String caseName : root.getKeys(false)) {
            if (!fileCases.contains(normalizeName(caseName))) return;
        }

        File backup = new File(plugin.getDataFolder(), "config.cases-backup.yml");
        if (!backup.exists()) {
            try {
                plugin.getConfig().save(backup);
            } catch (IOException ex) {
                plugin.getLogger().warning("Не удалось сохранить backup перед переносом кейсов: " + ex.getMessage());
                return;
            }
        }
        plugin.getConfig().set("cases", null);
        plugin.saveConfig();
        plugin.getLogger().info("Секция cases перенесена в plugins/pnCases/cases/*.yml. "
                + "Старый config сохранён как config.cases-backup.yml.");
    }

    private void saveBundledCases() {
        int saved = 0;
        for (String resource : DEFAULT_RESOURCES) {
            if (plugin.getResource(resource) == null) continue;
            File target = new File(plugin.getDataFolder(), resource);
            if (target.exists()) continue;
            try {
                plugin.saveResource(resource, false);
                saved++;
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Не удалось распаковать пример кейса " + resource + ": " + ex.getMessage());
            }
        }
        if (saved > 0) {
            plugin.getLogger().info("Созданы отдельные конфиги кейсов: plugins/pnCases/cases/*.yml (" + saved + ").");
        }
    }

    private File directory() {
        return new File(plugin.getDataFolder(), "cases");
    }

    private File file(String caseName) {
        return new File(directory(), normalizeName(caseName) + ".yml");
    }

    private static ConfigurationSection caseSection(YamlConfiguration yaml) {
        ConfigurationSection nested = yaml.getConfigurationSection("case");
        return nested == null ? yaml : nested;
    }

    private static String firstString(ConfigurationSection section, String... keys) {
        if (section == null) return null;
        for (String key : keys) {
            if (section.contains(key)) return section.getString(key);
        }
        return null;
    }

    private static void copySection(ConfigurationSection source, ConfigurationSection target) {
        for (String key : source.getKeys(false)) {
            Object value = source.get(key);
            if (value instanceof ConfigurationSection nested) {
                copySection(nested, target.createSection(key));
            } else {
                target.set(key, value);
            }
        }
    }

    private static void writeNewCaseDefaults(ConfigurationSection root, String caseName) {
        String visibleName = "&x&4&2&9&F&9&1Новый кейс &8| &f" + caseName;
        root.set("id", caseName);
        root.set("display-name", visibleName);

        ConfigurationSection hologram = root.createSection("hologram");
        hologram.set("enabled", true);
        hologram.set("type", "TEXT");
        hologram.set("y", 1.5D);
        hologram.set("lines", List.of(visibleName, "&7Нажмите ПКМ, чтобы открыть"));

        ConfigurationSection showcase = root.createSection("idle-particles");
        showcase.set("enabled", true);
        showcase.set("effects", true);
        showcase.set("style", "VERTICAL_SPIRAL");
        showcase.set("theme", "ELECTRIC");
        showcase.set("interval_ticks", 3);
        showcase.set("radius", 0.8D);
        showcase.set("height", 1.55D);
        showcase.set("speed", 0.12D);
        showcase.set("view_distance", 28);
        ConfigurationSection showcaseItem = showcase.createSection("item");
        showcaseItem.set("material", "NETHER_STAR");
        showcaseItem.set("name", visibleName);

        ConfigurationSection gui = root.createSection("gui");
        gui.set("title", "&8" + caseName);
        gui.set("size", 54);
        gui.set("open_slot", 22);
        gui.set("animation_slot", 49);
        ConfigurationSection decor = gui.createSection("decor");
        decor.set("slots", List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35,
                36, 37, 38, 39, 40, 41, 42, 43, 44));
        ConfigurationSection decorItem = decor.createSection("item");
        decorItem.set("material", "GRAY_STAINED_GLASS_PANE");
        decorItem.set("name", " ");
        ConfigurationSection history = gui.createSection("history");
        history.set("slots", List.of(45, 46, 47, 48, 51, 52, 53));
        ConfigurationSection emptyHistory = history.createSection("empty-item");
        emptyHistory.set("material", "BARRIER");
        emptyHistory.set("name", "&8История пуста");
        ConfigurationSection animationItem = gui.createSection("animation-item");
        animationItem.set("material", "NETHER_STAR");
        animationItem.set("name", "&eКруг фортуны");
        animationItem.set("lore", List.of("&7Настройка открытия в Machine GUI."));
        ConfigurationSection openItem = gui.createSection("open-item");
        openItem.set("material", "CHEST");
        openItem.set("name", visibleName);
        openItem.set("lore", List.of("", "&7Новый кейс готов к настройке.", ""));

        ConfigurationSection cost = root.createSection("cost");
        cost.set("type", "NONE");
        cost.set("amount", 0);
        cost.set("buy_xp_enabled", false);
        cost.set("buy_xp_levels", 0);

        ConfigurationSection animation = root.createSection("animation");
        animation.set("fixed", AnimationType.FORTUNE_RING.name());
        animation.set("duration_ticks", 80);
        animation.set("cycle_every_ticks", 3);
        animation.set("rise_blocks", 1.2D);
        animation.set("spin_degrees_per_tick", 18);
        animation.set("items", List.of(
                Map.of("material", "GOLD_INGOT", "name", "&6Золото"),
                Map.of("material", "DIAMOND", "name", "&bАлмаз"),
                Map.of("material", "EMERALD", "name", "&aИзумруд")
        ));
        root.set("rewards", List.of(Map.of(
                "chance", 100,
                "rarity", "COMMON",
                "type", "ITEM",
                "item", Map.of("material", "DIAMOND", "amount", 1, "name", "&bАлмаз"),
                "message", "&aВы получили &f{reward}&a!"
        )));
    }

    public record Source(String name, ConfigurationSection section, boolean fileBacked) {
    }

    public record Writable(ConfigurationSection section, YamlConfiguration yaml, File file, boolean fileBacked) {
    }
}
