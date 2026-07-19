package ru.privatenull.cases.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.privatenull.PnCasesPlugin;
import ru.privatenull.cases.model.AnimationType;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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

    private final PnCasesPlugin plugin;

    public CaseConfigRepository(PnCasesPlugin plugin) {
        this.plugin = plugin;
    }

    public List<String> configuredNames(Collection<String> fallback) {
        Set<String> names = new TreeSet<>();
        ConfigurationSection cases = plugin.getConfig().getConfigurationSection("cases");
        if (cases != null) {
            for (String configured : cases.getKeys(false)) {
                String normalized = normalizeName(configured);
                if (isValidId(normalized)) names.add(normalized);
            }
        }

        File[] files = directory().listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String name = fileId(file.getName());
                if (isValidId(name)) names.add(name);
            }
        }
        if (names.isEmpty() && fallback != null) {
            for (String configured : fallback) {
                String normalized = normalizeName(configured);
                if (isValidId(normalized)) names.add(normalized);
            }
        }
        return new ArrayList<>(names);
    }

    public boolean exists(String caseName) {
        String normalized = normalizeName(caseName);
        return isValidId(normalized) && declaredSourceExists(
                embeddedCaseDeclared(normalized), matchingCaseFileCount(normalized));
    }

    public boolean update(String caseName, Consumer<ConfigurationSection> updater) {
        if (!isValidId(normalizeName(caseName)) || updater == null) return false;
        Writable writable = writable(caseName, false);
        if (writable == null || writable.section() == null) return false;
        Map<String, Object> before = snapshotSection(writable.section());
        try {
            updater.accept(writable.section());
        } catch (RuntimeException ex) {
            restoreSection(writable.section(), before);
            plugin.getLogger().severe("Не удалось изменить конфиг кейса '" + normalizeName(caseName) + "': " + ex.getMessage());
            return false;
        }
        if (save(writable)) return true;
        restoreSection(writable.section(), before);
        return false;
    }

    public CreateResult create(String caseName) {
        if (!isValidId(caseName)) return CreateResult.INVALID_ID;
        String normalized = normalizeName(caseName);
        if (exists(normalized)) return CreateResult.ALREADY_EXISTS;

        File directory = directory();
        if (!directory.exists() && !directory.mkdirs()) {
            plugin.getLogger().severe("Не удалось создать папку кейсов: " + directory.getPath());
            return CreateResult.SAVE_FAILED;
        }

        File target = file(normalized);
        YamlConfiguration yaml = new YamlConfiguration();
        writeNewCaseDefaults(yaml, normalized);
        return saveYamlAtomically(yaml, target) ? CreateResult.CREATED : CreateResult.SAVE_FAILED;
    }

    public List<Source> loadSources() {
        return loadAll().sources();
    }

    public LoadResult loadAll() {
        Map<String, Source> sources = new LinkedHashMap<>();
        boolean successful = true;
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("cases");
        Set<String> authoritativeEmbedded = new HashSet<>();
        if (root != null && plugin.getConfig().getBoolean("case-files.auto-export", true)) {
            for (String caseName : root.getKeys(false)) {
                authoritativeEmbedded.add(normalizeName(caseName));
            }
        }

        File casesDirectory = directory();
        File[] files = casesDirectory.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            boolean hasEmbeddedCases = root != null && !root.getKeys(false).isEmpty();
            if (missingCaseDirectoryIsEmpty(casesDirectory.exists(), hasEmbeddedCases)) {
                files = new File[0];
            } else {
                plugin.getLogger().severe("Не удалось прочитать папку cases: путь недоступен, не является папкой "
                        + "или отсутствует без embedded-кейсов. Reload отменён.");
                successful = false;
            }
        }
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            for (File file : files) {
                String canonicalId = fileId(file.getName());
                if (!isValidId(canonicalId)) {
                    plugin.getLogger().severe("Пропущен конфиг кейса с недопустимым именем файла: " + file.getName());
                    successful = false;
                    continue;
                }
                if (authoritativeEmbedded.contains(canonicalId)) {
                    plugin.getLogger().warning("Миграция кейса '" + canonicalId
                            + "' не завершена; standalone-файл временно игнорируется в пользу config.yml.");
                    continue;
                }
                YamlConfiguration yaml = loadYaml(file);
                if (yaml == null) {
                    successful = false;
                    continue;
                }
                ConfigurationSection section = caseSection(yaml);
                warnIdMismatch(file.getName(), canonicalId, section);
                if (sources.putIfAbsent(canonicalId, new Source(canonicalId, section, true)) != null) {
                    plugin.getLogger().severe("Дубликат id кейса '" + canonicalId + "' в папке cases. Файл "
                            + file.getName() + " пропущен.");
                    successful = false;
                }
            }
        }

        if (root != null) {
            for (String caseName : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(caseName);
                String normalized = normalizeName(caseName);
                if (!isValidId(normalized) || section == null) {
                    plugin.getLogger().severe("Пропущен некорректный кейс config.yml: cases." + caseName);
                    successful = false;
                    continue;
                }
                warnIdMismatch("config.yml:cases." + caseName, normalized, section);
                Source embedded = new Source(normalized, section, false);
                Source previous = sources.putIfAbsent(normalized, embedded);
                if (previous != null) {
                    if (replaceStandaloneWithEmbedded(
                            plugin.getConfig().getBoolean("case-files.auto-export", true),
                            previous.fileBacked())) {
                        sources.put(normalized, embedded);
                        plugin.getLogger().warning("Миграция кейса '" + normalized
                                + "' не завершена; до следующего успешного экспорта используется версия из config.yml.");
                    } else {
                        plugin.getLogger().severe(previous.fileBacked()
                                ? "Дубликат кейса '" + normalized + "': он одновременно объявлен в config.yml и cases/"
                                + normalized + ".yml."
                                : "Дубликат embedded-id кейса '" + normalized
                                + "' в config.yml. Проверьте регистр и повторяющиеся секции.");
                        successful = false;
                    }
                }
            }
        }
        return new LoadResult(new ArrayList<>(sources.values()), successful);
    }

    public Source findSource(String caseName) {
        String normalized = normalizeName(caseName);
        if (!isValidId(normalized)) return null;

        ConfigurationSection embedded = embeddedCaseSection(normalized);
        File caseFile = existingCaseFile(normalized);
        if (preferEmbeddedSource(plugin.getConfig().getBoolean("case-files.auto-export", true),
                embedded != null, caseFile != null)) {
            warnIdMismatch("config.yml:cases." + normalized, normalized, embedded);
            return new Source(normalized, embedded, false);
        }

        if (caseFile != null) {
            YamlConfiguration yaml = loadYaml(caseFile);
            if (yaml == null) return null;
            ConfigurationSection section = caseSection(yaml);
            warnIdMismatch(caseFile.getName(), normalized, section);
            return new Source(normalized, section, true);
        }

        if (embedded == null) return null;
        warnIdMismatch("config.yml:cases." + normalized, normalized, embedded);
        return new Source(normalized, embedded, false);
    }

    public Writable writable(String caseName, boolean create) {
        String normalized = normalizeName(caseName);
        if (!isValidId(normalized)) return null;

        ConfigurationSection embedded = embeddedCaseSection(normalized);
        File caseFile = existingCaseFile(normalized);
        if (preferEmbeddedSource(plugin.getConfig().getBoolean("case-files.auto-export", true),
                embedded != null, caseFile != null)) {
            return new Writable(embedded, null, null, false);
        }

        if (caseFile != null) {
            YamlConfiguration yaml = loadYaml(caseFile);
            if (yaml == null) return null;
            return new Writable(caseSection(yaml), yaml, caseFile, true);
        }

        if (embedded != null) return new Writable(embedded, null, null, false);
        ConfigurationSection cases = plugin.getConfig().getConfigurationSection("cases");
        if (cases == null) {
            if (!create) return null;
            cases = plugin.getConfig().createSection("cases");
        }
        ConfigurationSection section = create ? cases.createSection(normalized) : null;
        return section == null ? null : new Writable(section, null, null, false);
    }

    static boolean preferEmbeddedSource(boolean autoExport, boolean embeddedExists, boolean standaloneExists) {
        return autoExport && embeddedExists && standaloneExists;
    }

    static boolean replaceStandaloneWithEmbedded(boolean autoExport, boolean previousFileBacked) {
        return autoExport && previousFileBacked;
    }

    static boolean declaredSourceExists(boolean embeddedDeclared, int physicalFileMatches) {
        return embeddedDeclared || physicalFileMatches > 0;
    }

    static boolean missingCaseDirectoryIsEmpty(boolean pathExists, boolean embeddedCasesExist) {
        return !pathExists && embeddedCasesExist;
    }

    public boolean save(Writable writable) {
        if (writable == null) return false;
        if (writable.fileBacked()) {
            return saveYamlAtomically(writable.yaml(), writable.file());
        }
        return saveMainConfig();
    }

    public boolean saveMainConfig() {
        return saveYamlAtomically(plugin.getConfig(), new File(plugin.getDataFolder(), "config.yml"));
    }

    public boolean delete(String caseName) {
        Writable writable = writable(caseName, false);
        if (writable == null || writable.section() == null) return false;

        if (writable.fileBacked()) {
            try {
                return Files.deleteIfExists(writable.file().toPath());
            } catch (IOException ex) {
                plugin.getLogger().severe("Не удалось удалить конфиг кейса '" + normalizeName(caseName) + "': " + ex.getMessage());
                return false;
            }
        }

        ConfigurationSection cases = plugin.getConfig().getConfigurationSection("cases");
        if (cases == null) return false;
        String normalized = normalizeName(caseName);
        Object snapshot = cases.get(normalized);
        cases.set(normalized, null);
        if (saveMainConfig()) return true;
        cases.set(normalized, snapshot);
        return false;
    }

    public void exportMainCasesIfMissing() {
        if (!plugin.getConfig().getBoolean("case-files.auto-export", true)) return;
        File directory = directory();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("cases");
        if (!ensureDirectory(directory)) return;
        if (root == null || root.getKeys(false).isEmpty()) {
            return;
        }

        int expected = root.getKeys(false).size();
        Set<String> exportedIds = new HashSet<>();
        Map<String, File> exportTargets = new LinkedHashMap<>();
        for (String caseName : root.getKeys(false)) {
            ConfigurationSection source = root.getConfigurationSection(caseName);
            String normalized = normalizeName(caseName);
            if (source == null || !isValidId(normalized) || !exportedIds.add(normalized)) {
                plugin.getLogger().severe("Автоэкспорт остановлен: некорректный или повторяющийся id кейса '" + caseName + "'.");
                return;
            }
            File target = resolveExportTarget(normalized);
            if (target == null) {
                plugin.getLogger().severe("Автоэкспорт остановлен: найдено несколько standalone-файлов для кейса '"
                        + normalized + "'.");
                return;
            }
            exportTargets.put(normalized, target);
        }

        int exported = 0;
        for (String caseName : root.getKeys(false)) {
            String normalized = normalizeName(caseName);
            ConfigurationSection source = root.getConfigurationSection(caseName);
            File target = exportTargets.get(normalized);
            if (sameCaseContent(source, target)) {
                // A previous migration attempt may already have written this
                // case. Rewriting it would rotate <case>.yml.bak again and
                // destroy the original standalone version kept for recovery.
                exported++;
                continue;
            }
            YamlConfiguration yaml = new YamlConfiguration();
            copySection(source, yaml);
            if (saveYamlAtomically(yaml, target)) exported++;
        }
        if (exported == expected && exportedIds.size() == expected) {
            if (removeMainCasesWhenExported(exportTargets)) {
                plugin.getLogger().info("Созданы отдельные конфиги кейсов: plugins/pnCases/cases/*.yml ("
                        + exported + ").");
            }
        } else {
            plugin.getLogger().severe("Секция cases оставлена в config.yml: успешно экспортировано "
                    + exported + " из " + expected + " кейсов.");
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

    private ConfigurationSection embeddedCaseSection(String caseId) {
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("cases");
        if (root == null) return null;
        ConfigurationSection exact = root.getConfigurationSection(caseId);
        if (exact != null) return exact;

        ConfigurationSection match = null;
        for (String configured : root.getKeys(false)) {
            if (!normalizeName(configured).equals(caseId)) continue;
            if (match != null) {
                plugin.getLogger().severe("Несколько embedded-кейсов имеют id '" + caseId + "'. Редактирование отменено.");
                return null;
            }
            match = root.getConfigurationSection(configured);
        }
        return match;
    }

    private boolean embeddedCaseDeclared(String caseId) {
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("cases");
        if (root == null) return false;
        return root.getKeys(false).stream().anyMatch(configured -> normalizeName(configured).equals(caseId));
    }

    private int matchingCaseFileCount(String caseId) {
        File direct = file(caseId);
        File[] matches = directory().listFiles((dir, name) ->
                name.toLowerCase(Locale.ROOT).endsWith(".yml") && fileId(name).equals(caseId));
        return matches == null ? (direct.isFile() ? 1 : 0) : matches.length;
    }

    private File existingCaseFile(String caseId) {
        File resolved = resolveExportTarget(caseId);
        return resolved != null && resolved.isFile() ? resolved : null;
    }

    private File resolveExportTarget(String caseId) {
        File[] matches = directory().listFiles((dir, name) ->
                name.toLowerCase(Locale.ROOT).endsWith(".yml") && fileId(name).equals(caseId));
        if (matches == null || matches.length == 0) return file(caseId);
        return matches.length == 1 ? matches[0] : null;
    }

    private boolean removeMainCasesWhenExported(Map<String, File> exportedFiles) {
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("cases");
        if (root == null || root.getKeys(false).isEmpty()) return false;
        for (String caseName : root.getKeys(false)) {
            String normalized = normalizeName(caseName);
            ConfigurationSection original = root.getConfigurationSection(caseName);
            File exported = exportedFiles.get(normalized);
            if (!isValidId(normalized) || original == null || exported == null) {
                plugin.getLogger().severe("Секция cases оставлена в config.yml: для кейса '" + caseName
                        + "' нет корректного отдельного файла.");
                return false;
            }
            YamlConfiguration readback = loadYaml(exported);
            if (readback == null) return false;
            ConfigurationSection exportedSection = caseSection(readback);
            if (exportedSection.getKeys(false).isEmpty()
                    || !snapshotSection(original).equals(snapshotSection(exportedSection))) {
                plugin.getLogger().severe("Секция cases оставлена в config.yml: cases/" + exported.getName()
                        + " не совпадает с исходными настройками кейса '" + caseName + "'.");
                return false;
            }
        }

        File backup = new File(plugin.getDataFolder(), "config.cases-backup.yml");
        if (!saveYamlAtomically(plugin.getConfig(), backup)) return false;

        Map<String, Object> previousCases = snapshotSection(root);
        plugin.getConfig().set("cases", null);
        if (!saveMainConfig()) {
            ConfigurationSection restoredCases = plugin.getConfig().createSection("cases");
            restoreSection(restoredCases, previousCases);
            plugin.getLogger().severe("Секция cases не удалена из config.yml: безопасное сохранение не удалось.");
            return false;
        }
        plugin.getLogger().info("Секция cases перенесена в plugins/pnCases/cases/*.yml. "
                + "Старый config сохранён как config.cases-backup.yml.");
        return true;
    }

    private boolean sameCaseContent(ConfigurationSection source, File target) {
        if (source == null || target == null || !target.isFile()) return false;
        YamlConfiguration existing = loadYaml(target);
        return existing != null && sameCaseContent(source, caseSection(existing));
    }

    static boolean sameCaseContent(ConfigurationSection source, ConfigurationSection target) {
        return source != null && target != null
                && snapshotSection(source).equals(snapshotSection(target));
    }

    private File directory() {
        return new File(plugin.getDataFolder(), "cases");
    }

    private File file(String caseName) {
        return new File(directory(), normalizeName(caseName) + ".yml");
    }

    static String fileId(String fileName) {
        if (fileName == null) return "";
        String name = fileName.trim();
        if (name.toLowerCase(Locale.ROOT).endsWith(".yml")) {
            name = name.substring(0, name.length() - 4);
        }
        return normalizeName(name);
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

    private static void clearSection(ConfigurationSection section) {
        for (String key : new ArrayList<>(section.getKeys(false))) {
            section.set(key, null);
        }
    }

    private YamlConfiguration loadYaml(File file) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
            return yaml;
        } catch (IOException | InvalidConfigurationException ex) {
            plugin.getLogger().severe("Не удалось прочитать конфиг кейса " + file.getName() + ": " + ex.getMessage());
            return null;
        }
    }

    private void warnIdMismatch(String source, String canonicalId, ConfigurationSection section) {
        String explicitId = firstString(section, "id", "case_id", "case-id");
        if (explicitId != null && !explicitId.isBlank() && !canonicalId.equals(normalizeName(explicitId))) {
            plugin.getLogger().warning(source + " содержит id='" + explicitId + "', но используется канонический id '"
                    + canonicalId + "' из имени файла/секции. Измените id, чтобы убрать предупреждение.");
        }
    }

    private boolean saveYamlAtomically(FileConfiguration yaml, File target) {
        Path temporary = null;
        try {
            File parent = target.getParentFile();
            if (parent != null && !ensureDirectory(parent)) return false;
            Path parentPath = parent == null ? target.toPath().toAbsolutePath().getParent() : parent.toPath();
            temporary = Files.createTempFile(parentPath, target.getName() + ".", ".tmp");
            yaml.save(temporary.toFile());

            YamlConfiguration readback = new YamlConfiguration();
            readback.load(temporary.toFile());

            Path targetPath = target.toPath();
            if (Files.isRegularFile(targetPath)) {
                Files.copy(targetPath, targetPath.resolveSibling(target.getName() + ".bak"),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(temporary, targetPath, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | InvalidConfigurationException ex) {
            plugin.getLogger().severe("Не удалось безопасно сохранить " + target.getName() + ": " + ex.getMessage());
            return false;
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                }
            }
        }
    }

    static Map<String, Object> snapshotSection(ConfigurationSection section) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            copy.put(key, deepCopyValue(section.get(key)));
        }
        return copy;
    }

    static void restoreSection(ConfigurationSection section, Map<String, Object> snapshot) {
        clearSection(section);
        restoreEntries(section, snapshot);
    }

    private static void restoreEntries(ConfigurationSection section, Map<?, ?> values) {
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                ConfigurationSection child = section.createSection(key);
                restoreEntries(child, nested);
            } else {
                section.set(key, deepCopyValue(value));
            }
        }
    }

    private static List<?> deepCopyList(List<?> source) {
        List<Object> copy = new ArrayList<>();
        if (source == null) return copy;
        for (Object value : source) copy.add(deepCopyValue(value));
        return copy;
    }

    private static Object deepCopyValue(Object value) {
        if (value instanceof ConfigurationSection section) return snapshotSection(section);
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(String.valueOf(entry.getKey()), deepCopyValue(entry.getValue()));
            }
            return copy;
        }
        if (value instanceof List<?> list) return deepCopyList(list);
        return value;
    }

    public static String defaultDisplayName(String caseName) {
        return "&x&4&2&9&F&9&1Новый кейс &8| &f" + caseName;
    }

    static void writeNewCaseDefaults(ConfigurationSection root, String caseName) {
        String visibleName = defaultDisplayName(caseName);
        root.set("blocks", List.of());
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
        gui.set("title", "{display-name}");
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
        openItem.set("name", "{display-name}");
        openItem.set("lore", List.of("", "&7Новый кейс готов к настройке.", ""));
        openItem.set("extra-lore", List.of(
                "",
                "&7Ключи: &f{have}&7/&f{need} &8({key_name}&8)",
                "",
                "{left-click}",
                "{right-click}"
        ));

        ConfigurationSection cost = root.createSection("cost");
        cost.set("type", "KEY");
        cost.set("key", caseName);
        cost.set("amount", 1);
        cost.set("buy_xp_enabled", false);
        cost.set("buy_xp_levels", 0);

        ConfigurationSection animation = root.createSection("animation");
        animation.set("fixed", AnimationType.FORTUNE_RING.name());
        animation.set("items", List.of(
                Map.of("material", "GOLD_INGOT", "name", "&6Золото"),
                Map.of("material", "DIAMOND", "name", "&bАлмаз"),
                Map.of("material", "EMERALD", "name", "&aИзумруд")
        ));
        root.set("rewards", List.of(Map.of(
                "chance", 100,
                "rarity", "COMMON",
                "type", "ITEM",
                "item", Map.of("material", "CHEST", "amount", 1, "name", "&fНаграда кейса"),
                "message", "&aВы получили &6{reward}&a!"
        )));
    }

    public record Source(String name, ConfigurationSection section, boolean fileBacked) {
    }

    public record LoadResult(List<Source> sources, boolean successful) {
        public LoadResult {
            sources = List.copyOf(sources);
        }
    }

    public record Writable(ConfigurationSection section, YamlConfiguration yaml, File file, boolean fileBacked) {
    }
}
