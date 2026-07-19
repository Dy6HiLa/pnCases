package ru.privatenull.cases;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import ru.privatenull.cases.animation.AnimationRegistry;
import ru.privatenull.cases.animation.CaseAnimation;
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
import ru.privatenull.gui.caseview.AnimationSelectHolder;
import ru.privatenull.gui.caseview.CaseGuiHolder;
import ru.privatenull.gui.machine.MachineGuiHolder;
import ru.privatenull.PnCasesPlugin;
import ru.privatenull.storage.OpenHistoryStorage;
import ru.privatenull.storage.PendingRewardStorage;
import ru.privatenull.storage.PlayerPrefsStorage;
import ru.privatenull.pnlibrary.text.ColorUtil;
import ru.privatenull.util.ServerCompatibility;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;

public class CaseManager {

    private static final long ANIMATION_WATCHDOG_TICKS = 20L * 120L;
    private static final double SPECTATOR_REPEL_RADIUS = 5.0D;
    private static final double OPENER_REPEL_RADIUS = 3.0D;

    public enum UnbindCaseResult {
        REMOVED,
        NOT_FOUND,
        NOT_BOUND,
        SAVE_FAILED,
        REFRESH_FAILED
    }

    public enum BindCaseResult {
        BOUND,
        ALREADY_BOUND,
        NOT_FOUND,
        BLOCK_OCCUPIED,
        SAVE_FAILED,
        REFRESH_FAILED
    }

    public enum CreateCaseResult {
        CREATED,
        ALREADY_EXISTS,
        INVALID_ID,
        SAVE_FAILED,
        RELOAD_FAILED
    }

    public enum DeleteCaseResult {
        DELETED,
        NOT_FOUND,
        DELETE_FAILED,
        RELOAD_FAILED
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
    private final Map<UUID, ActiveOpening> activeOpenings = new ConcurrentHashMap<>();

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
    private final PendingRewardStorage pendingRewards;
    private final PlayerPrefsStorage playerPrefs;
    private final BukkitTask openingBarrierTask;
    private volatile boolean shuttingDown;

    public CaseManager(PnCasesPlugin plugin) {
        this.plugin = plugin;
        this.configRepository = new CaseConfigRepository(plugin);
        this.blockCodec = new CaseBlockCodec();
        this.animationRegistry = new AnimationRegistry(plugin);
        this.rewardPresentation = new RewardPresentationService(plugin);
        this.definitionLoader = new CaseDefinitionLoader(blockCodec, rewardPresentation);
        this.idleParticles = new IdleParticleService(plugin, this);
        this.openHistoryStorage = new OpenHistoryStorage(plugin.getDatabase());
        this.pendingRewards = plugin.getPendingRewards();
        this.playerPrefs = new PlayerPrefsStorage(plugin.getDatabase());
        this.rewardDelivery = new RewardDeliveryService(plugin, openHistoryStorage, rewardPresentation);
        this.rewardSelector = new RewardSelector();
        this.openingBarrierTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin, this::repelPlayersFromActiveCases, 1L, 2L);
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
        shuttingDown = true;
        openingBarrierTask.cancel();
        animationRegistry.shutdownAll();
        for (ActiveOpening opening : new ArrayList<>(activeOpenings.values())) {
            completeOpening(opening, false, null);
        }
        idleParticles.shutdown();
        openingPlayers.clear();
        busyCases.clear();
        activeOpenings.clear();
        casesByName.clear();
        caseByBlock.clear();
        caseSections.clear();
        keyNames.clear();
        selectedCaseBlocks.clear();
        playerAnimations.clear();
    }

    public List<String> getCaseNames() { return new ArrayList<>(casesByName.keySet()); }

    private void repelPlayersFromActiveCases() {
        if (activeOpenings.isEmpty()) return;
        for (ActiveOpening opening : activeOpenings.values()) {
            if (opening.completed.get() || opening.caseBlockLocation == null) continue;
            World world = Bukkit.getWorld(opening.worldId);
            if (world == null) continue;

            Location center = opening.caseBlockLocation.clone().add(0.5D, 0.0D, 0.5D);
            for (Player target : world.getPlayers()) {
                if (!target.isOnline() || target.isDead()) continue;
                double radius = target.getUniqueId().equals(opening.playerId)
                        ? OPENER_REPEL_RADIUS : SPECTATOR_REPEL_RADIUS;
                repelIfTooClose(target, center, radius);
            }
        }
    }

