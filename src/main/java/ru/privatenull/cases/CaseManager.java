package ru.privatenull.cases;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.privatenull.cases.animation.AnimationRegistry;
import ru.privatenull.cases.animation.AnimationType;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.cases.model.CaseGuiLayout;
import ru.privatenull.cases.model.IdleParticleSettings;
import ru.privatenull.cases.model.Reward;
import ru.privatenull.listeners.CaseGuiHolder;
import ru.privatenull.pnCases;
import ru.privatenull.storage.OpenHistoryStorage;
import ru.privatenull.storage.PlayerPrefsStorage;
import ru.privatenull.util.ItemFactory;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class CaseManager {

    public enum UnbindCaseResult {
        REMOVED,
        NOT_FOUND,
        NOT_BOUND
    }

    public static final int PREVIEW_SLOT = 50;
    public static final int ANIMATION_SLOT = 49;

    private static final int[] HISTORY_SLOTS = {45, 46, 47, 48, 51, 52, 53};
    private static final int[] DECOR_SLOTS = {
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 17,
            18, 26,
            27, 35,
            36, 37, 38, 39, 40, 41, 42, 43, 44
    };
    private static final DateTimeFormatter HISTORY_TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM HH:mm");
    private static final List<String> DEFAULT_CASE_RESOURCES = List.of(
            "cases/money.yml",
            "cases/playerpoints.yml",
            "cases/items.yml",
            "cases/luckperms.yml"
    );

    private final pnCases plugin;

    private final Map<String, CaseDefinition> casesByName = new HashMap<>();
    private final Map<BlockKey, String> caseByBlock = new HashMap<>();
    private final Map<String, ConfigurationSection> caseSections = new HashMap<>();
    private final Set<String> fileBackedCases = new HashSet<>();
    private final Map<String, String> keyNames = new HashMap<>();

    private final Set<UUID> openingPlayers = ConcurrentHashMap.newKeySet();
    private final Map<BlockKey, UUID> busyCases = new ConcurrentHashMap<>();
    private final Map<UUID, BlockKey> selectedCaseBlocks = new ConcurrentHashMap<>();
    private final Map<UUID, AnimationType> playerAnimations = new ConcurrentHashMap<>();

    private final AnimationRegistry animationRegistry;
    private final IdleParticleService idleParticles;
    private final OpenHistoryStorage openHistoryStorage;
    private final PlayerPrefsStorage playerPrefs;

    public CaseManager(pnCases plugin) {
        this.plugin = plugin;
        this.animationRegistry = new AnimationRegistry(plugin);
        this.idleParticles = new IdleParticleService(plugin, this);
        this.openHistoryStorage = new OpenHistoryStorage(plugin.getDatabase());
        this.playerPrefs = new PlayerPrefsStorage(plugin.getDatabase());
    }

    public pnCases getPlugin() { return plugin; }

    public void shutdown() {
        animationRegistry.shutdownAll();
        idleParticles.shutdown();
        openingPlayers.clear();
        casesByName.clear();
        caseByBlock.clear();
        caseSections.clear();
        fileBackedCases.clear();
        keyNames.clear();
        selectedCaseBlocks.clear();
        playerAnimations.clear();
    }

    public List<String> getCaseNames() { return new ArrayList<>(casesByName.keySet()); }
    public List<String> getConfiguredCaseNames() {
        Set<String> names = new TreeSet<>();
        ConfigurationSection cases = plugin.getConfig().getConfigurationSection("cases");
        if (cases != null) {
            names.addAll(cases.getKeys(false));
        }

        File[] files = getCaseFilesDirectory().listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                names.add(name.substring(0, name.length() - 4));
            }
        }

        if (names.isEmpty()) {
            names.addAll(getCaseNames());
        }
        return new ArrayList<>(names);
    }
    public List<String> getKeyNames()  { return new ArrayList<>(keyNames.keySet()); }
    public boolean keyExists(String keyId) { return keyNames.containsKey(keyId.toLowerCase()); }

    public int getLoadedCaseCount() { return casesByName.size(); }
    public int getConfiguredKeyCount() { return keyNames.size(); }

    public CaseDefinition getCaseByName(String name) { return casesByName.get(name.toLowerCase()); }
    public ConfigurationSection getCaseSection(String name) {
        return name == null ? null : caseSections.get(name.toLowerCase(Locale.ROOT));
    }

    public boolean updateCaseConfig(String caseName, Consumer<ConfigurationSection> updater) {
        if (caseName == null || caseName.isBlank() || updater == null) {
            return false;
        }

        WritableCaseConfig writable = getExistingWritableCaseConfig(caseName);
        if (writable == null || writable.section() == null) {
            return false;
        }

        updater.accept(writable.section());
        saveWritableCaseConfig(writable);
        reloadFromConfig();
        return getCaseByName(caseName) != null;
    }

    public CaseDefinition getCaseByBlock(Block block) {
        String name = caseByBlock.get(BlockKey.of(block));
        return name == null ? null : casesByName.get(name);
    }

    public AnimationRegistry getAnimationRegistry() { return animationRegistry; }

    public void exportMainCasesToFilesIfMissing() {
        if (!plugin.getConfig().getBoolean("case-files.auto-export", true)) {
            return;
        }

        File dir = getCaseFilesDirectory();
        File[] existing = dir.listFiles((file, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (existing != null && existing.length > 0) {
            removeMainCasesFromConfigIfFileBacked();
            return;
        }

        ConfigurationSection root = plugin.getConfig().getConfigurationSection("cases");
        if (root == null) {
            if (!dir.exists() && !dir.mkdirs()) {
                plugin.getLogger().warning("Не удалось создать папку cases для отдельных конфигов кейсов.");
                return;
            }
            saveBundledCaseFiles();
            return;
        }

        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("Не удалось создать папку cases для отдельных конфигов кейсов.");
            return;
        }

        if (root.getKeys(false).isEmpty()) {
            saveBundledCaseFiles();
            return;
        }

        int exported = 0;
        for (String caseName : root.getKeys(false)) {
            ConfigurationSection source = root.getConfigurationSection(caseName);
            if (source == null) continue;

            YamlConfiguration yaml = new YamlConfiguration();
            copySection(source, yaml);

            File file = getCaseFile(caseName);
            try {
                yaml.save(file);
                exported++;
            } catch (IOException e) {
                plugin.getLogger().warning("Не удалось создать отдельный конфиг кейса " + caseName + ": " + e.getMessage());
            }
        }

        if (exported > 0) {
            removeMainCasesFromConfigIfFileBacked();
        }

        if (exported > 0) {
            plugin.getLogger().info("Созданы отдельные конфиги кейсов: plugins/pnCases/cases/*.yml (" + exported + ").");
        }
    }

    private void removeMainCasesFromConfigIfFileBacked() {
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("cases");
        if (root == null || root.getKeys(false).isEmpty()) {
            return;
        }

        Set<String> fileCases = new HashSet<>();
        File[] files = getCaseFilesDirectory().listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null || files.length == 0) {
            return;
        }

        for (File file : files) {
            String name = file.getName();
            fileCases.add(normalizeCaseName(name.substring(0, name.length() - 4)));
        }

        for (String caseName : root.getKeys(false)) {
            if (!fileCases.contains(normalizeCaseName(caseName))) {
                return;
            }
        }

        File backup = new File(plugin.getDataFolder(), "config.cases-backup.yml");
        if (!backup.exists()) {
            try {
                plugin.getConfig().save(backup);
            } catch (IOException e) {
                plugin.getLogger().warning("Не удалось сохранить backup перед переносом кейсов из config.yml: " + e.getMessage());
                return;
            }
        }

        plugin.getConfig().set("cases", null);
        plugin.saveConfig();
        plugin.getLogger().info("Секция cases перенесена в plugins/pnCases/cases/*.yml. Старый config сохранён как config.cases-backup.yml.");
    }

    private void saveBundledCaseFiles() {
        int saved = 0;
        for (String resource : DEFAULT_CASE_RESOURCES) {
            if (plugin.getResource(resource) == null) {
                continue;
            }

            File target = new File(plugin.getDataFolder(), resource);
            if (target.exists()) {
                continue;
            }

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

    public AnimationType getPlayerAnimation(UUID uuid) {
        return playerAnimations.computeIfAbsent(uuid, playerPrefs::getAnimation);
    }

    public void setPlayerAnimation(UUID uuid, AnimationType type) {
        playerAnimations.put(uuid, type);
        playerPrefs.setAnimation(uuid, type);
    }

    public void bindCaseToBlock(String caseName, Block target) {
        WritableCaseConfig writable = getWritableCaseConfig(caseName);
        ConfigurationSection cs = writable.section();

        List<Map<String, Object>> blocks = readConfiguredBlockMaps(cs);
        addBlockMap(blocks, Map.of(
                "world", target.getWorld().getName(),
                "x", target.getX(),
                "y", target.getY(),
                "z", target.getZ()
        ));
        cs.set("block", null);
        cs.set("blocks", blocks);

        if (!cs.isConfigurationSection("gui")) {
            ConfigurationSection gui = cs.createSection("gui");
            gui.set("title", "&8Кейс: " + caseName);
            ConfigurationSection openItem = gui.createSection("open-item");
            openItem.set("material", "CHEST");
            openItem.set("name", "&aОткрыть кейс");
            openItem.set("lore", List.of("&7Нажми, чтобы открыть"));
        }

        if (!cs.isConfigurationSection("cost")) {
            ConfigurationSection cost = cs.createSection("cost");
            cost.set("type", "NONE");
            cost.set("amount", 0);
            cost.set("buy_xp_levels", 0);
        }

        if (!cs.isConfigurationSection("animation")) {
            ConfigurationSection an = cs.createSection("animation");
            an.set("duration_ticks", 80);
            an.set("cycle_every_ticks", 2);
            an.set("rise_blocks", 1.2);
            an.set("spin_degrees_per_tick", 18);
            an.set("items", List.of(
                    Map.of("material", "SLIME_BALL", "name", "&aСгусток яда"),
                    Map.of("material", "SPIDER_EYE", "name", "&2Ядовитый глаз")
            ));
        }

        if (!cs.isList("rewards")) {
            cs.set("rewards", List.of(Map.of(
                    "chance", 100, "type", "ITEM",
                    "item", Map.of("material", "DIAMOND", "amount", 1, "name", "&bАлмаз")
            )));
        }

        saveWritableCaseConfig(writable);
        reloadFromConfig();
    }

    public UnbindCaseResult unbindCaseFromBlock(String caseName) {
        if (caseName == null || caseName.isBlank()) {
            return UnbindCaseResult.NOT_FOUND;
        }

        String normalized = caseName.toLowerCase(Locale.ROOT);
        WritableCaseConfig writable = getExistingWritableCaseConfig(normalized);
        if (writable == null) {
            return UnbindCaseResult.NOT_FOUND;
        }

        ConfigurationSection section = writable.section();
        if (section == null || (!section.isConfigurationSection("block") && !section.isList("blocks"))) {
            return UnbindCaseResult.NOT_BOUND;
        }

        CaseDefinition loaded = casesByName.get(normalized);
        if (loaded != null) {
            for (Location location : loaded.blockLocations()) {
                busyCases.remove(BlockKey.of(location));
            }
        }

        section.set("block", null);
        section.set("blocks", null);
        saveWritableCaseConfig(writable);
        reloadFromConfig();
        return UnbindCaseResult.REMOVED;
    }

    public void reloadFromConfig() {
        var holograms = plugin.getHolograms();
        if (holograms != null) holograms.clearManaged();

        casesByName.clear();
        caseByBlock.clear();
        keyNames.clear();

        ConfigurationSection keysSec = plugin.getConfig().getConfigurationSection("keys");
        if (keysSec != null) {
            for (String keyId : keysSec.getKeys(false)) {
                ConfigurationSection ks = keysSec.getConfigurationSection(keyId);
                String displayName = ks != null ? ks.getString("name", keyId) : keyId;
                keyNames.put(keyId.toLowerCase(), color(displayName));
            }
        }

        List<CaseConfigSource> sources = loadCaseConfigSources();
        if (sources.isEmpty()) {
            idleParticles.syncCases(List.of());
            plugin.getLogger().info("Loaded cases: 0, active blocks: 0, keys: " + keyNames.size());
            return;
        }

        for (CaseConfigSource source : sources) {
            String caseName = source.name();
            ConfigurationSection cs = source.section();
            if (cs == null) continue;

            List<Location> blockLocs = readBlockLocations(cs);

            ConfigurationSection gui = cs.getConfigurationSection("gui");
            String title = gui != null ? gui.getString("title", "&8Case") : "&8Case";
            ItemStack openBtn = ItemFactory.fromSection(gui != null ? gui.getConfigurationSection("open-item") : null);
            if (openBtn == null) openBtn = new ItemStack(Material.CHEST);
            CaseGuiLayout guiLayout = readGuiLayout(gui);
            IdleParticleSettings idleParticleSettings = readIdleParticles(cs.getConfigurationSection("idle-particles"));

            ConfigurationSection cost = cs.getConfigurationSection("cost");
            String typeStr = cost != null ? cost.getString("type", "NONE") : "NONE";
            CaseDefinition.CostType costType;
            try { costType = CaseDefinition.CostType.valueOf(typeStr.toUpperCase(Locale.ROOT)); }
            catch (Exception ex) { costType = CaseDefinition.CostType.NONE; }
            int costAmount = cost != null ? cost.getInt("amount", 0) : 0;
            String costKeyId = cost != null ? cost.getString("key", null) : null;
            if (costKeyId != null) costKeyId = costKeyId.toLowerCase(Locale.ROOT);
            int buyXp = 0;
            if (cost != null) {
                boolean buyXpEnabled = getBooleanAlias(cost, true,
                        "buy_xp_enabled", "buy-xp-enabled", "buy_key_with_xp", "buy-key-with-xp");
                if (buyXpEnabled) {
                    buyXp = cost.getInt("buy_xp_levels", 0);
                    if (buyXp <= 0) buyXp = cost.getInt("buy-xp-levels", 0);
                }
            }

            ConfigurationSection an = cs.getConfigurationSection("animation");
            int duration = getIntAlias(an, 80, "duration_ticks", "duration-ticks");
            int cycleEvery = getIntAlias(an, 2, "cycle_every_ticks", "cycle-every-ticks");
            double rise = getDoubleAlias(an, 1.2, "rise_blocks", "rise-blocks");
            float spin = (float) getDoubleAlias(an, 18.0, "spin_degrees_per_tick", "spin-degrees-per-tick");
            AnimationType fixedAnimation = readFixedAnimation(an);

            List<ItemStack> animItems = new ArrayList<>();
            if (an != null && an.isList("items")) {
                List<?> raw = an.getList("items");
                if (raw != null) for (Object o : raw) {
                    if (o instanceof Map<?, ?> map) animItems.add(ItemFactory.fromMap(map));
                }
            }
            animItems.removeIf(Objects::isNull);
            if (animItems.isEmpty()) animItems.add(new ItemStack(Material.SLIME_BALL));

            List<Reward> rewards = new ArrayList<>();
            if (cs.isList("rewards")) {
                List<?> rawRewards = cs.getList("rewards");
                if (rawRewards != null) for (Object rr : rawRewards) {
                    if (!(rr instanceof Map<?, ?> map)) continue;
                    int chance = asInt(map.get("chance"), 0);
                    String typeS = String.valueOf(map.containsKey("type") ? map.get("type") : "ITEM");
                    Reward.Type rType = inferRewardType(typeS, map);

                    String message = map.containsKey("message") ? String.valueOf(map.get("message")) : null;
                    Object rarityRaw = firstPresent(map, "rarity", "rare");
                    Reward.Rarity rarity = Reward.Rarity.parse(rarityRaw == null ? null : String.valueOf(rarityRaw), chance);
                    String displayName = null;
                    ItemStack item = null;
                    String lpGroup = null, lpNode = null, lpDuration = null;
                    double vaultAmount = 0.0;
                    int playerPointsAmount = 0;

                    if (rType == Reward.Type.ITEM) {
                        Object itemObj = firstPresent(map, "item", "items");
                        if (itemObj instanceof Map<?, ?> itemMap) {
                            item = ItemFactory.fromMap(itemMap);
                            Object nameObj = itemMap.get("name");
                            if (nameObj != null) displayName = String.valueOf(nameObj);
                        }
                        if (displayName == null && item != null) {
                            ItemMeta meta = item.getItemMeta();
                            if (meta != null && meta.hasDisplayName()) displayName = meta.getDisplayName();
                        }
                        if (displayName == null && item != null) displayName = "&f" + item.getType().name();
                    } else if (rType == Reward.Type.LUCKPERMS) {
                        Object lpObj = map.get("luckperms");
                        if (lpObj instanceof Map<?, ?> lpMap) {
                            Object g = lpMap.get("group"), n = lpMap.get("node"), d = lpMap.get("duration");
                            if (g != null) lpGroup = String.valueOf(g);
                            if (n != null) lpNode = String.valueOf(n);
                            if (d != null) lpDuration = String.valueOf(d);
                            Object dn = firstPresent(lpMap, map, "display_name", "display-name", "displayName", "name");
                            displayName = dn != null ? String.valueOf(dn) : null;
                        }
                        item = buildRewardVisualItem(map, displayName);
                        displayName = resolveRewardDisplayName(item, displayName != null ? displayName : "&dПривилегия");
                    } else if (rType == Reward.Type.VAULT) {
                        Map<?, ?> vaultMap = getNestedMap(map, "vault");
                        vaultAmount = asDouble(firstPresent(vaultMap, map, "amount", "money", "value"), 0.0);
                        displayName = "&a" + formatVaultAmount(vaultAmount);
                        item = buildRewardVisualItem(map, displayName);
                        displayName = resolveRewardDisplayName(item, displayName);
                    } else if (rType == Reward.Type.PLAYERPOINTS) {
                        Map<?, ?> pointsMap = getNestedMap(map, "playerpoints", "player_points", "player-points", "points");
                        playerPointsAmount = Math.max(0, asInt(firstPresent(pointsMap, map, "amount", "points", "value"), 0));
                        displayName = "&b" + formatPlayerPointsAmount(playerPointsAmount);
                        item = buildRewardVisualItem(map, displayName);
                        displayName = resolveRewardDisplayName(item, displayName);
                    }

                    if (isValidReward(chance, rType, item, lpGroup, lpNode, vaultAmount, playerPointsAmount)) {
                        rewards.add(new Reward(chance, rType, item, lpGroup, lpNode, lpDuration,
                                vaultAmount, playerPointsAmount, message, displayName, rarity));
                    }
                }
            }
            if (rewards.isEmpty()) {
                rewards.add(new Reward(100, Reward.Type.ITEM, new ItemStack(Material.DIAMOND),
                        null, null, null, "&aТы получил алмаз!", "&bАлмаз", Reward.Rarity.COMMON));
            }

            CaseDefinition def = new CaseDefinition(
                    caseName.toLowerCase(Locale.ROOT), blockLocs, title, openBtn, guiLayout, idleParticleSettings,
                    costType, costAmount, costKeyId, buyXp,
                    duration, cycleEvery, rise, spin, fixedAnimation, animItems, rewards
            );
            casesByName.put(def.name(), def);
            caseSections.put(def.name(), cs);
            if (source.fileBacked()) {
                fileBackedCases.add(def.name());
            }
            for (Location blockLoc : blockLocs) {
                if (blockLoc != null) {
                    caseByBlock.put(BlockKey.of(blockLoc), def.name());
                }
            }
        }

        if (holograms != null) holograms.syncCases(casesByName.values());
        idleParticles.syncCases(casesByName.values());
        plugin.getLogger().info("Loaded cases: " + casesByName.size() + ", active blocks: " + caseByBlock.size() + ", keys: " + keyNames.size());
    }

    public ItemStack buildGuiOpenItem(Player p, CaseDefinition def) {
        ItemStack it = def.openButton().clone();
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;

        int buyExp = Math.max(0, def.buyKeyWithXpLevels());
        List<String> lore = meta.hasLore()
                ? new ArrayList<>(Objects.requireNonNull(meta.getLore()))
                : new ArrayList<>();

        int have = 0;
        int need = Math.max(1, def.costAmount());
        String keyId = def.costKeyId();
        if (def.costType() == CaseDefinition.CostType.KEY) {
            have = (keyId == null) ? 0 : plugin.getKeyStorage().get(p.getUniqueId(), keyId);
        }

        lore.addAll(buildCaseButtonExtraLore(def, have, need, buyExp));

        meta.setLore(lore);
        it.setItemMeta(meta);
        return it;
    }

    private List<String> buildCaseButtonExtraLore(CaseDefinition def, int have, int need, int buyExp) {
        String keyId = def.costKeyId() == null ? "" : def.costKeyId();
        String keyName = keyId.isBlank() ? "" : keyNames.getOrDefault(keyId.toLowerCase(Locale.ROOT), keyId);
        String keysBalance = def.costType() == CaseDefinition.CostType.KEY
                ? plugin.getMessages().getOr("gui.case-button.keys-balance", "gui-keys-balance",
                "have", String.valueOf(have),
                "need", String.valueOf(need),
                "key", keyId,
                "key_name", keyName,
                "key-name", keyName)
                : "";
        String buyHint = plugin.getMessages().getOr("gui.case-button.buy-xp-hint", "gui-buy-xp-hint",
                "levels", String.valueOf(buyExp));
        String previewLeftHint = plugin.getMessages().getOr("gui.case-button.preview-left-hint", "gui.case-button.buy-xp-disabled");
        String openHint = plugin.getMessages().getOr("gui.case-button.open-hint", "gui-open-hint");
        String previewHint = buyExp > 0
                ? plugin.getMessages().getOr("gui.case-button.preview-hint", "gui.case-button.preview-hint")
                : "";
        if (previewHint.startsWith("§c[missing:")) {
            previewHint = color("&7СКМ &8— &bпосмотреть содержимое");
        }

        List<String> lines = plugin.getMessages().getList("gui.case-button.extra-lore",
                "case", def.name(),
                "title", def.guiTitle(),
                "material", def.openButton().getType().getKey().toString(),
                "key", keyId,
                "key_name", keyName,
                "key-name", keyName,
                "have", String.valueOf(have),
                "need", String.valueOf(need),
                "levels", String.valueOf(buyExp),
                "keys-balance", keysBalance,
                "buy-xp-hint", buyHint,
                "preview-left-hint", previewLeftHint,
                "left-click", buyExp > 0 ? buyHint : previewLeftHint,
                "open-hint", openHint,
                "right-click", openHint,
                "preview-hint", previewHint,
                "middle-click", previewHint);
        trimTrailingEmptyLines(lines);
        return lines;
    }

    private void trimTrailingEmptyLines(List<String> lines) {
        while (!lines.isEmpty()) {
            String last = ChatColor.stripColor(lines.get(lines.size() - 1));
            if (last != null && !last.isBlank()) {
                return;
            }
            lines.remove(lines.size() - 1);
        }
    }

    public ItemStack buildAnimationSelectorItem(Player p) {
        return buildAnimationSelectorItem(p, null);
    }

    public ItemStack buildAnimationSelectorItem(Player p, CaseDefinition def) {
        if (def != null && def.guiLayout().animationItem() != null) {
            return def.guiLayout().animationItem().clone();
        }

        AnimationType current = getPlayerAnimation(p.getUniqueId());
        ItemStack it = new ItemStack(current.icon());
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;

        meta.setDisplayName(color("&fАнимация: " + current.displayName()));

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(color("&7Текущая: " + current.displayName()));
        lore.add(color("&7Нажмите, чтобы выбрать другую"));
        lore.add("");

        meta.setLore(lore);
        it.setItemMeta(meta);
        return it;
    }

    public ItemStack buildPreviewButton(CaseDefinition def) {
        if (def.guiLayout().previewItem() != null) {
            return def.guiLayout().previewItem().clone();
        }

        ItemStack item = new ItemStack(Material.ENDER_EYE);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        int rewards = def.rewards().size();
        meta.setDisplayName(color("&x&4&2&9&F&9&1▸ &fСодержимое кейса"));
        meta.setLore(List.of(
                "",
                color("&x&A&0&E&F&A&1 «Предпросмотр»"),
                color(" &7- &fНаград: &x&4&2&9&F&9&1" + rewards),
                color(" &7- &fПоказаны шансы и редкость"),
                "",
                color("&x&4&2&9&F&9&1▸ &fНажмите, чтобы открыть")
        ));
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack buildRewardPreviewItem(Reward reward, int totalChance) {
        return buildRewardPreviewItem(null, reward, totalChance);
    }

    public ItemStack buildRewardPreviewItem(CaseDefinition def, Reward reward, int totalChance) {
        ItemStack item = buildRewardDisplayItem(def, reward);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String rewardName = color(resolveRewardViewName(reward, item));
        meta.setDisplayName(reward.rarity().color() + "◆ " + rewardName);

        List<String> lore = new ArrayList<>();
        if (meta.hasLore() && meta.getLore() != null) {
            lore.addAll(meta.getLore());
            lore.add("");
        }

        lore.add(color("§x§A§0§E§F§A§1 «Информация»"));
        lore.add(color(" §7- &fТип: §x§4§2§9§F§9§1" + formatRewardType(reward.type())));
        lore.add(color(" §7- &fРедкость: " + reward.rarity().coloredName()));
        lore.add(color(" §7- &fШанс: §x§4§2§9§F§9§1" + formatChancePercent(reward.chance(), totalChance)));
        lore.add(color(" §7- &fВес шанса: §x§4§2§9§F§9§1" + reward.chance()));

        if (reward.type() == Reward.Type.VAULT) {
            lore.add(color(" §7- &fСумма: §x§4§2§9§F§9§1" + formatVaultAmount(reward.vaultAmount())));
        } else if (reward.type() == Reward.Type.PLAYERPOINTS) {
            lore.add(color(" §7- &fПоинты: §x§4§2§9§F§9§1" + formatPlayerPointsAmount(reward.playerPointsAmount())));
        } else if (reward.type() == Reward.Type.LUCKPERMS) {
            if (reward.lpDuration() != null && !reward.lpDuration().isBlank()) {
                lore.add(color(" §7- &fСрок: §x§4§2§9§F§9§1" + reward.lpDuration()));
            }
        }
        lore.add("");

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildRewardDisplayItem(CaseDefinition def, Reward reward) {
        if (reward.visualItem() != null) {
            return reward.visualItem().clone();
        }

        ItemStack matched = findMatchingAnimationItem(def, reward);
        if (matched != null) {
            return matched;
        }

        Material material = switch (reward.type()) {
            case VAULT -> Material.EMERALD;
            case PLAYERPOINTS -> Material.AMETHYST_SHARD;
            case LUCKPERMS -> Material.NETHER_STAR;
            case ITEM -> Material.CHEST;
        };

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = reward.displayName();
            if (name == null || name.isBlank()) {
                name = switch (reward.type()) {
                    case VAULT -> "&a" + formatVaultAmount(reward.vaultAmount());
                    case PLAYERPOINTS -> "&b" + formatPlayerPointsAmount(reward.playerPointsAmount());
                    case LUCKPERMS -> "&dПривилегия";
                    case ITEM -> "&fНаграда";
                };
            }
            meta.setDisplayName(color(name));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack findMatchingAnimationItem(CaseDefinition def, Reward reward) {
        if (def == null || reward == null || def.animationItems() == null) {
            return null;
        }

        String rewardName = normalizeDisplayName(reward.displayName());
        String groupName = normalizeDisplayName(reward.lpGroup());
        String nodeName = normalizeDisplayName(reward.lpNode());

        for (ItemStack item : def.animationItems()) {
            if (item == null) continue;

            String itemName = normalizeDisplayName(getDisplayName(item));
            if (matchesDisplayName(itemName, rewardName)
                    || matchesDisplayName(itemName, groupName)
                    || matchesDisplayName(itemName, nodeName)) {
                return item.clone();
            }
        }

        return null;
    }

    private boolean matchesDisplayName(String itemName, String rewardName) {
        if (itemName.length() < 2 || rewardName.length() < 2) {
            return false;
        }
        return itemName.equals(rewardName) || itemName.contains(rewardName) || rewardName.contains(itemName);
    }

    private String normalizeDisplayName(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String colored = color(value);
        String stripped = ChatColor.stripColor(colored);
        if (stripped == null) {
            stripped = colored;
        }
        return stripped.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "");
    }

    public void fillCaseGui(Inventory inv, Player p, CaseDefinition def) {
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, null);
        }
        CaseGuiLayout layout = def.guiLayout();
        fillDecor(inv, layout);
        inv.setItem(layout.openSlot(), buildGuiOpenItem(p, def));
        fillHistory(inv, def);
        if (def.fixedAnimation() == null) {
            inv.setItem(layout.animationSlot(), buildAnimationSelectorItem(p, def));
        }
    }

    private void fillDecor(Inventory inv, CaseGuiLayout layout) {
        ItemStack pane = layout.decorItem() == null ? new ItemStack(Material.GRAY_STAINED_GLASS_PANE) : layout.decorItem().clone();
        for (int slot : layout.decorSlots()) {
            if (slot >= 0 && slot < inv.getSize()) {
                inv.setItem(slot, pane.clone());
            }
        }
    }

    private void fillHistory(Inventory inv, CaseDefinition def) {
        List<OpenHistoryStorage.Entry> history = openHistoryStorage.get(def.name());
        List<Integer> historySlots = def.guiLayout().historySlots();
        for (int i = 0; i < historySlots.size(); i++) {
            int slot = historySlots.get(i);
            if (slot < 0 || slot >= inv.getSize()) continue;
            if (i < history.size()) {
                inv.setItem(slot, buildHistoryItem(def, history.get(i)));
            } else {
                inv.setItem(slot, buildEmptyHistoryItem(def));
            }
        }
    }

    private ItemStack buildHistoryItem(CaseDefinition def, OpenHistoryStorage.Entry entry) {
        ItemStack it = buildHistoryRewardItem(def, entry.rewardName());
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;

        meta.setDisplayName("§x§A§0§E§F§A§1◆ " + color(entry.rewardName()));

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§x§A§0§E§F§A§1 «Детали открытия»");
        lore.add(" §7- §fИгрок: §x§F§B§C§A§0§8" + entry.playerName());
        lore.add(" §7- §fНаграда: " + color(entry.rewardName()));
        lore.add("");
        lore.add("§x§C§0§9§6§A§B «Время»");
        lore.add(" §7- §f" + formatHistoryTime(entry.openedAt()));
        lore.add("");

        meta.setLore(lore);
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack buildHistoryRewardItem(CaseDefinition def, String rewardName) {
        if (def != null && rewardName != null) {
            String targetName = normalizeDisplayName(rewardName);
            for (Reward reward : def.rewards()) {
                String currentName = normalizeDisplayName(reward.displayName());
                if (matchesDisplayName(currentName, targetName)) {
                    return buildRewardDisplayItem(def, reward);
                }
            }
        }

        ItemStack fallback = new ItemStack(Material.CLOCK);
        ItemMeta meta = fallback.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(rewardName == null || rewardName.isBlank() ? "&fНаграда" : rewardName));
            fallback.setItemMeta(meta);
        }
        return fallback;
    }

    private ItemStack buildEmptyHistoryItem(CaseDefinition def) {
        ItemStack configured = def.guiLayout().emptyHistoryItem();
        ItemStack it = configured == null ? new ItemStack(Material.BARRIER) : configured.clone();
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;

        if (meta.hasDisplayName() || meta.hasLore()) {
            return it;
        }

        meta.setDisplayName("§8История пуста");

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§x§A§0§E§F§A§1 «История кейса»");
        lore.add(" §7- §fПоследние открытия");
        lore.add(" §7- §fбудут отображаться здесь");
        lore.add("");

        meta.setLore(lore);
        it.setItemMeta(meta);
        return it;
    }

    private String formatHistoryTime(long epochSeconds) {
        if (epochSeconds <= 0L) return "неизвестно";

        long now = System.currentTimeMillis() / 1000L;
        long diff = now - epochSeconds;

        if (diff < 60) {
            return "только что";
        }

        if (diff < 3600) {
            long minutes = diff / 60;
            return minutes + " " + getWordForm((int) minutes, "минуту", "минуты", "минут") + " назад";
        }

        if (diff < 86400) {
            long hours = diff / 3600;
            return hours + " " + getWordForm((int) hours, "час", "часа", "часов") + " назад";
        }
        long days = diff / 86400;
        if (days < 7) {
            return days + " " + getWordForm((int) days, "день", "дня", "дней") + " назад";
        }
        LocalDateTime time = LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.systemDefault());
        return HISTORY_TIME_FORMAT.format(time);
    }

    private String getWordForm(int n, String form1, String form2, String form5) {
        n = Math.abs(n) % 100;
        int n1 = n % 10;
        if (n > 10 && n < 20) return form5;
        if (n1 > 1 && n1 < 5) return form2;
        if (n1 == 1) return form1;
        return form5;
    }

    public void openCaseGui(Player p, CaseDefinition def) {
        BlockKey selected = selectedCaseBlocks.get(p.getUniqueId());
        if (selected == null && def.blockLocation() != null) {
            selectedCaseBlocks.put(p.getUniqueId(), BlockKey.of(def.blockLocation()));
        }
        Inventory inv = Bukkit.createInventory(CaseGuiHolder.caseGui(def.name()), def.guiLayout().size(), color(def.guiTitle()));
        fillCaseGui(inv, p, def);
        p.openInventory(inv);
    }

    public void openCaseGui(Player p, CaseDefinition def, Block sourceBlock) {
        if (sourceBlock != null) {
            selectedCaseBlocks.put(p.getUniqueId(), BlockKey.of(sourceBlock));
        }
        openCaseGui(p, def);
    }

    public void tryOpenCase(Player p, CaseDefinition def) {
        if (openingPlayers.contains(p.getUniqueId())) {
            p.sendMessage(plugin.getMessages().get("already-opening"));
            return;
        }
        BlockKey sourceBlock = selectedBlockKey(p, def);
        if (!tryLockCase(p, sourceBlock)) {
            p.sendMessage(plugin.getMessages().get("case-busy"));
            return;
        }
        if (!checkAndTakeCost(p, def)) {
            unlockCase(sourceBlock);
            selectedCaseBlocks.remove(p.getUniqueId());
            return;
        }

        p.closeInventory();
        openingPlayers.add(p.getUniqueId());
        runAnimationAndReward(p, def, sourceBlock);
    }

    public boolean openCasePaidByXp(Player p, CaseDefinition def, int levels) {
        if (openingPlayers.contains(p.getUniqueId()) || levels <= 0) return false;
        BlockKey sourceBlock = selectedBlockKey(p, def);
        if (!tryLockCase(p, sourceBlock)) return false;
        if (p.getLevel() < levels) {
            unlockCase(sourceBlock);
            return false;
        }

        p.setLevel(p.getLevel() - levels);
        p.closeInventory();
        openingPlayers.add(p.getUniqueId());
        runAnimationAndReward(p, def, sourceBlock);
        return true;
    }

    private boolean checkAndTakeCost(Player p, CaseDefinition def) {
        if (def.costType() == CaseDefinition.CostType.NONE) return true;

        if (def.costType() == CaseDefinition.CostType.XP_LEVELS) {
            if (p.getLevel() < def.costAmount()) {
                p.sendMessage(plugin.getMessages().get("not-enough-levels", "amount", String.valueOf(def.costAmount())));
                return false;
            }
            p.setLevel(p.getLevel() - def.costAmount());
            return true;
        }

        if (def.costType() == CaseDefinition.CostType.KEY) {
            String keyId = def.costKeyId();
            if (keyId == null || !keyExists(keyId)) {
                p.sendMessage(plugin.getMessages().get("key-not-configured"));
                return false;
            }
            int need = Math.max(1, def.costAmount());
            int have = plugin.getKeyStorage().get(p.getUniqueId(), keyId);
            if (have < need) {
                p.sendMessage(plugin.getMessages().get("not-enough-keys",
                        "need", String.valueOf(need),
                        "have", String.valueOf(have)));
                return false;
            }
            plugin.getKeyStorage().take(p.getUniqueId(), keyId, need);
            return true;
        }

        return true;
    }

    private void runAnimationAndReward(Player p, CaseDefinition def, BlockKey sourceBlock) {
        Location sourceLocation = locationFromBlockKey(sourceBlock);
        if (sourceLocation == null && def.blockLocation() != null) {
            sourceLocation = def.blockLocation();
        }
        if (sourceLocation == null) {
            openingPlayers.remove(p.getUniqueId());
            selectedCaseBlocks.remove(p.getUniqueId());
            unlockCase(sourceBlock);
            return;
        }

        Location caseBlockLocation = sourceLocation.clone();
        Location base = sourceLocation.clone().add(0.5, 0.0, 0.5);
        World w = base.getWorld();
        if (w == null) {
            openingPlayers.remove(p.getUniqueId());
            selectedCaseBlocks.remove(p.getUniqueId());
            unlockCase(sourceBlock);
            return;
        }

        var holograms = plugin.getHolograms();
        if (holograms != null) holograms.hideCase(def, caseBlockLocation);

        Reward finalReward = pickReward(def.rewards());

        AnimationType animationType = def.fixedAnimation() == null
                ? getPlayerAnimation(p.getUniqueId())
                : def.fixedAnimation();
        animationRegistry.get(animationType).play(p, def, finalReward, base, () -> {
            giveReward(p, def, finalReward);
            openingPlayers.remove(p.getUniqueId());
            selectedCaseBlocks.remove(p.getUniqueId());
            unlockCase(sourceBlock);
            if (holograms != null) holograms.showCase(def, caseBlockLocation);
        });
    }

    public void giveKey(Player p, String keyId, int amount) {
        if (amount <= 0) return;
        plugin.getKeyStorage().add(p.getUniqueId(), keyId.toLowerCase(Locale.ROOT), amount);
    }

    public String getKeyDisplayName(String keyId) {
        return keyNames.getOrDefault(keyId.toLowerCase(Locale.ROOT), keyId);
    }

    public void giveReward(Player p, Reward reward) {
        giveReward(p, null, reward);
    }

    public void giveReward(Player p, CaseDefinition def, Reward reward) {
        String rewardLabel = color(resolveRewardViewName(reward, buildRewardDisplayItem(def, reward)));
        boolean delivered = false;

        if (reward.type() == Reward.Type.ITEM) {
            ItemStack rewardItem = reward.item();
            if (rewardItem == null) {
                p.sendMessage(plugin.getMessages().getOr("reward.invalid", "reward-invalid"));
                plugin.getLogger().warning("РќРµ СѓРґР°Р»РѕСЃСЊ РІС‹РґР°С‚СЊ ITEM-РЅР°РіСЂР°РґСѓ РёРіСЂРѕРєСѓ " + p.getName() + ": item РѕС‚СЃСѓС‚СЃС‚РІСѓРµС‚.");
                return;
            }
            giveToInventoryOrDrop(p, rewardItem.clone());
            String msg = reward.message();
            if (msg != null && !msg.isBlank()) {
                p.sendMessage(formatRewardMessage(msg, rewardLabel, "", ""));
            } else {
                p.sendMessage(plugin.getMessages().get("reward-default", "reward", rewardLabel));
            }
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.28f, 1.6f);
            delivered = true;
        }

        if (reward.type() == Reward.Type.LUCKPERMS) {
            String subject = p.getUniqueId().toString();
            String dur = reward.lpDuration();
            if (reward.lpGroup() != null && !reward.lpGroup().isBlank()) {
                String cmd = (dur != null && !dur.isBlank())
                        ? "lp user " + subject + " parent addtemp " + reward.lpGroup() + " " + dur
                        : "lp user " + subject + " parent add " + reward.lpGroup();
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            }
            if (reward.lpNode() != null && !reward.lpNode().isBlank()) {
                String cmd = (dur != null && !dur.isBlank())
                        ? "lp user " + subject + " permission settemp " + reward.lpNode() + " true " + dur
                        : "lp user " + subject + " permission set " + reward.lpNode() + " true";
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            }
            String msg = reward.message();
            if (msg != null && !msg.isBlank()) {
                p.sendMessage(formatRewardMessage(msg, rewardLabel, "", ""));
            } else {
                p.sendMessage(plugin.getMessages().get("reward-luckperms", "reward", rewardLabel));
            }
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.32f, 1.2f);
            delivered = true;
        }

        if (reward.type() == Reward.Type.VAULT) {
            var economy = plugin.getVaultEconomy();
            String formattedAmount = formatVaultAmount(reward.vaultAmount());
            if (economy == null || !economy.isAvailable() || !economy.deposit(p, reward.vaultAmount())) {
                p.sendMessage(plugin.getMessages().getOr("reward.provider-missing", "reward-provider-missing",
                        "provider", "Vault"));
                plugin.getLogger().warning("Не удалось выдать Vault-награду игроку " + p.getName() + ": " + reward.vaultAmount());
                return;
            }

            String msg = reward.message();
            if (msg != null && !msg.isBlank()) {
                p.sendMessage(formatRewardMessage(msg, rewardLabel, formattedAmount, ""));
            } else {
                p.sendMessage(plugin.getMessages().getOr("reward.vault", "reward-vault",
                        "reward", rewardLabel,
                        "amount", formattedAmount));
            }
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.28f, 1.7f);
            delivered = true;
        }

        if (reward.type() == Reward.Type.PLAYERPOINTS) {
            var points = plugin.getPlayerPoints();
            String formattedAmount = formatPlayerPointsAmount(reward.playerPointsAmount());
            if (points == null || !points.isAvailable() || !points.give(p.getUniqueId(), reward.playerPointsAmount())) {
                p.sendMessage(plugin.getMessages().getOr("reward.provider-missing", "reward-provider-missing",
                        "provider", "PlayerPoints"));
                plugin.getLogger().warning("Не удалось выдать PlayerPoints-награду игроку " + p.getName() + ": " + reward.playerPointsAmount());
                return;
            }

            String msg = reward.message();
            if (msg != null && !msg.isBlank()) {
                p.sendMessage(formatRewardMessage(msg, rewardLabel, "", formattedAmount));
            } else {
                p.sendMessage(plugin.getMessages().getOr("reward.playerpoints", "reward-playerpoints",
                        "reward", rewardLabel,
                        "amount", formattedAmount));
            }
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.30f, 1.45f);
            delivered = true;
        }

        if (!delivered) {
            return;
        }

        if (def != null) {
            openHistoryStorage.add(def.name(), p.getName(), rewardLabel);
        }

        List<String> broadcast = plugin.getMessages().getList("broadcast",
                "player", p.getName(),
                "case", getCaseDisplayName(def),
                "reward", rewardLabel);
        Bukkit.getOnlinePlayers().forEach(online -> broadcast.forEach(online::sendMessage));
    }

    private void giveToInventoryOrDrop(Player p, ItemStack item) {
        Map<Integer, ItemStack> leftover = p.getInventory().addItem(item);
        if (!leftover.isEmpty()) leftover.values().forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
    }

    private Reward pickReward(List<Reward> rewards) {
        int total = rewards.stream().mapToInt(Reward::chance).sum();
        if (total <= 0) return rewards.get(0);
        int r = new Random().nextInt(total) + 1, cur = 0;
        for (Reward rw : rewards) {
            cur += rw.chance();
            if (r <= cur) return rw;
        }
        return rewards.get(rewards.size() - 1);
    }

    private ItemStack buildRewardVisualItem(Map<?, ?> rewardMap, String displayName) {
        Object itemObj = firstPresent(rewardMap, "visual", "visual_item", "visual-item", "display_item", "display-item", "item", "items");
        if (itemObj instanceof Map<?, ?> itemMap) {
            return ItemFactory.fromMap(itemMap);
        }

        if (!rewardMap.containsKey("base64") && !rewardMap.containsKey("material")) {
            return null;
        }

        Map<String, Object> visualMap = new HashMap<>();
        for (Map.Entry<?, ?> entry : rewardMap.entrySet()) {
            if (entry.getKey() == null) continue;
            visualMap.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        if (!visualMap.containsKey("name") && displayName != null && !displayName.isBlank()) {
            visualMap.put("name", displayName);
        }
        return ItemFactory.fromMap(visualMap);
    }

    private String resolveRewardDisplayName(ItemStack item, String fallback) {
        if (item == null) {
            return fallback;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName();
        }
        return fallback;
    }

    private static boolean isValidReward(int chance, Reward.Type type, ItemStack item, String lpGroup, String lpNode,
                                         double vaultAmount, int playerPointsAmount) {
        if (chance <= 0) return false;
        return switch (type) {
            case ITEM -> item != null;
            case VAULT -> vaultAmount > 0.0;
            case PLAYERPOINTS -> playerPointsAmount > 0;
            case LUCKPERMS -> (lpGroup != null && !lpGroup.isBlank()) || (lpNode != null && !lpNode.isBlank());
        };
    }

    private static Reward.Type parseRewardType(String value) {
        if (value == null) return Reward.Type.ITEM;
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "LUCKPERMS" -> Reward.Type.LUCKPERMS;
            case "VAULT", "MONEY", "ECONOMY", "ECO" -> Reward.Type.VAULT;
            case "PLAYERPOINTS", "PLAYER_POINTS", "PLAYER-POINTS", "POINTS" -> Reward.Type.PLAYERPOINTS;
            case "ITEM" -> Reward.Type.ITEM;
            default -> Reward.Type.ITEM;
        };
    }

    private static Reward.Type inferRewardType(String rawType, Map<?, ?> rewardMap) {
        Reward.Type parsed = parseRewardType(rawType);

        if (parsed == Reward.Type.ITEM && hasVaultRewardData(rewardMap)) {
            return Reward.Type.VAULT;
        }
        if (parsed == Reward.Type.ITEM && hasPlayerPointsRewardData(rewardMap)) {
            return Reward.Type.PLAYERPOINTS;
        }

        return parsed;
    }

    private static boolean hasVaultRewardData(Map<?, ?> rewardMap) {
        Map<?, ?> vaultMap = getNestedMap(rewardMap, "vault", "money", "economy", "eco");
        return firstPresent(vaultMap, rewardMap, "amount", "money", "value", "vault_amount", "vault-amount") != null;
    }

    private static boolean hasPlayerPointsRewardData(Map<?, ?> rewardMap) {
        Map<?, ?> pointsMap = getNestedMap(rewardMap, "playerpoints", "player_points", "player-points", "points");
        return firstPresent(pointsMap, rewardMap, "amount", "points", "value", "player_points", "player-points") != null;
    }

    private static Map<?, ?> getNestedMap(Map<?, ?> map, String... keys) {
        if (map == null) return null;
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof Map<?, ?> nested) {
                return nested;
            }
        }
        return null;
    }

    private static Object firstPresent(Map<?, ?> primary, Map<?, ?> fallback, String... keys) {
        Object value = firstPresent(primary, keys);
        return value != null ? value : firstPresent(fallback, keys);
    }

    private static Object firstPresent(Map<?, ?> map, String... keys) {
        if (map == null) return null;
        for (String key : keys) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
        }
        return null;
    }

    private List<CaseConfigSource> loadCaseConfigSources() {
        Map<String, CaseConfigSource> sources = new LinkedHashMap<>();

        ConfigurationSection root = plugin.getConfig().getConfigurationSection("cases");
        if (root != null) {
            for (String caseName : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(caseName);
                if (section == null) continue;
                String normalized = normalizeCaseName(caseName);
                sources.put(normalized, new CaseConfigSource(normalized, section, false));
            }
        }

        File[] files = getCaseFilesDirectory().listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            for (File file : files) {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                String fallbackName = file.getName().substring(0, file.getName().length() - 4);
                ConfigurationSection section = readCaseFileSection(yaml);
                String explicitId = getStringAlias(section, null, "id", "case_id", "case-id");
                String caseName = normalizeCaseName(explicitId == null || explicitId.isBlank() ? fallbackName : explicitId);
                sources.put(caseName, new CaseConfigSource(caseName, section, true));
            }
        }

        return new ArrayList<>(sources.values());
    }

    private ConfigurationSection readCaseFileSection(YamlConfiguration yaml) {
        ConfigurationSection nested = yaml.getConfigurationSection("case");
        return nested == null ? yaml : nested;
    }

    private WritableCaseConfig getWritableCaseConfig(String caseName) {
        String normalized = normalizeCaseName(caseName);
        File caseFile = getCaseFile(normalized);
        if (caseFile.isFile()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(caseFile);
            return new WritableCaseConfig(readCaseFileSection(yaml), yaml, caseFile, true);
        }

        ConfigurationSection cases = plugin.getConfig().getConfigurationSection("cases");
        if (cases == null) cases = plugin.getConfig().createSection("cases");

        ConfigurationSection section = cases.getConfigurationSection(normalized);
        if (section == null) section = cases.createSection(normalized);
        return new WritableCaseConfig(section, null, null, false);
    }

    private WritableCaseConfig getExistingWritableCaseConfig(String caseName) {
        String normalized = normalizeCaseName(caseName);
        File caseFile = getCaseFile(normalized);
        if (caseFile.isFile()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(caseFile);
            return new WritableCaseConfig(readCaseFileSection(yaml), yaml, caseFile, true);
        }

        ConfigurationSection cases = plugin.getConfig().getConfigurationSection("cases");
        if (cases == null || !cases.isConfigurationSection(normalized)) {
            return null;
        }
        return new WritableCaseConfig(cases.getConfigurationSection(normalized), null, null, false);
    }

    private void saveWritableCaseConfig(WritableCaseConfig writable) {
        if (writable.fileBacked()) {
            try {
                writable.yaml().save(writable.file());
            } catch (IOException e) {
                plugin.getLogger().severe("Не удалось сохранить файл кейса " + writable.file().getName() + ": " + e.getMessage());
            }
            return;
        }

        plugin.saveConfig();
    }

    private File getCaseFilesDirectory() {
        return new File(plugin.getDataFolder(), "cases");
    }

    private File getCaseFile(String caseName) {
        return new File(getCaseFilesDirectory(), normalizeCaseName(caseName) + ".yml");
    }

    private String normalizeCaseName(String caseName) {
        return (caseName == null ? "" : caseName.trim()).toLowerCase(Locale.ROOT);
    }

    private boolean isValidCaseId(String caseName) {
        return caseName != null && caseName.matches("[a-z0-9_-]{1,64}");
    }

    private void copySection(ConfigurationSection source, ConfigurationSection target) {
        for (String key : source.getKeys(false)) {
            Object value = source.get(key);
            if (value instanceof ConfigurationSection nested) {
                ConfigurationSection child = target.createSection(key);
                copySection(nested, child);
            } else {
                target.set(key, value);
            }
        }
    }

    private CaseGuiLayout readGuiLayout(ConfigurationSection gui) {
        CaseGuiLayout defaults = CaseGuiLayout.defaults();
        if (gui == null) {
            return defaults;
        }

        int size = normalizeInventorySize(getIntAlias(gui, defaults.size(), "size", "rows"));
        if (gui.contains("rows")) {
            size = normalizeInventorySize(gui.getInt("rows", 6) * 9);
        }

        int openSlot = getIntAlias(gui, defaults.openSlot(), "open_slot", "open-slot", "open_item_slot", "open-item-slot");
        int animationSlot = getIntAlias(gui, defaults.animationSlot(), "animation_slot", "animation-slot");
        int previewSlot = getIntAlias(gui, defaults.previewSlot(), "preview_slot", "preview-slot", "rewards_slot", "rewards-slot");

        ConfigurationSection decor = gui.getConfigurationSection("decor");
        List<Integer> decorSlots = readSlotList(decor, defaults.decorSlots(), "slots");
        ItemStack decorItem = readGuiItem(decor, "item", null, Material.GRAY_STAINED_GLASS_PANE, " ");

        ConfigurationSection history = gui.getConfigurationSection("history");
        List<Integer> historySlots = readSlotList(history, defaults.historySlots(), "slots");
        ItemStack emptyHistoryItem = readGuiItem(history, "empty-item", null, Material.BARRIER, "§8История пуста");

        ItemStack animationItem = readGuiItem(gui, "animation-item", null, null, null);
        ItemStack previewItem = readGuiItem(gui, "preview-item", null, null, null);

        return new CaseGuiLayout(
                size,
                clampSlot(openSlot, size, defaults.openSlot()),
                clampSlot(animationSlot, size, defaults.animationSlot()),
                clampSlot(previewSlot, size, defaults.previewSlot()),
                filterSlots(historySlots, size),
                filterSlots(decorSlots, size),
                decorItem,
                animationItem,
                previewItem,
                emptyHistoryItem
        );
    }

    private IdleParticleSettings readIdleParticles(ConfigurationSection section) {
        IdleParticleSettings defaults = IdleParticleSettings.defaults();
        if (section == null) {
            return defaults;
        }

        return new IdleParticleSettings(
                section.getBoolean("enabled", defaults.enabled()),
                section.getBoolean("effects", section.getBoolean("effects_enabled", defaults.effectsEnabled())),
                readIdleParticleStyle(section.getString("style"), defaults.style()),
                readIdleParticleTheme(section.getString("theme"), defaults.theme()),
                Math.max(2, getIntAlias(section, defaults.intervalTicks(), "interval_ticks", "interval-ticks", "period_ticks", "period-ticks")),
                clamp(getDoubleAlias(section, defaults.radius(), "radius"), 0.25, 2.50),
                clamp(getDoubleAlias(section, defaults.height(), "height"), 0.30, 3.00),
                clamp(getDoubleAlias(section, defaults.speed(), "speed"), 0.02, 0.80),
                clamp(getDoubleAlias(section, defaults.viewDistance(), "view_distance", "view-distance"), 4.0, 64.0),
                readGuiItem(section, "item", defaults.displayItem(), null, null)
        );
    }

    private IdleParticleSettings.Style readIdleParticleStyle(String raw, IdleParticleSettings.Style fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return IdleParticleSettings.Style.valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private IdleParticleSettings.Theme readIdleParticleTheme(String raw, IdleParticleSettings.Theme fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return IdleParticleSettings.Theme.valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private ItemStack readGuiItem(ConfigurationSection root, String key, ItemStack fallback, Material fallbackMaterial, String fallbackName) {
        if (root != null) {
            ConfigurationSection section = root.getConfigurationSection(key);
            ItemStack item = ItemFactory.fromSection(section);
            if (item != null) {
                return item;
            }
        }

        if (fallback != null) {
            return fallback.clone();
        }
        if (fallbackMaterial == null) {
            return null;
        }

        ItemStack item = new ItemStack(fallbackMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta != null && fallbackName != null) {
            meta.setDisplayName(color(fallbackName));
            item.setItemMeta(meta);
        }
        return item;
    }

    private List<Integer> readSlotList(ConfigurationSection section, List<Integer> fallback, String key) {
        if (section == null || !section.isList(key)) {
            return fallback;
        }

        List<Integer> slots = new ArrayList<>();
        for (Object raw : section.getList(key, Collections.emptyList())) {
            if (raw instanceof Number number) {
                slots.add(number.intValue());
                continue;
            }
            try {
                slots.add(Integer.parseInt(String.valueOf(raw)));
            } catch (NumberFormatException ignored) {
            }
        }
        return slots;
    }

    private List<Integer> filterSlots(List<Integer> slots, int size) {
        List<Integer> out = new ArrayList<>();
        for (Integer slot : slots) {
            if (slot != null && slot >= 0 && slot < size && !out.contains(slot)) {
                out.add(slot);
            }
        }
        return out;
    }

    private int normalizeInventorySize(int raw) {
        int size = raw <= 6 ? raw * 9 : raw;
        size = Math.max(9, Math.min(54, size));
        return ((size + 8) / 9) * 9;
    }

    private int clampSlot(int slot, int size, int fallback) {
        return slot >= 0 && slot < size ? slot : Math.min(fallback, size - 1);
    }

    private String getDisplayName(ItemStack item) {
        if (item == null) return "Награда";

        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName();
        }

        String raw = item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder result = new StringBuilder(raw.length());
        boolean upper = true;
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (Character.isWhitespace(ch)) {
                result.append(ch);
                upper = true;
            } else if (upper) {
                result.append(Character.toUpperCase(ch));
                upper = false;
            } else {
                result.append(ch);
            }
        }
        return result.toString();
    }

    private String resolveRewardViewName(Reward reward, ItemStack visual) {
        String configured = reward == null ? null : reward.displayName();
        String visualName = getCustomDisplayName(visual);

        if (isGenericLuckPermsName(reward, configured) && visualName != null) {
            return visualName;
        }
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        if (visualName != null) {
            return visualName;
        }
        if (reward == null) {
            return "&fНаграда";
        }

        return switch (reward.type()) {
            case VAULT -> "&a" + formatVaultAmount(reward.vaultAmount());
            case PLAYERPOINTS -> "&b" + formatPlayerPointsAmount(reward.playerPointsAmount());
            case LUCKPERMS -> "&dПривилегия";
            case ITEM -> "&fНаграда";
        };
    }

    private String getCustomDisplayName(ItemStack item) {
        if (item == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName();
        }
        return null;
    }

    private boolean isGenericLuckPermsName(Reward reward, String value) {
        if (reward == null || reward.type() != Reward.Type.LUCKPERMS || value == null || value.isBlank()) {
            return false;
        }
        String stripped = ChatColor.stripColor(color(value));
        if (stripped == null) {
            return false;
        }
        String normalized = stripped.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "");
        return normalized.equals("luckperms") || normalized.equals("привилегия");
    }

    private String formatRewardType(Reward.Type type) {
        return switch (type) {
            case ITEM -> "Предмет";
            case LUCKPERMS -> "Привилегия";
            case VAULT -> "Деньги";
            case PLAYERPOINTS -> "Поинты";
        };
    }

    private String formatChancePercent(int chance, int totalChance) {
        if (totalChance <= 0 || chance <= 0) return "0%";

        double percent = chance * 100.0D / totalChance;
        if (Math.abs(percent - Math.rint(percent)) < 0.01D) {
            return String.valueOf((int) Math.rint(percent)) + "%";
        }
        return String.format(Locale.US, "%.1f%%", percent);
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    private String formatRewardMessage(String raw, String rewardLabel, String moneyAmount, String pointsAmount) {
        String amount = !moneyAmount.isBlank() ? moneyAmount : pointsAmount;
        return color(raw
                .replace("{reward}", rewardLabel)
                .replace("{amount}", amount)
                .replace("{money}", moneyAmount)
                .replace("{points}", pointsAmount));
    }

    private String formatVaultAmount(double amount) {
        String symbol = plugin.getConfig().getString("reward-symbols.vault", "$");
        return (symbol == null ? "$" : symbol) + formatAmount(amount);
    }

    private String formatPlayerPointsAmount(int amount) {
        String symbol = plugin.getConfig().getString("reward-symbols.playerpoints", "✦");
        return (symbol == null ? "✦" : symbol) + amount;
    }

    private String getCaseDisplayName(CaseDefinition def) {
        if (def == null) return "кейс";

        String keyId = def.costKeyId();
        if (keyId != null && !keyId.isBlank()) {
            String keyName = keyNames.get(keyId.toLowerCase(Locale.ROOT));
            if (keyName != null && !keyName.isBlank()) {
                return color(keyName);
            }
        }

        String title = def.guiTitle();
        if (title != null && !title.isBlank()) {
            return color(title);
        }

        return def.name();
    }

    private static int asInt(Object o, int def) {
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return def; }
    }

    private List<Location> readBlockLocations(ConfigurationSection caseSection) {
        List<Location> locations = new ArrayList<>();
        for (Map<String, Object> block : readConfiguredBlockMaps(caseSection)) {
            Location location = readBlockLocation(block);
            if (location == null) {
                continue;
            }

            BlockKey key = BlockKey.of(location);
            boolean exists = false;
            for (Location existing : locations) {
                if (BlockKey.of(existing).equals(key)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                locations.add(location);
            }
        }
        return locations;
    }

    private Location readBlockLocation(Map<String, Object> block) {
        if (block == null) {
            return null;
        }

        String worldName = String.valueOf(block.getOrDefault("world", "")).trim();
        if (worldName.isBlank()) {
            return null;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }

        return new Location(
                world,
                asInt(block.get("x"), 0),
                asInt(block.get("y"), 0),
                asInt(block.get("z"), 0)
        );
    }

    private List<Map<String, Object>> readConfiguredBlockMaps(ConfigurationSection caseSection) {
        List<Map<String, Object>> blocks = new ArrayList<>();
        if (caseSection == null) {
            return blocks;
        }

        addBlockMap(blocks, blockMapFromSection(caseSection.getConfigurationSection("block")));

        List<?> rawBlocks = caseSection.getList("blocks", Collections.emptyList());
        for (Object raw : rawBlocks) {
            addBlockMap(blocks, blockMapFromObject(raw));
        }
        return blocks;
    }

    private void addBlockMap(List<Map<String, Object>> blocks, Map<String, Object> candidate) {
        Map<String, Object> normalized = normalizeBlockMap(candidate);
        if (normalized == null) {
            return;
        }

        String key = blockMapKey(normalized);
        for (Map<String, Object> existing : blocks) {
            if (blockMapKey(existing).equals(key)) {
                return;
            }
        }
        blocks.add(normalized);
    }

    private Map<String, Object> blockMapFromSection(ConfigurationSection section) {
        if (section == null) {
            return null;
        }

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("world", section.getString("world", ""));
        map.put("x", section.get("x"));
        map.put("y", section.get("y"));
        map.put("z", section.get("z"));
        return map;
    }

    private Map<String, Object> blockMapFromObject(Object raw) {
        if (raw instanceof ConfigurationSection section) {
            return blockMapFromSection(section);
        }
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            copy.put("world", map.get("world"));
            copy.put("x", map.get("x"));
            copy.put("y", map.get("y"));
            copy.put("z", map.get("z"));
            return copy;
        }
        return null;
    }

    private Map<String, Object> normalizeBlockMap(Map<String, Object> raw) {
        if (raw == null) {
            return null;
        }

        Object worldRaw = raw.get("world");
        String worldName = worldRaw == null ? "" : String.valueOf(worldRaw).trim();
        if (worldName.isBlank() || !raw.containsKey("x") || !raw.containsKey("y") || !raw.containsKey("z")) {
            return null;
        }

        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("world", worldName);
        normalized.put("x", asInt(raw.get("x"), 0));
        normalized.put("y", asInt(raw.get("y"), 0));
        normalized.put("z", asInt(raw.get("z"), 0));
        return normalized;
    }

    private String blockMapKey(Map<String, Object> block) {
        return String.valueOf(block.get("world")).toLowerCase(Locale.ROOT)
                + ":" + asInt(block.get("x"), 0)
                + ":" + asInt(block.get("y"), 0)
                + ":" + asInt(block.get("z"), 0);
    }

    private static AnimationType readFixedAnimation(ConfigurationSection animation) {
        String raw = getStringAlias(animation, null,
                "fixed", "fixed_animation", "fixed-animation", "case_animation", "case-animation", "type");
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (normalized.equals("PLAYER")
                || normalized.equals("PLAYERS")
                || normalized.equals("CHOICE")
                || normalized.equals("SELECT")
                || normalized.equals("NONE")
                || normalized.equals("FALSE")
                || normalized.equals("OFF")) {
            return null;
        }

        try {
            return AnimationType.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String getStringAlias(ConfigurationSection section, String def, String... keys) {
        if (section == null) return def;
        for (String key : keys) {
            if (section.contains(key)) {
                return section.getString(key, def);
            }
        }
        return def;
    }

    private static int getIntAlias(ConfigurationSection section, int def, String... keys) {
        if (section == null) return def;
        for (String key : keys) {
            if (section.contains(key)) {
                return section.getInt(key, def);
            }
        }
        return def;
    }

    private static boolean getBooleanAlias(ConfigurationSection section, boolean def, String... keys) {
        if (section == null) return def;
        for (String key : keys) {
            if (section.contains(key)) {
                return section.getBoolean(key, def);
            }
        }
        return def;
    }

    private static double asDouble(Object o, double def) {
        if (o instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(o)); } catch (Exception e) { return def; }
    }

    private static double getDoubleAlias(ConfigurationSection section, double def, String... keys) {
        if (section == null) return def;
        for (String key : keys) {
            if (section.contains(key)) {
                return section.getDouble(key, def);
            }
        }
        return def;
    }

    private static String formatAmount(double amount) {
        if (amount == Math.rint(amount)) {
            return String.valueOf((long) amount);
        }
        return String.format(Locale.US, "%.2f", amount);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public boolean isOpening(UUID playerId) { return openingPlayers.contains(playerId); }

    public boolean isCaseBusy(CaseDefinition def, UUID viewer) {
        BlockKey key = def.blockLocation() == null ? null : BlockKey.of(def.blockLocation());
        return isCaseBusy(key, viewer);
    }

    public boolean isCaseBusy(CaseDefinition def, UUID viewer, Block block) {
        BlockKey key = block == null ? null : BlockKey.of(block);
        return isCaseBusy(key, viewer);
    }

    public boolean isCaseBlockBusy(Location blockLocation) {
        return blockLocation != null && busyCases.containsKey(BlockKey.of(blockLocation));
    }

    private boolean isCaseBusy(BlockKey key, UUID viewer) {
        if (key == null) return false;
        UUID who = busyCases.get(key);
        return who != null && !who.equals(viewer);
    }

    private boolean tryLockCase(Player p, BlockKey key) {
        if (key == null) return false;
        UUID prev = busyCases.putIfAbsent(key, p.getUniqueId());
        return prev == null || prev.equals(p.getUniqueId());
    }

    private void unlockCase(BlockKey key) {
        if (key != null) {
            busyCases.remove(key);
        }
    }

    private BlockKey selectedBlockKey(Player player, CaseDefinition def) {
        BlockKey selected = selectedCaseBlocks.get(player.getUniqueId());
        if (selected != null) {
            return selected;
        }
        return def.blockLocation() == null ? null : BlockKey.of(def.blockLocation());
    }

    private Location locationFromBlockKey(BlockKey key) {
        if (key == null) {
            return null;
        }
        World world = Bukkit.getWorld(key.world());
        return world == null ? null : new Location(world, key.x(), key.y(), key.z());
    }

    private record CaseConfigSource(String name, ConfigurationSection section, boolean fileBacked) {
    }

    private record WritableCaseConfig(ConfigurationSection section, YamlConfiguration yaml, File file, boolean fileBacked) {
    }

    public record BlockKey(String world, int x, int y, int z) {
        public static BlockKey of(Block b) { return new BlockKey(b.getWorld().getName(), b.getX(), b.getY(), b.getZ()); }
        public static BlockKey of(Location l) { return new BlockKey(l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ()); }
    }
}
