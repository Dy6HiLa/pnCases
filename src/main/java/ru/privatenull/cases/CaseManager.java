package ru.privatenull.cases;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.cases.animation.AnimationRegistry;
import ru.privatenull.cases.model.AnimationType;
import ru.privatenull.cases.config.CaseBlockCodec;
import ru.privatenull.cases.config.CaseConfigRepository;
import ru.privatenull.cases.config.CaseDefinitionLoader;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.cases.model.Reward;
import ru.privatenull.cases.reward.RewardDeliveryService;
import ru.privatenull.cases.reward.RewardPresentationService;
import ru.privatenull.cases.reward.RewardSelector;
import ru.privatenull.cases.view.CaseView;
import ru.privatenull.PnCasesPlugin;
import ru.privatenull.storage.OpenHistoryStorage;
import ru.privatenull.storage.PlayerPrefsStorage;
import ru.privatenull.pnlibrary.text.ColorUtil;
import ru.privatenull.util.ServerCompatibility;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class CaseManager {

    public enum UnbindCaseResult {
        REMOVED,
        NOT_FOUND,
        NOT_BOUND
    }

    public enum CreateCaseResult {
        CREATED,
        ALREADY_EXISTS,
        INVALID_ID,
        SAVE_FAILED
    }

    private final PnCasesPlugin plugin;

    private final Map<String, CaseDefinition> casesByName = new HashMap<>();
    private final Map<BlockKey, String> caseByBlock = new HashMap<>();
    private final Map<String, ConfigurationSection> caseSections = new HashMap<>();
    private final Map<String, String> keyNames = new HashMap<>();

    private final Set<UUID> openingPlayers = ConcurrentHashMap.newKeySet();
    private final Map<BlockKey, UUID> busyCases = new ConcurrentHashMap<>();
    private final Map<UUID, BlockKey> selectedCaseBlocks = new ConcurrentHashMap<>();
    private final Map<UUID, AnimationType> playerAnimations = new ConcurrentHashMap<>();

    private final AnimationRegistry animationRegistry;
    private final CaseConfigRepository configRepository;
    private final CaseBlockCodec blockCodec;
    private final CaseDefinitionLoader definitionLoader;
    private final RewardPresentationService rewardPresentation;
    private final RewardDeliveryService rewardDelivery;
    private final RewardSelector rewardSelector;
    private CaseView caseView;
    private final IdleParticleService idleParticles;
    private final OpenHistoryStorage openHistoryStorage;
    private final PlayerPrefsStorage playerPrefs;

    public CaseManager(PnCasesPlugin plugin) {
        this.plugin = plugin;
        this.configRepository = new CaseConfigRepository(plugin);
        this.blockCodec = new CaseBlockCodec();
        this.animationRegistry = new AnimationRegistry(plugin);
        this.rewardPresentation = new RewardPresentationService(plugin);
        this.definitionLoader = new CaseDefinitionLoader(blockCodec, rewardPresentation);
        this.idleParticles = new IdleParticleService(plugin, this);
        this.openHistoryStorage = new OpenHistoryStorage(plugin.getDatabase());
        this.playerPrefs = new PlayerPrefsStorage(plugin.getDatabase());
        this.rewardDelivery = new RewardDeliveryService(plugin, openHistoryStorage, rewardPresentation);
        this.rewardSelector = new RewardSelector();
    }

    public PnCasesPlugin getPlugin() { return plugin; }

    public RewardPresentationService getRewardPresentation() { return rewardPresentation; }

    public void setCaseView(CaseView caseView) {
        this.caseView = Objects.requireNonNull(caseView, "caseView");
    }

    public List<OpenHistoryStorage.Entry> getOpenHistory(String caseName) {
        return openHistoryStorage.get(caseName);
    }

    public void shutdown() {
        animationRegistry.shutdownAll();
        idleParticles.shutdown();
        openingPlayers.clear();
        casesByName.clear();
        caseByBlock.clear();
        caseSections.clear();
        keyNames.clear();
        selectedCaseBlocks.clear();
        playerAnimations.clear();
    }

    public List<String> getCaseNames() { return new ArrayList<>(casesByName.keySet()); }
    public List<String> getConfiguredCaseNames() {
        return configRepository.configuredNames(getCaseNames());
    }

    public List<String> getBaseTemplateNames() {
        return List.of("money", "items", "playerpoints", "luckperms").stream()
                .filter(this::caseExists)
                .toList();
    }

    public String getCaseTemplate(String caseName) {
        ConfigurationSection section = getCaseSection(caseName);
        return section == null ? "custom" : section.getString("template", "custom");
    }

    public boolean applyCaseTemplate(String caseName, String templateName) {
        if (!configRepository.applyTemplate(caseName, templateName)) {
            return false;
        }
        reloadFromConfig();
        return getCaseByName(caseName) != null;
    }
    public List<String> getKeyNames()  { return new ArrayList<>(keyNames.keySet()); }
    public boolean keyExists(String keyId) {
        return keyId != null && keyNames.containsKey(keyId.toLowerCase(Locale.ROOT));
    }

    public int getLoadedCaseCount() { return casesByName.size(); }
    public int getConfiguredKeyCount() { return keyNames.size(); }

    public CaseDefinition getCaseByName(String name) {
        return name == null ? null : casesByName.get(name.toLowerCase(Locale.ROOT));
    }
    public ConfigurationSection getCaseSection(String name) {
        return name == null ? null : caseSections.get(name.toLowerCase(Locale.ROOT));
    }

    public boolean updateCaseConfig(String caseName, Consumer<ConfigurationSection> updater) {
        return updateCaseConfig(caseName, updater, true, true);
    }

    public boolean updateCaseConfig(
            String caseName,
            Consumer<ConfigurationSection> updater,
            boolean refreshHologram,
            boolean refreshShowcase
    ) {
        if (caseName == null || caseName.isBlank() || updater == null) {
            return false;
        }

        if (!configRepository.update(caseName, updater)) return false;
        return refreshCaseFromConfig(caseName, refreshHologram, refreshShowcase);
    }

    public boolean caseExists(String caseName) {
        return configRepository.exists(caseName);
    }

    public CreateCaseResult createCustomCase(String caseName) {
        CaseConfigRepository.CreateResult result = configRepository.create(caseName);
        if (result == CaseConfigRepository.CreateResult.CREATED) {
            createDefaultKey(caseName);
            reloadFromConfig();
        }
        return CreateCaseResult.valueOf(result.name());
    }

    private void createDefaultKey(String caseName) {
        String keyId = CaseConfigRepository.normalizeName(caseName) + "_key";
        ConfigurationSection keys = plugin.getConfig().getConfigurationSection("keys");
        if (keys == null) {
            keys = plugin.getConfig().createSection("keys");
        }
        if (keys.isConfigurationSection(keyId)) {
            return;
        }
        ConfigurationSection key = keys.createSection(keyId);
        key.set("name", "&fКлюч: " + caseName);
        plugin.saveConfig();
    }

    public CaseDefinition getCaseByBlock(Block block) {
        String name = caseByBlock.get(BlockKey.of(block));
        return name == null ? null : casesByName.get(name);
    }

    public AnimationRegistry getAnimationRegistry() { return animationRegistry; }

    public void exportMainCasesToFilesIfMissing() {
        configRepository.exportMainCasesIfMissing();
    }

    public AnimationType getPlayerAnimation(UUID uuid) {
        if (ServerCompatibility.useMinecraft1165AnimationMode()) {
            return AnimationType.FORTUNE_RING;
        }
        return playerAnimations.computeIfAbsent(uuid, playerPrefs::getAnimation);
    }

    public void setPlayerAnimation(UUID uuid, AnimationType type) {
        if (ServerCompatibility.useMinecraft1165AnimationMode()) {
            type = AnimationType.FORTUNE_RING;
        }
        playerAnimations.put(uuid, type);
        playerPrefs.setAnimation(uuid, type);
    }

    public void bindCaseToBlock(String caseName, Block target) {
        CaseConfigRepository.Writable writable = configRepository.writable(caseName, true);
        ConfigurationSection cs = writable.section();

        List<Map<String, Object>> blocks = blockCodec.readConfiguredBlocks(cs);
        blockCodec.addBlock(blocks, Map.of(
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

        configRepository.save(writable);
        reloadFromConfig();
    }

    public UnbindCaseResult unbindCaseFromBlock(String caseName) {
        if (caseName == null || caseName.isBlank()) {
            return UnbindCaseResult.NOT_FOUND;
        }

        String normalized = CaseConfigRepository.normalizeName(caseName);
        CaseConfigRepository.Writable writable = configRepository.writable(normalized, false);
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
        configRepository.save(writable);
        reloadFromConfig();
        return UnbindCaseResult.REMOVED;
    }

    public void reloadFromConfig() {
        var holograms = plugin.getHolograms();
        if (holograms != null) holograms.clearManaged();

        casesByName.clear();
        caseByBlock.clear();
        caseSections.clear();
        keyNames.clear();

        ConfigurationSection keysSec = plugin.getConfig().getConfigurationSection("keys");
        if (keysSec != null) {
            for (String keyId : keysSec.getKeys(false)) {
                ConfigurationSection ks = keysSec.getConfigurationSection(keyId);
                String displayName = ks != null ? ks.getString("name", keyId) : keyId;
                keyNames.put(keyId.toLowerCase(Locale.ROOT), color(displayName));
            }
        }

        List<CaseConfigRepository.Source> sources = configRepository.loadSources();
        for (CaseConfigRepository.Source source : sources) {
            ConfigurationSection section = source.section();
            if (section == null) continue;
            CaseDefinition definition = definitionLoader.load(source.name(), section);

            casesByName.put(definition.name(), definition);
            caseSections.put(definition.name(), section);
            for (Location location : definition.blockLocations()) {
                caseByBlock.put(BlockKey.of(location), definition.name());
            }
        }

        if (holograms != null) holograms.syncCases(casesByName.values());
        idleParticles.syncCases(casesByName.values());
        plugin.getLogger().info("Loaded cases: " + casesByName.size() + ", active blocks: " + caseByBlock.size() + ", keys: " + keyNames.size());
    }

    /**
     * Applies an edit made through the machine GUI without rebuilding visuals of
     * unrelated cases.  A full reload is reserved for /pncases reload and world
     * discovery, where rebuilding the complete registry is intentional.
     */
    private boolean refreshCaseFromConfig(String caseName, boolean refreshHologram, boolean refreshShowcase) {
        String normalized = CaseConfigRepository.normalizeName(caseName);
        CaseConfigRepository.Source source = configRepository.loadSources().stream()
                .filter(candidate -> normalized.equals(candidate.name()))
                .findFirst()
                .orElse(null);
        if (source == null || source.section() == null) {
            return false;
        }

        CaseDefinition previous = casesByName.get(normalized);
        CaseDefinition updated = definitionLoader.load(source.name(), source.section());

        if (previous != null) {
            for (Location location : previous.blockLocations()) {
                caseByBlock.remove(BlockKey.of(location));
            }
        }
        casesByName.put(updated.name(), updated);
        caseSections.put(updated.name(), source.section());
        for (Location location : updated.blockLocations()) {
            caseByBlock.put(BlockKey.of(location), updated.name());
        }

        if (refreshHologram) {
            var holograms = plugin.getHolograms();
            if (holograms != null) {
                holograms.refreshCase(previous, updated);
            }
        }
        if (refreshShowcase) {
            idleParticles.refreshCase(previous, updated);
        }
        return true;
    }

    public void onWorldUnload(World world) {
        idleParticles.removeWorldDisplays(world);
    }

    public ItemStack buildGuiOpenItem(Player p, CaseDefinition def) {
        return view().buildOpenButton(p, def);
    }

    public ItemStack buildAnimationSelectorItem(Player p) {
        return view().buildAnimationButton(p, null);
    }

    public ItemStack buildAnimationSelectorItem(Player p, CaseDefinition def) {
        return view().buildAnimationButton(p, def);
    }

    public ItemStack buildPreviewButton(CaseDefinition def) {
        return view().buildPreviewButton(def);
    }

    public void fillCaseGui(Inventory inv, Player p, CaseDefinition def) {
        view().fill(inv, p, def);
    }

    public void openCaseGui(Player p, CaseDefinition def) {
        BlockKey selected = selectedCaseBlocks.get(p.getUniqueId());
        if (selected == null && def.blockLocation() != null) {
            selectedCaseBlocks.put(p.getUniqueId(), BlockKey.of(def.blockLocation()));
        }
        view().open(p, def);
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

        Reward finalReward = rewardSelector.select(def.rewards());

        AnimationType animationType = def.fixedAnimation() == null
                ? getPlayerAnimation(p.getUniqueId())
                : def.fixedAnimation();
        if (ServerCompatibility.useMinecraft1165AnimationMode()) {
            animationType = AnimationType.FORTUNE_RING;
        }
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
        if (keyId == null || keyId.isBlank()) {
            return "";
        }
        return keyNames.getOrDefault(keyId.toLowerCase(Locale.ROOT), humanizeKeyName(keyId));
    }

    public void giveReward(Player p, Reward reward) {
        rewardDelivery.deliver(p, null, reward);
    }

    public void giveReward(Player p, CaseDefinition def, Reward reward) {
        rewardDelivery.deliver(p, def, reward);
    }

    private String color(String s) {
        return ColorUtil.colorize(s);
    }

    private CaseView view() {
        if (caseView == null) {
            throw new IllegalStateException("CaseView is not configured");
        }
        return caseView;
    }

    private String humanizeKeyName(String keyId) {
        String normalized = keyId == null ? "" : keyId.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "items_key", "resources_key", "tools_key" -> "&bКлюч с ресурсами";
            case "money_key" -> "&6Ключ с монетами";
            case "points_key", "playerpoints_key" -> "&bКлюч с поинтами";
            case "donate_key", "luckperms_key" -> "&6Донат ключ";
            default -> keyId == null || keyId.isBlank() ? "" : "&f" + keyId.replace('_', ' ');
        };
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

    public record BlockKey(String world, int x, int y, int z) {
        public static BlockKey of(Block b) { return new BlockKey(b.getWorld().getName(), b.getX(), b.getY(), b.getZ()); }
        public static BlockKey of(Location l) { return new BlockKey(l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ()); }
    }
}