    private void repelIfTooClose(Player player, Location center, double radius) {
        Location location = player.getLocation();
        double dx = location.getX() - center.getX();
        double dz = location.getZ() - center.getZ();
        if (dx * dx + dz * dz >= radius * radius) return;

        Vector outward = new Vector(dx, 0.0D, dz);
        if (outward.lengthSquared() < 0.0001D) {
            outward = player.getLocation().getDirection().multiply(-1.0D);
            outward.setY(0.0D);
        }
        if (outward.lengthSquared() < 0.0001D) outward = new Vector(1.0D, 0.0D, 0.0D);
        outward.normalize().multiply(0.75D).setY(0.12D);
        player.setVelocity(outward);
        player.setFallDistance(0.0F);
    }
    public List<String> getConfiguredCaseNames() {
        return configRepository.configuredNames(getCaseNames());
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
        if (!CaseConfigRepository.isValidId(caseName)) return CreateCaseResult.INVALID_ID;
        if (configRepository.exists(caseName)) return CreateCaseResult.ALREADY_EXISTS;

        DefaultKeyChange keyChange = createDefaultKey(
                caseName,
                CaseConfigRepository.defaultDisplayName(caseName)
        );
        if (!keyChange.success()) return CreateCaseResult.SAVE_FAILED;

        CaseConfigRepository.CreateResult result = configRepository.create(caseName);
        if (result == CaseConfigRepository.CreateResult.CREATED) {
            return reloadFromConfig() ? CreateCaseResult.CREATED : CreateCaseResult.RELOAD_FAILED;
        }
        if (keyChange.created()) {
            rollbackDefaultKey(caseName);
        }
        return CreateCaseResult.valueOf(result.name());
    }

    public DeleteCaseResult deleteCase(String caseName) {
        String normalized = CaseConfigRepository.normalizeName(caseName);
        if (!CaseConfigRepository.isValidId(normalized) || !configRepository.exists(normalized)) {
            return DeleteCaseResult.NOT_FOUND;
        }
        if (!configRepository.delete(normalized)) {
            return DeleteCaseResult.DELETE_FAILED;
        }

        removeDefaultKey(normalized);
        return reloadFromConfig() ? DeleteCaseResult.DELETED : DeleteCaseResult.RELOAD_FAILED;
    }

    private DefaultKeyChange createDefaultKey(String caseName, String displayName) {
        String keyId = CaseConfigRepository.normalizeName(caseName);
        ConfigurationSection keys = plugin.getConfig().getConfigurationSection("keys");
        boolean createdKeysSection = keys == null;
        if (keys == null) {
            keys = plugin.getConfig().createSection("keys");
        }
        if (keys.contains(keyId)) {
            return new DefaultKeyChange(true, false);
        }
        ConfigurationSection key = keys.createSection(keyId);
        key.set("name", displayName);
        if (configRepository.saveMainConfig()) {
            return new DefaultKeyChange(true, true);
        }

        keys.set(keyId, null);
        if (createdKeysSection && keys.getKeys(false).isEmpty()) plugin.getConfig().set("keys", null);
        return new DefaultKeyChange(false, false);
    }

    private void rollbackDefaultKey(String caseName) {
        String keyId = CaseConfigRepository.normalizeName(caseName);
        ConfigurationSection keys = plugin.getConfig().getConfigurationSection("keys");
        if (keys == null || !keys.contains(keyId)) return;
        keys.set(keyId, null);
        if (!configRepository.saveMainConfig()) {
            plugin.getLogger().severe("Не удалось откатить ключ '" + keyId + "' после ошибки создания кейса.");
        }
    }

    private void removeDefaultKey(String caseName) {
        ConfigurationSection keys = plugin.getConfig().getConfigurationSection("keys");
        if (keys == null) return;
        String keyId = CaseConfigRepository.normalizeName(caseName);
        if (!keys.contains(keyId)) return;
        keys.set(keyId, null);
        if (!configRepository.saveMainConfig()) {
            plugin.getLogger().warning("Кейс '" + caseName + "' удалён, но ключ '" + keyId + "' не удалось удалить из config.yml.");
        }
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

    public BindCaseResult bindCaseToBlock(String caseName, Block target) {
        String normalized = CaseConfigRepository.normalizeName(caseName);
        if (target == null || !configRepository.exists(normalized)) return BindCaseResult.NOT_FOUND;

        BlockKey targetKey = BlockKey.of(target);
        String owner;
        try {
            owner = configuredOwner(targetKey, normalized);
        } catch (IllegalStateException ex) {
            plugin.getLogger().severe(ex.getMessage());
            return BindCaseResult.REFRESH_FAILED;
        }
        if (owner != null && !owner.equals(normalized)) return BindCaseResult.BLOCK_OCCUPIED;
        if (normalized.equals(owner)) return BindCaseResult.ALREADY_BOUND;

        boolean saved = configRepository.update(normalized, cs -> {
            List<Map<String, Object>> blocks = blockCodec.readConfiguredBlocks(cs);
            blockCodec.addBlock(blocks, Map.of(
                    "world", target.getWorld().getName(),
                    "x", target.getX(),
                    "y", target.getY(),
                    "z", target.getZ()
            ));
            cs.set("block", null);
            cs.set("blocks", blocks);
            ensureBoundCaseDefaults(cs, normalized);
        });
        if (!saved) return BindCaseResult.SAVE_FAILED;
        return refreshCaseFromConfig(normalized, true, true)
                ? BindCaseResult.BOUND
                : BindCaseResult.REFRESH_FAILED;
    }

    private String configuredOwner(BlockKey target, String requestedCase) {
        CaseConfigRepository.LoadResult loadResult = configRepository.loadAll();
        if (!loadResult.successful()) {
            throw new IllegalStateException("Нельзя привязать блок: в конфигах кейсов есть ошибки id, дубликаты или повреждённый YAML.");
        }

        String requestedOwner = null;
        for (CaseConfigRepository.Source source : loadResult.sources()) {
            for (Map<String, Object> configured : blockCodec.readConfiguredBlocks(source.section())) {
                BlockKey configuredKey = blockKey(configured);
                if (!target.equals(configuredKey)) continue;
                if (!source.name().equals(requestedCase)) return source.name();
                requestedOwner = source.name();
            }
        }
        return requestedOwner;
    }

    private BlockKey blockKey(Map<String, Object> configured) {
        if (configured == null) return null;
        return new BlockKey(
                String.valueOf(configured.getOrDefault("world", "")),
                integer(configured.get("x")),
                integer(configured.get("y")),
                integer(configured.get("z"))
        );
    }

    private int integer(Object value) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private void ensureBoundCaseDefaults(ConfigurationSection cs, String caseName) {
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
            ConfigurationSection animation = cs.createSection("animation");
            animation.set("duration_ticks", 80);
            animation.set("cycle_every_ticks", 2);
            animation.set("rise_blocks", 1.2);
            animation.set("spin_degrees_per_tick", 18);
            animation.set("items", List.of(
                    Map.of("material", "SLIME_BALL", "name", "&aСгусток яда"),
                    Map.of("material", "SPIDER_EYE", "name", "&2Ядовитый глаз")
            ));
        }

        if (!cs.isList("rewards")) {
            cs.set("rewards", List.of(Map.of(
                    "chance", 100,
                    "type", "ITEM",
                    "item", Map.of("material", "DIAMOND", "amount", 1, "name", "&bАлмаз")
            )));
        }
    }

