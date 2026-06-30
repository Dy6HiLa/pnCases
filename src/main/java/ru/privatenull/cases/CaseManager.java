package ru.privatenull.cases;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.privatenull.cases.animation.AnimationRegistry;
import ru.privatenull.cases.animation.AnimationType;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.cases.model.Reward;
import ru.privatenull.listeners.CaseGuiHolder;
import ru.privatenull.pnCases;
import ru.privatenull.storage.OpenHistoryStorage;
import ru.privatenull.storage.PlayerPrefsStorage;
import ru.privatenull.util.ItemFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CaseManager {

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

    private final pnCases plugin;

    private final Map<String, CaseDefinition> casesByName = new HashMap<>();
    private final Map<BlockKey, String> caseByBlock = new HashMap<>();
    private final Map<String, String> keyNames = new HashMap<>();

    private final Set<UUID> openingPlayers = ConcurrentHashMap.newKeySet();
    private final Map<BlockKey, UUID> busyCases = new ConcurrentHashMap<>();
    private final Map<UUID, AnimationType> playerAnimations = new ConcurrentHashMap<>();

    private final AnimationRegistry animationRegistry;
    private final OpenHistoryStorage openHistoryStorage;
    private final PlayerPrefsStorage playerPrefs;

    public CaseManager(pnCases plugin) {
        this.plugin = plugin;
        this.animationRegistry = new AnimationRegistry(plugin);
        this.openHistoryStorage = new OpenHistoryStorage(plugin.getDatabase());
        this.playerPrefs = new PlayerPrefsStorage(plugin.getDatabase());
    }

    public pnCases getPlugin() { return plugin; }

    public void shutdown() {
        animationRegistry.shutdownAll();
        openingPlayers.clear();
        casesByName.clear();
        caseByBlock.clear();
        keyNames.clear();
        playerAnimations.clear();
    }

    public List<String> getCaseNames() { return new ArrayList<>(casesByName.keySet()); }
    public List<String> getKeyNames()  { return new ArrayList<>(keyNames.keySet()); }
    public boolean keyExists(String keyId) { return keyNames.containsKey(keyId.toLowerCase()); }

    public int getLoadedCaseCount() { return casesByName.size(); }
    public int getConfiguredKeyCount() { return keyNames.size(); }

    public CaseDefinition getCaseByName(String name) { return casesByName.get(name.toLowerCase()); }

    public CaseDefinition getCaseByBlock(Block block) {
        String name = caseByBlock.get(BlockKey.of(block));
        return name == null ? null : casesByName.get(name);
    }

    public AnimationRegistry getAnimationRegistry() { return animationRegistry; }

    public AnimationType getPlayerAnimation(UUID uuid) {
        return playerAnimations.computeIfAbsent(uuid, playerPrefs::getAnimation);
    }

    public void setPlayerAnimation(UUID uuid, AnimationType type) {
        playerAnimations.put(uuid, type);
        playerPrefs.setAnimation(uuid, type);
    }

    public void bindCaseToBlock(String caseName, Block target) {
        ConfigurationSection cases = plugin.getConfig().getConfigurationSection("cases");
        if (cases == null) cases = plugin.getConfig().createSection("cases");

        ConfigurationSection cs = cases.getConfigurationSection(caseName);
        if (cs == null) cs = cases.createSection(caseName);

        ConfigurationSection blockSec = cs.getConfigurationSection("block");
        if (blockSec == null) blockSec = cs.createSection("block");

        blockSec.set("world", target.getWorld().getName());
        blockSec.set("x", target.getX());
        blockSec.set("y", target.getY());
        blockSec.set("z", target.getZ());

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

        plugin.saveConfig();
        reloadFromConfig();
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

        ConfigurationSection root = plugin.getConfig().getConfigurationSection("cases");
        if (root == null) {
            plugin.getLogger().info("Loaded cases: 0, keys: " + keyNames.size());
            return;
        }

        for (String caseName : root.getKeys(false)) {
            ConfigurationSection cs = root.getConfigurationSection(caseName);
            if (cs == null) continue;

            ConfigurationSection bs = cs.getConfigurationSection("block");
            if (bs == null) continue;
            String worldName = bs.getString("world");
            World wld = worldName == null ? null : Bukkit.getWorld(worldName);
            if (wld == null) continue;

            Location blockLoc = new Location(wld, bs.getInt("x"), bs.getInt("y"), bs.getInt("z"));

            ConfigurationSection gui = cs.getConfigurationSection("gui");
            String title = gui != null ? gui.getString("title", "&8Case") : "&8Case";
            ItemStack openBtn = ItemFactory.fromSection(gui != null ? gui.getConfigurationSection("open-item") : null);
            if (openBtn == null) openBtn = new ItemStack(Material.CHEST);

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
                buyXp = cost.getInt("buy_xp_levels", 0);
                if (buyXp <= 0) buyXp = cost.getInt("buy-xp-levels", 0);
            }

            ConfigurationSection an = cs.getConfigurationSection("animation");
            int duration = getIntAlias(an, 80, "duration_ticks", "duration-ticks");
            int cycleEvery = getIntAlias(an, 2, "cycle_every_ticks", "cycle-every-ticks");
            double rise = getDoubleAlias(an, 1.2, "rise_blocks", "rise-blocks");
            float spin = (float) getDoubleAlias(an, 18.0, "spin_degrees_per_tick", "spin-degrees-per-tick");

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
                            displayName = dn != null ? String.valueOf(dn) : "&f" + (lpGroup != null ? lpGroup : lpNode);
                        }
                        item = buildRewardVisualItem(map, displayName);
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
                    caseName.toLowerCase(Locale.ROOT), blockLoc, title, openBtn,
                    costType, costAmount, costKeyId, buyXp,
                    duration, cycleEvery, rise, spin, animItems, rewards
            );
            casesByName.put(def.name(), def);
            caseByBlock.put(BlockKey.of(blockLoc), def.name());
        }

        if (holograms != null) holograms.syncCases(casesByName.values());
        plugin.getLogger().info("Loaded cases: " + casesByName.size() + ", keys: " + keyNames.size());
    }

    public ItemStack buildGuiOpenItem(Player p, CaseDefinition def) {
        ItemStack it = def.openButton().clone();
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;

        List<String> lore = meta.hasLore()
                ? new ArrayList<>(Objects.requireNonNull(meta.getLore()))
                : new ArrayList<>();
        lore.add(" ");

        if (def.costType() == CaseDefinition.CostType.KEY) {
            String keyId = def.costKeyId();
            int need = Math.max(1, def.costAmount());
            int have = (keyId == null) ? 0 : plugin.getKeyStorage().get(p.getUniqueId(), keyId);
            lore.add(plugin.getMessages().getOr("gui.case-button.keys-balance", "gui-keys-balance",
                    "have", String.valueOf(have),
                    "need", String.valueOf(need)));
        }

        int buyExp = Math.max(0, def.buyKeyWithXpLevels());
        if (buyExp > 0) {
            lore.add(plugin.getMessages().getOr("gui.case-button.buy-xp-hint", "gui-buy-xp-hint", "levels", String.valueOf(buyExp)));
        } else {
            lore.add(plugin.getMessages().getOr("gui.case-button.buy-xp-disabled", "gui-buy-xp-disabled"));
        }
        lore.add(plugin.getMessages().getOr("gui.case-button.open-hint", "gui-open-hint"));

        meta.setLore(lore);
        it.setItemMeta(meta);
        return it;
    }

    public ItemStack buildAnimationSelectorItem(Player p) {
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
        ItemStack item = new ItemStack(Material.BOOK);
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
        ItemStack item = buildRewardDisplayItem(reward);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String rewardName = color(reward.displayName() != null ? reward.displayName() : getDisplayName(item));
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
            if (reward.lpGroup() != null && !reward.lpGroup().isBlank()) {
                lore.add(color(" §7- &fГруппа: §x§4§2§9§F§9§1" + reward.lpGroup()));
            }
            if (reward.lpNode() != null && !reward.lpNode().isBlank()) {
                lore.add(color(" §7- &fПраво: §x§4§2§9§F§9§1" + reward.lpNode()));
            }
            if (reward.lpDuration() != null && !reward.lpDuration().isBlank()) {
                lore.add(color(" §7- &fСрок: §x§4§2§9§F§9§1" + reward.lpDuration()));
            }
        }
        lore.add("");

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildRewardDisplayItem(Reward reward) {
        if (reward.visualItem() != null) {
            return reward.visualItem().clone();
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
                    case LUCKPERMS -> "&dLuckPerms";
                    case ITEM -> "&fНаграда";
                };
            }
            meta.setDisplayName(color(name));
            item.setItemMeta(meta);
        }
        return item;
    }

    public void fillCaseGui(Inventory inv, Player p, CaseDefinition def) {
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, null);
        }
        fillDecor(inv);
        inv.setItem(22, buildGuiOpenItem(p, def));
        fillHistory(inv, def);
        inv.setItem(ANIMATION_SLOT, buildAnimationSelectorItem(p));
        inv.setItem(PREVIEW_SLOT, buildPreviewButton(def));
    }

    private void fillDecor(Inventory inv) {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            pane.setItemMeta(meta);
        }

        for (int slot : DECOR_SLOTS) {
            if (slot >= 0 && slot < inv.getSize()) {
                inv.setItem(slot, pane);
            }
        }
    }

    private void fillHistory(Inventory inv, CaseDefinition def) {
        List<OpenHistoryStorage.Entry> history = openHistoryStorage.get(def.name());
        for (int i = 0; i < HISTORY_SLOTS.length; i++) {
            if (i < history.size()) {
                inv.setItem(HISTORY_SLOTS[i], buildHistoryItem(history.get(i)));
            } else {
                inv.setItem(HISTORY_SLOTS[i], buildEmptyHistoryItem());
            }
        }
    }

    private ItemStack buildHistoryItem(OpenHistoryStorage.Entry entry) {
        ItemStack it = new ItemStack(Material.BOOK);
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;

        meta.setDisplayName("§x§A§0§E§F§A§1◆ §x§F§B§C§A§0§8" + entry.playerName());

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

    private ItemStack buildEmptyHistoryItem() {
        ItemStack it = new ItemStack(Material.BARRIER);
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;

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
        Inventory inv = Bukkit.createInventory(CaseGuiHolder.caseGui(def.name()), 54, color(def.guiTitle()));
        fillCaseGui(inv, p, def);
        p.openInventory(inv);
    }

    public void tryOpenCase(Player p, CaseDefinition def) {
        if (openingPlayers.contains(p.getUniqueId())) {
            p.sendMessage(plugin.getMessages().get("already-opening"));
            return;
        }
        if (!tryLockCase(p, def)) {
            p.sendMessage(plugin.getMessages().get("case-busy"));
            return;
        }
        if (!checkAndTakeCost(p, def)) {
            unlockCase(p, def);
            return;
        }

        p.closeInventory();
        openingPlayers.add(p.getUniqueId());
        runAnimationAndReward(p, def);
    }

    public boolean openCasePaidByXp(Player p, CaseDefinition def, int levels) {
        if (openingPlayers.contains(p.getUniqueId()) || levels <= 0) return false;
        if (!tryLockCase(p, def)) return false;
        if (p.getLevel() < levels) {
            unlockCase(p, def);
            return false;
        }

        p.setLevel(p.getLevel() - levels);
        p.closeInventory();
        openingPlayers.add(p.getUniqueId());
        runAnimationAndReward(p, def);
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

    private void runAnimationAndReward(Player p, CaseDefinition def) {
        Location base = def.blockLocation().clone().add(0.5, 0.0, 0.5);
        World w = base.getWorld();
        if (w == null) {
            openingPlayers.remove(p.getUniqueId());
            return;
        }

        var holograms = plugin.getHolograms();
        if (holograms != null) holograms.hideCase(def);

        Reward finalReward = pickReward(def.rewards());

        AnimationType animationType = getPlayerAnimation(p.getUniqueId());
        animationRegistry.get(animationType).play(p, def, finalReward, base, () -> {
            giveReward(p, def, finalReward);
            openingPlayers.remove(p.getUniqueId());
            unlockCase(p, def);
            if (holograms != null) holograms.showCase(def);
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
        String rewardLabel = color(reward.displayName() != null ? reward.displayName() : "&fНаграда");
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

    private String formatRewardType(Reward.Type type) {
        return switch (type) {
            case ITEM -> "Предмет";
            case LUCKPERMS -> "LuckPerms";
            case VAULT -> "Vault";
            case PLAYERPOINTS -> "PlayerPoints";
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

    private static int getIntAlias(ConfigurationSection section, int def, String... keys) {
        if (section == null) return def;
        for (String key : keys) {
            if (section.contains(key)) {
                return section.getInt(key, def);
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

    public boolean isOpening(UUID playerId) { return openingPlayers.contains(playerId); }

    public boolean isCaseBusy(CaseDefinition def, UUID viewer) {
        UUID who = busyCases.get(BlockKey.of(def.blockLocation()));
        return who != null && !who.equals(viewer);
    }

    private boolean tryLockCase(Player p, CaseDefinition def) {
        UUID prev = busyCases.putIfAbsent(BlockKey.of(def.blockLocation()), p.getUniqueId());
        return prev == null || prev.equals(p.getUniqueId());
    }

    private void unlockCase(Player p, CaseDefinition def) {
        BlockKey k = BlockKey.of(def.blockLocation());
        UUID cur = busyCases.get(k);
        if (cur != null && cur.equals(p.getUniqueId())) busyCases.remove(k);
    }

    public record BlockKey(String world, int x, int y, int z) {
        public static BlockKey of(Block b) { return new BlockKey(b.getWorld().getName(), b.getX(), b.getY(), b.getZ()); }
        public static BlockKey of(Location l) { return new BlockKey(l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ()); }
    }
}