    public UnbindCaseResult unbindCaseFromBlock(String caseName) {
        if (caseName == null || caseName.isBlank()) {
            return UnbindCaseResult.NOT_FOUND;
        }

        String normalized = CaseConfigRepository.normalizeName(caseName);
        CaseConfigRepository.Source source = configRepository.findSource(normalized);
        if (source == null || source.section() == null) {
            return UnbindCaseResult.NOT_FOUND;
        }

        if (blockCodec.readConfiguredBlocks(source.section()).isEmpty()) {
            return UnbindCaseResult.NOT_BOUND;
        }

        boolean saved = configRepository.update(normalized, section -> {
            section.set("block", null);
            section.set("blocks", null);
        });
        if (!saved) return UnbindCaseResult.SAVE_FAILED;
        return refreshCaseFromConfig(normalized, true, true)
                ? UnbindCaseResult.REMOVED
                : UnbindCaseResult.REFRESH_FAILED;
    }

    public boolean reloadFromConfig() {
        Map<String, CaseDefinition> loadedCases = new HashMap<>();
        Map<BlockKey, String> loadedBlocks = new HashMap<>();
        Map<BlockKey, String> configuredBlocks = new HashMap<>();
        Map<String, ConfigurationSection> loadedSections = new HashMap<>();
        Map<String, String> loadedKeys = new HashMap<>();
        ConfigurationSection keysSec = plugin.getConfig().getConfigurationSection("keys");
        if (keysSec != null) {
            for (String keyId : keysSec.getKeys(false)) {
                ConfigurationSection ks = keysSec.getConfigurationSection(keyId);
                String displayName = ks != null ? ks.getString("name", keyId) : keyId;
                loadedKeys.put(keyId.toLowerCase(Locale.ROOT), color(displayName));
            }
        }

        CaseConfigRepository.LoadResult loadResult = configRepository.loadAll();
        if (!loadResult.successful()) {
            plugin.getLogger().severe("Reload кейсов отменён: исправьте ошибки id, дубликаты или повреждённые YAML. Старые кейсы продолжают работать.");
            return false;
        }
        try {
            for (CaseConfigRepository.Source source : loadResult.sources()) {
                ConfigurationSection section = source.section();
                if (section == null) throw new IllegalStateException("Пустая секция кейса " + source.name());
                CaseDefinition definition = definitionLoader.load(source.name(), section);
                if (loadedCases.putIfAbsent(definition.name(), definition) != null) {
                    throw new IllegalStateException("Дубликат id кейса '" + definition.name() + "'.");
                }
                loadedSections.put(definition.name(), section);
                for (Map<String, Object> configured : blockCodec.readConfiguredBlocks(section)) {
                    BlockKey blockKey = blockKey(configured);
                    String previousOwner = configuredBlocks.putIfAbsent(blockKey, definition.name());
                    if (previousOwner != null && !previousOwner.equals(definition.name())) {
                        throw new IllegalStateException("Блок " + blockKey + " одновременно принадлежит кейсам '"
                                + previousOwner + "' и '" + definition.name() + "'.");
                    }
                }
                for (Location location : definition.blockLocations()) {
                    BlockKey blockKey = BlockKey.of(location);
                    String previousOwner = loadedBlocks.putIfAbsent(blockKey, definition.name());
                    if (previousOwner != null && !previousOwner.equals(definition.name())) {
                        throw new IllegalStateException("Блок " + blockKey + " одновременно принадлежит кейсам '"
                                + previousOwner + "' и '" + definition.name() + "'.");
                    }
                }
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().severe("Reload кейсов отменён: " + ex.getMessage() + " Старые кейсы продолжают работать.");
            return false;
        }

        casesByName.clear();
        casesByName.putAll(loadedCases);
        caseByBlock.clear();
        caseByBlock.putAll(loadedBlocks);
        caseSections.clear();
        caseSections.putAll(loadedSections);
        keyNames.clear();
        keyNames.putAll(loadedKeys);

        var holograms = plugin.getHolograms();
        if (holograms != null) {
            holograms.syncCases(casesByName.values());
            hideActiveOpeningHolograms(holograms, null);
        }
        idleParticles.syncCases(casesByName.values());
        closePnCasesGuis(null, true);
        plugin.getLogger().info("Loaded cases: " + casesByName.size() + ", active blocks: " + caseByBlock.size() + ", keys: " + keyNames.size());
        return true;
    }

    /**
     * Applies an edit made through the machine GUI without rebuilding visuals of
     * unrelated cases.  A full reload is reserved for /pncases reload and world
     * discovery, where rebuilding the complete registry is intentional.
     */
    private boolean refreshCaseFromConfig(String caseName, boolean refreshHologram, boolean refreshShowcase) {
        String normalized = CaseConfigRepository.normalizeName(caseName);
        CaseConfigRepository.Source source = configRepository.findSource(normalized);
        if (source == null || source.section() == null) {
            return false;
        }

        CaseDefinition previous = casesByName.get(normalized);
        CaseDefinition updated;
        try {
            updated = definitionLoader.load(source.name(), source.section());
        } catch (RuntimeException ex) {
            plugin.getLogger().severe("Не удалось обновить кейс '" + normalized + "': " + ex.getMessage());
            return false;
        }

        List<BlockKey> previousBlocks = previous == null
                ? List.of()
                : previous.blockLocations().stream().map(BlockKey::of).toList();
        List<BlockKey> newBlocks = updated.blockLocations().stream().map(BlockKey::of).toList();
        Map<BlockKey, String> updatedBlockMap = replaceOwnedBlocks(
                caseByBlock, normalized, previousBlocks, newBlocks);
        if (updatedBlockMap == null) {
            plugin.getLogger().severe("Кейс '" + normalized
                    + "' не обновлён: один из его блоков уже принадлежит другому кейсу.");
            return false;
        }

        caseByBlock.clear();
        caseByBlock.putAll(updatedBlockMap);
        casesByName.put(updated.name(), updated);
        caseSections.put(updated.name(), source.section());

        if (refreshHologram) {
            var holograms = plugin.getHolograms();
            if (holograms != null) {
                holograms.refreshCase(previous, updated);
                hideActiveOpeningHolograms(holograms, normalized);
            }
        }
        if (refreshShowcase) {
            idleParticles.refreshCase(previous, updated);
        }
        closePnCasesGuis(normalized, false);
        return true;
    }

    private void closePnCasesGuis(String caseName, boolean includeMachine) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory top = player.getOpenInventory().getTopInventory();
            Object holder = top == null ? null : top.getHolder();
            boolean matchingCaseGui = holder instanceof CaseGuiHolder caseHolder
                    && (caseName == null || caseName.equalsIgnoreCase(caseHolder.caseName()));
            boolean matchingAnimationGui = holder instanceof AnimationSelectHolder animationHolder
                    && (caseName == null || caseName.equalsIgnoreCase(animationHolder.caseName()));
            if (matchingCaseGui || matchingAnimationGui) {
                if (plugin.getGuiOpenAnimations() != null) plugin.getGuiOpenAnimations().cancel(player);
                player.closeInventory();
            } else if (includeMachine && holder instanceof MachineGuiHolder) {
                if (plugin.getGuiOpenAnimations() != null) plugin.getGuiOpenAnimations().cancel(player);
                player.closeInventory();
            }
        }
    }

    static Map<BlockKey, String> replaceOwnedBlocks(
            Map<BlockKey, String> current,
            String caseName,
            Collection<BlockKey> previous,
            Collection<BlockKey> updated
    ) {
        Map<BlockKey, String> candidate = new HashMap<>(current);
        for (BlockKey block : previous) candidate.remove(block, caseName);
        for (BlockKey block : updated) {
            String owner = candidate.putIfAbsent(block, caseName);
            if (owner != null && !owner.equals(caseName)) return null;
        }
        return candidate;
    }

    public void onWorldUnload(World world) {
        if (world == null) return;
        UUID unloadingWorldId = world.getUID();

        try {
            // Animation instances are shared across worlds. Cleanup is therefore
            // scoped by the tracked world instead of cancelling the whole type.
            animationRegistry.cancelWorld(world);
            for (ActiveOpening opening : new ArrayList<>(activeOpenings.values())) {
                if (belongsToWorld(opening.worldId, unloadingWorldId)) {
                    completeOpening(opening, true, unloadingWorldId);
                }
            }
        } finally {
            idleParticles.removeWorldDisplays(world);
        }
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
        if (selected == null || !def.name().equals(caseByBlock.get(selected))) {
            BlockKey fallback = def.blockLocation() == null ? null : BlockKey.of(def.blockLocation());
            if (fallback != null && def.name().equals(caseByBlock.get(fallback))) {
                selectedCaseBlocks.put(p.getUniqueId(), fallback);
            } else {
                selectedCaseBlocks.remove(p.getUniqueId());
            }
        }
        view().open(p, def);
    }

    public void openCaseGui(Player p, CaseDefinition def, Block sourceBlock) {
        if (sourceBlock != null) {
            BlockKey source = BlockKey.of(sourceBlock);
            if (def.name().equals(caseByBlock.get(source))) {
                selectedCaseBlocks.put(p.getUniqueId(), source);
            }
        }
        openCaseGui(p, def);
    }

    public void tryOpenCase(Player p, CaseDefinition def) {
        if (openingPlayers.contains(p.getUniqueId())) {
            p.sendMessage(plugin.getMessages().get("already-opening"));
            return;
        }
        if (!deliverPendingReward(p)) {
            p.sendMessage(plugin.getMessages().get("pending-reward-wait"));
            return;
        }
        BlockKey sourceBlock = selectedBlockKey(p, def);
        PreparedOpening prepared = prepareOpening(p, def, sourceBlock);
        if (prepared == null) {
            releaseOpeningState(p.getUniqueId(), sourceBlock);
            return;
        }
        if (!tryLockCase(p, sourceBlock)) {
            p.sendMessage(plugin.getMessages().get("case-busy"));
            return;
        }
        CostReceipt cost;
        try {
            cost = takeConfiguredCost(p, def);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Не удалось списать стоимость открытия игрока " + p.getName() + ".", exception);
            releaseOpeningState(p.getUniqueId(), sourceBlock);
            p.sendMessage(plugin.getMessages().get("case-open-storage-error"));
            return;
        }
        if (cost == null) {
            releaseOpeningState(p.getUniqueId(), sourceBlock);
            return;
        }

        try {
            p.closeInventory();
        } catch (RuntimeException exception) {
            refundCost(p, cost);
            releaseOpeningState(p.getUniqueId(), sourceBlock);
            plugin.getLogger().log(Level.WARNING,
                    "Не удалось закрыть GUI перед открытием кейса игроку " + p.getName() + ".", exception);
            return;
        }
        startAnimationAndReward(p, def, sourceBlock, prepared, cost);
    }

    public boolean openCasePaidByXp(Player p, CaseDefinition def, int levels) {
        if (openingPlayers.contains(p.getUniqueId()) || levels <= 0) return false;
        if (!deliverPendingReward(p)) {
            p.sendMessage(plugin.getMessages().get("pending-reward-wait"));
            return false;
        }
        BlockKey sourceBlock = selectedBlockKey(p, def);
        PreparedOpening prepared = prepareOpening(p, def, sourceBlock);
        if (prepared == null) {
            releaseOpeningState(p.getUniqueId(), sourceBlock);
            return false;
        }
        if (!tryLockCase(p, sourceBlock)) return false;
        int originalLevel = p.getLevel();
        if (originalLevel < levels) {
            releaseOpeningState(p.getUniqueId(), sourceBlock);
            return false;
        }

        try {
            p.setLevel(originalLevel - levels);
            p.closeInventory();
        } catch (RuntimeException exception) {
            try {
                p.setLevel(originalLevel);
            } catch (RuntimeException restoreFailure) {
                exception.addSuppressed(restoreFailure);
            }
            releaseOpeningState(p.getUniqueId(), sourceBlock);
            plugin.getLogger().log(Level.WARNING,
                    "Не удалось закрыть GUI перед XP-открытием кейса игроку " + p.getName() + ".", exception);
            return false;
        }
        return startAnimationAndReward(p, def, sourceBlock, prepared, CostReceipt.xp(levels));
    }

    private CostReceipt takeConfiguredCost(Player p, CaseDefinition def) {
        if (def.costType() == CaseDefinition.CostType.NONE) return CostReceipt.none();

        if (def.costType() == CaseDefinition.CostType.XP_LEVELS) {
            int originalLevel = p.getLevel();
            if (originalLevel < def.costAmount()) {
                p.sendMessage(plugin.getMessages().get("not-enough-levels", "amount", String.valueOf(def.costAmount())));
                return null;
            }
            try {
                p.setLevel(originalLevel - def.costAmount());
            } catch (RuntimeException exception) {
                try {
                    p.setLevel(originalLevel);
                } catch (RuntimeException restoreFailure) {
                    exception.addSuppressed(restoreFailure);
                }
                throw exception;
            }
            return CostReceipt.xp(def.costAmount());
        }

        if (def.costType() == CaseDefinition.CostType.KEY) {
            String keyId = def.costKeyId();
            if (keyId == null || !keyExists(keyId)) {
                p.sendMessage(plugin.getMessages().get("key-not-configured"));
                return null;
            }
            int need = Math.max(1, def.costAmount());
            int have = plugin.getKeyStorage().get(p.getUniqueId(), keyId);
            if (have < need) {
                p.sendMessage(plugin.getMessages().get("not-enough-keys",
                        "need", String.valueOf(need),
                        "have", String.valueOf(have)));
                return null;
            }
            if (plugin.getKeyStorage().take(p.getUniqueId(), keyId, need)) {
                return CostReceipt.key(keyId, need);
            }
            int actual = plugin.getKeyStorage().get(p.getUniqueId(), keyId);
            p.sendMessage(plugin.getMessages().get("not-enough-keys",
                    "need", String.valueOf(need),
                    "have", String.valueOf(actual)));
            return null;
        }

        return CostReceipt.none();
    }

    private PreparedOpening prepareOpening(Player p, CaseDefinition def, BlockKey sourceBlock) {
        UUID playerId = p.getUniqueId();
        if (sourceBlock == null || !def.name().equals(caseByBlock.get(sourceBlock))) {
            return null;
        }
        Location sourceLocation = locationFromBlockKey(sourceBlock);
        if (sourceLocation == null) {
            return null;
        }

        Location caseBlockLocation = sourceLocation.clone();
        Location base = sourceLocation.clone().add(0.5, 0.0, 0.5);
        World w = base.getWorld();
        if (w == null) {
            return null;
        }

        Reward finalReward;
        CaseAnimation animation;
        try {
            finalReward = rewardSelector.select(def.rewards());
            AnimationType animationType = def.fixedAnimation() == null
                    ? getPlayerAnimation(playerId)
                    : def.fixedAnimation();
            if (ServerCompatibility.useMinecraft1165AnimationMode()) {
                animationType = AnimationType.FORTUNE_RING;
            }
            animation = animationRegistry.get(animationType);
            if (animation == null) {
                throw new IllegalStateException("Animation is not registered: " + animationType);
            }
        } catch (RuntimeException | LinkageError exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Не удалось подготовить анимацию кейса '" + def.name() + "'.", exception);
            return null;
        }

        return new PreparedOpening(finalReward, animation, base, caseBlockLocation, w.getUID());
    }

    private boolean startAnimationAndReward(
            Player p,
            CaseDefinition def,
            BlockKey sourceBlock,
            PreparedOpening prepared,
            CostReceipt cost
    ) {
        UUID playerId = p.getUniqueId();
        ActiveOpening opening = new ActiveOpening(
                playerId,
                p,
                def,
                prepared.reward,
                sourceBlock,
                prepared.caseBlockLocation,
                prepared.worldId,
                prepared.animation
        );
        ActiveOpening previous = activeOpenings.putIfAbsent(playerId, opening);
        if (previous != null) {
            plugin.getLogger().severe("Повторный активный сеанс открытия для игрока " + p.getName()
                    + " был отклонён.");
            refundCost(p, cost);
            if (!Objects.equals(previous.sourceBlock, sourceBlock)) {
                selectedCaseBlocks.remove(playerId, sourceBlock);
                unlockCase(sourceBlock, playerId);
            }
            p.sendMessage(plugin.getMessages().get("already-opening"));
            return false;
        }
        openingPlayers.add(playerId);
        opening.completionCallback = () -> completeOpening(opening, true, null);

        try {
            pendingRewards.save(playerId, prepared.reward);
            opening.pendingStored = true;
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Не удалось сохранить отложенную награду игрока " + p.getName()
                            + "; анимация не будет запущена.", exception);
            boolean safelyDiscarded = discardPossiblySavedPending(playerId);
            if (safelyDiscarded) {
                refundCost(p, cost);
            }
            completeOpening(opening, false, null);
            p.sendMessage(plugin.getMessages().get(safelyDiscarded
                    ? "pending-reward-save-failed"
                    : "pending-reward-save-uncertain"));
            return false;
        }

        try {
            var holograms = plugin.getHolograms();
            if (holograms != null) {
                holograms.hideCase(def, prepared.caseBlockLocation);
                opening.hologramHidden = true;
            }
            opening.watchdog = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (opening.completed.get()) return;
                plugin.getLogger().warning("Анимация кейса '" + opening.caseName
                        + "' не завершилась вовремя; выполняется безопасная отмена.");
                cancelAnimationRun(opening, true);
            }, ANIMATION_WATCHDOG_TICKS);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Не удалось запустить окружение анимации кейса '" + def.name() + "'.", exception);
            completeOpening(opening, true, null);
            return true;
        }

        try {
            prepared.animation.play(p, def, prepared.reward, prepared.base, opening.completionCallback);
        } catch (RuntimeException | LinkageError exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Анимация кейса '" + def.name() + "' аварийно остановлена.", exception);
            cancelAnimationRun(opening, true);
        }
        return true;
    }

    /**
     * Delivers a reward left by a previous interrupted opening. A new opening
     * must never overwrite that single pending-reward slot.
     */
    public boolean deliverPendingReward(Player player) {
        if (player == null || !player.isOnline() || shuttingDown || !plugin.isEnabled()) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        // A pending row also protects the reward selected by a currently active
        // animation. Join/reload tasks must never consume that row early.
        if (pendingDeliveryBlocked(activeOpenings.containsKey(playerId), openingPlayers.contains(playerId))) {
            return false;
        }

        Reward pending;
        try {
            pending = pendingRewards.load(playerId);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Не удалось проверить отложенную награду игрока " + player.getName() + ".", exception);
            return false;
        }
        if (pending == null) return true;

        try {
            if (!giveReward(player, pending)) return false;
            pendingRewards.clear(playerId);
            return true;
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Не удалось выдать отложенную награду игроку " + player.getName() + ".", exception);
            return false;
        }
    }

    public boolean hasPendingReward(UUID playerId) {
        if (playerId == null) return false;
        try {
            return pendingRewards.load(playerId) != null;
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Не удалось проверить pending-награду игрока " + playerId + ".", exception);
            return false;
        }
    }

    static boolean pendingDeliveryBlocked(boolean activeOpening, boolean markedOpening) {
        return activeOpening || markedOpening;
    }

    static boolean belongsToWorld(UUID sessionWorldId, UUID targetWorldId) {
        return sessionWorldId != null && sessionWorldId.equals(targetWorldId);
    }

    private void cancelAnimationRun(ActiveOpening opening, boolean deliverReward) {
        if (opening == null) return;
        try {
            animationRegistry.cancelRun(opening.animation, opening.completionCallback);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось полностью отменить анимацию кейса.", exception);
        }
        completeOpening(opening, deliverReward, null);
    }

    private void completeOpening(ActiveOpening opening, boolean deliverReward, UUID unloadingWorldId) {
        if (opening == null || !opening.completed.compareAndSet(false, true)) return;

        try {
            boolean mayDeliver = deliverReward
                    && !shuttingDown
                    && plugin.isEnabled()
                    && opening.player.isOnline();
            if (mayDeliver && giveReward(opening.player, opening.definition, opening.reward)) {
                if (opening.pendingStored) {
                    pendingRewards.clear(opening.playerId);
                }
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Не удалось завершить выдачу награды игроку " + opening.player.getName()
                            + "; награда оставлена в pending storage.", exception);
        } finally {
            BukkitTask watchdog = opening.watchdog;
            if (watchdog != null) {
                try {
                    watchdog.cancel();
                } catch (RuntimeException ignored) {
                }
            }
            activeOpenings.remove(opening.playerId, opening);
            releaseOpeningState(opening.playerId, opening.sourceBlock);
            restoreOpeningHologram(opening, unloadingWorldId);
        }
    }

    private void restoreOpeningHologram(ActiveOpening opening, UUID unloadingWorldId) {
        if (!opening.hologramHidden || shuttingDown) return;
        if (unloadingWorldId != null && unloadingWorldId.equals(opening.worldId)) return;
        World loadedWorld = Bukkit.getWorld(opening.worldId);
        if (loadedWorld == null) return;

        CaseDefinition current = getCaseByName(opening.caseName);
        if (current == null || !opening.caseName.equals(caseByBlock.get(opening.sourceBlock))) return;

        var holograms = plugin.getHolograms();
        if (holograms == null) return;
        try {
            Location currentLocation = new Location(loadedWorld,
                    opening.sourceBlock.x(), opening.sourceBlock.y(), opening.sourceBlock.z());
            holograms.showCase(current, currentLocation);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "Не удалось восстановить голограмму кейса '" + opening.caseName + "'.", exception);
        }
    }

    private void hideActiveOpeningHolograms(
            ru.privatenull.hologram.HologramService holograms,
            String onlyCaseName
    ) {
        if (holograms == null) return;
        for (ActiveOpening opening : new ArrayList<>(activeOpenings.values())) {
            if (opening.completed.get() || !opening.hologramHidden) continue;
            if (onlyCaseName != null && !onlyCaseName.equals(opening.caseName)) continue;
            if (!opening.caseName.equals(caseByBlock.get(opening.sourceBlock))) continue;

            CaseDefinition current = getCaseByName(opening.caseName);
            World world = Bukkit.getWorld(opening.worldId);
            if (current == null || world == null) continue;
            Location location = new Location(world,
                    opening.sourceBlock.x(), opening.sourceBlock.y(), opening.sourceBlock.z());
            holograms.hideCase(current, location);
        }
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

    public boolean giveReward(Player p, Reward reward) {
        return rewardDelivery.deliver(p, null, reward);
    }

    public boolean giveReward(Player p, CaseDefinition def, Reward reward) {
        return rewardDelivery.deliver(p, def, reward);
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
        return keyId == null || keyId.isBlank() ? "" : "&f" + keyId.replace('_', ' ');
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

    private void unlockCase(BlockKey key, UUID owner) {
        if (key != null && owner != null) {
            busyCases.remove(key, owner);
        }
    }

    private void releaseOpeningState(UUID playerId, BlockKey sourceBlock) {
        openingPlayers.remove(playerId);
        if (sourceBlock != null) {
            selectedCaseBlocks.remove(playerId, sourceBlock);
            unlockCase(sourceBlock, playerId);
        } else {
            selectedCaseBlocks.remove(playerId);
        }
    }

    private BlockKey selectedBlockKey(Player player, CaseDefinition def) {
        BlockKey selected = selectedCaseBlocks.get(player.getUniqueId());
        if (selected != null && def.name().equals(caseByBlock.get(selected))) {
            return selected;
        }
        if (selected != null) selectedCaseBlocks.remove(player.getUniqueId(), selected);
        BlockKey fallback = def.blockLocation() == null ? null : BlockKey.of(def.blockLocation());
        return fallback != null && def.name().equals(caseByBlock.get(fallback)) ? fallback : null;
    }

    private Location locationFromBlockKey(BlockKey key) {
        if (key == null) {
            return null;
        }
        World world = Bukkit.getWorld(key.world());
        return world == null ? null : new Location(world, key.x(), key.y(), key.z());
    }

    private boolean discardPossiblySavedPending(UUID playerId) {
        try {
            pendingRewards.clear(playerId);
        } catch (RuntimeException clearFailure) {
            plugin.getLogger().log(Level.SEVERE,
                    "Не удалось очистить незавершённую pending-награду игрока " + playerId + ".",
                    clearFailure);
        }
        try {
            return pendingRewards.load(playerId) == null;
        } catch (RuntimeException verifyFailure) {
            plugin.getLogger().log(Level.SEVERE,
                    "Не удалось подтвердить очистку pending-награды игрока " + playerId + ".",
                    verifyFailure);
            return false;
        }
    }

    private void refundCost(Player player, CostReceipt receipt) {
        if (player == null || receipt == null || !receipt.refunded.compareAndSet(false, true)) return;
        try {
            switch (receipt.type) {
                case NONE -> {
                }
                case XP -> player.setLevel(player.getLevel() + receipt.amount);
                case KEY -> plugin.getKeyStorage().add(player.getUniqueId(), receipt.keyId, receipt.amount);
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Не удалось вернуть стоимость открытия игроку " + player.getName() + ".", exception);
        }
    }

    private record DefaultKeyChange(boolean success, boolean created) {
    }

    private record PreparedOpening(
            Reward reward,
            CaseAnimation animation,
            Location base,
            Location caseBlockLocation,
            UUID worldId
    ) {
    }

    private static final class CostReceipt {
        private enum Type { NONE, XP, KEY }

        private final Type type;
        private final String keyId;
        private final int amount;
        private final AtomicBoolean refunded = new AtomicBoolean();

        private CostReceipt(Type type, String keyId, int amount) {
            this.type = type;
            this.keyId = keyId;
            this.amount = Math.max(0, amount);
        }

        private static CostReceipt none() {
            return new CostReceipt(Type.NONE, null, 0);
        }

        private static CostReceipt xp(int amount) {
            return new CostReceipt(Type.XP, null, amount);
        }

        private static CostReceipt key(String keyId, int amount) {
            return new CostReceipt(Type.KEY, keyId, amount);
        }
    }

    private static final class ActiveOpening {
        private final UUID playerId;
        private final Player player;
        private final CaseDefinition definition;
        private final String caseName;
        private final Reward reward;
        private final BlockKey sourceBlock;
        private final Location caseBlockLocation;
        private final UUID worldId;
        private final CaseAnimation animation;
        private final AtomicBoolean completed = new AtomicBoolean();
        private volatile BukkitTask watchdog;
        private volatile Runnable completionCallback;
        private volatile boolean pendingStored;
        private volatile boolean hologramHidden;

        private ActiveOpening(
                UUID playerId,
                Player player,
                CaseDefinition definition,
                Reward reward,
                BlockKey sourceBlock,
                Location caseBlockLocation,
                UUID worldId,
                CaseAnimation animation
        ) {
            this.playerId = playerId;
            this.player = player;
            this.definition = definition;
            this.caseName = definition.name();
            this.reward = reward;
            this.sourceBlock = sourceBlock;
            this.caseBlockLocation = caseBlockLocation;
            this.worldId = worldId;
            this.animation = animation;
        }
    }

    public record BlockKey(String world, int x, int y, int z) {
        public BlockKey {
            world = world == null ? "" : world.toLowerCase(Locale.ROOT);
        }

        public static BlockKey of(Block b) { return new BlockKey(b.getWorld().getName(), b.getX(), b.getY(), b.getZ()); }
        public static BlockKey of(Location l) { return new BlockKey(l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ()); }
    }
}
