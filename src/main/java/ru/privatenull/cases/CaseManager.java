package ru.privatenull.cases;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import ru.privatenull.cases.animation.AnimationRegistry;
import ru.privatenull.cases.animation.AnimationType;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.cases.model.Reward;
import ru.privatenull.listeners.CaseGuiHolder;
import ru.privatenull.pnCases;
import ru.privatenull.storage.PlayerPrefsStorage;
import ru.privatenull.util.ItemFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CaseManager {

    private final pnCases plugin;

    private final Map<String, CaseDefinition> casesByName = new HashMap<>();
    private final Map<BlockKey, String> caseByBlock = new HashMap<>();
    private final Map<String, String> keyNames = new HashMap<>();

    private final Set<UUID> openingPlayers = ConcurrentHashMap.newKeySet();
    private final Map<BlockKey, UUID> busyCases = new ConcurrentHashMap<>();
    private final Map<UUID, AnimationType> playerAnimations = new ConcurrentHashMap<>();

    private final AnimationRegistry animationRegistry;
    private final PlayerPrefsStorage playerPrefs;

    public CaseManager(pnCases plugin) {
        this.plugin = plugin;
        this.animationRegistry = new AnimationRegistry(plugin);
        this.playerPrefs = new PlayerPrefsStorage(plugin);
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

    public CaseDefinition getCaseByName(String name) { return casesByName.get(name.toLowerCase()); }

    public CaseDefinition getCaseByBlock(Block block) {
        String name = caseByBlock.get(BlockKey.of(block));
        return name == null ? null : casesByName.get(name);
    }

    public AnimationType getPlayerAnimation(UUID uuid) {
        return playerAnimations.computeIfAbsent(uuid, playerPrefs::getAnimation);
    }

    public void setPlayerAnimation(UUID uuid, AnimationType type) {
        playerAnimations.put(uuid, type);
        playerPrefs.setAnimation(uuid, type);
    }

    public AnimationRegistry getAnimationRegistry() { return animationRegistry; }

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
                    Map.of("material", "DIAMOND_PICKAXE", "name", "&bАлмазная кирка"),
                    Map.of("material", "NETHERITE_AXE", "name", "&dНезеритовый топор")
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
        var fh = plugin.getFancyHolograms();
        if (fh != null) fh.clearManaged();

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
            int duration   = an != null ? an.getInt("duration_ticks", 80) : 80;
            int cycleEvery = an != null ? an.getInt("cycle_every_ticks", 2) : 2;
            double rise    = an != null ? an.getDouble("rise_blocks", 1.2) : 1.2;
            float spin     = an != null ? (float) an.getDouble("spin_degrees_per_tick", 18) : 18f;

            List<ItemStack> animItems = new ArrayList<>();
            if (an != null && an.isList("items")) {
                List<?> raw = an.getList("items");
                if (raw != null) for (Object o : raw) {
                    if (o instanceof Map<?, ?> map) animItems.add(ItemFactory.fromMap(map));
                }
            }
            animItems.removeIf(Objects::isNull);
            if (animItems.isEmpty()) animItems.add(new ItemStack(Material.DIAMOND));

            List<Reward> rewards = new ArrayList<>();
            if (cs.isList("rewards")) {
                List<?> rawRewards = cs.getList("rewards");
                if (rawRewards != null) for (Object rr : rawRewards) {
                    if (!(rr instanceof Map<?, ?> map)) continue;
                    int chance = asInt(map.get("chance"), 0);
                    String typeS = String.valueOf(map.containsKey("type") ? map.get("type") : "ITEM");
                    Reward.Type rType;
                    try { rType = Reward.Type.valueOf(typeS.toUpperCase(Locale.ROOT)); }
                    catch (Exception e) { rType = Reward.Type.ITEM; }

                    String message     = map.containsKey("message") ? String.valueOf(map.get("message")) : null;
                    String displayName = null;
                    ItemStack item     = null;
                    String lpGroup = null, lpNode = null, lpDuration = null;

                    if (rType == Reward.Type.ITEM) {
                        Object itemObj = map.get("item");
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
                            if (g != null) lpGroup    = String.valueOf(g);
                            if (n != null) lpNode     = String.valueOf(n);
                            if (d != null) lpDuration = String.valueOf(d);
                            Object dn = lpMap.get("display_name");
                            displayName = dn != null ? String.valueOf(dn) : "&f" + (lpGroup != null ? lpGroup : lpNode);
                        }
                    }

                    if (chance > 0 && (rType != Reward.Type.ITEM || item != null)) {
                        rewards.add(new Reward(chance, rType, item, lpGroup, lpNode, lpDuration, message, displayName));
                    }
                }
            }
            if (rewards.isEmpty()) {
                rewards.add(new Reward(100, Reward.Type.ITEM, new ItemStack(Material.DIAMOND),
                        null, null, null, "&aТы получил алмаз!", "&bАлмаз"));
            }

            CaseDefinition def = new CaseDefinition(
                    caseName.toLowerCase(Locale.ROOT), blockLoc, title, openBtn,
                    costType, costAmount, costKeyId, buyXp,
                    duration, cycleEvery, rise, spin, animItems, rewards
            );
            casesByName.put(def.name(), def);
            caseByBlock.put(BlockKey.of(blockLoc), def.name());
        }

        if (fh != null) fh.syncCases(casesByName.values());
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
            lore.add(plugin.getMessages().get("gui-keys-balance",
                    "have", String.valueOf(have),
                    "need", String.valueOf(need)));
        }

        int buyExp = Math.max(0, def.buyKeyWithXpLevels());
        if (buyExp > 0) {
            lore.add(plugin.getMessages().get("gui-buy-xp-hint", "levels", String.valueOf(buyExp)));
        } else {
            lore.add(plugin.getMessages().get("gui-buy-xp-disabled"));
        }
        lore.add(plugin.getMessages().get("gui-open-hint"));

        meta.setLore(lore);
        it.setItemMeta(meta);
        return it;
    }

    public ItemStack buildAnimationSelectorItem(Player p) {
        AnimationType current = getPlayerAnimation(p.getUniqueId());
        ItemStack it = new ItemStack(current.icon());
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color("&bАнимация: " + current.displayName()));
            List<String> lore = new ArrayList<>();
            lore.add(" ");
            lore.add(color("&7" + current.description().replace("\n", "\n&7")));
            lore.add(" ");
            lore.add(color("&7Нажми, чтобы сменить анимацию"));
            meta.setLore(lore);
            it.setItemMeta(meta);
        }
        return it;
    }

    public void openCaseGui(Player p, CaseDefinition def) {
        Inventory inv = Bukkit.createInventory(new CaseGuiHolder(def.name()), 54, color(def.guiTitle()));
        inv.setItem(22, buildGuiOpenItem(p, def));
        inv.setItem(49, buildAnimationSelectorItem(p));
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
        if (!checkAndTakeCost(p, def)) { unlockCase(p, def); return; }

        p.closeInventory();
        openingPlayers.add(p.getUniqueId());
        runAnimationAndReward(p, def);
    }

    public boolean openCasePaidByXp(Player p, CaseDefinition def, int levels) {
        if (openingPlayers.contains(p.getUniqueId()) || levels <= 0) return false;
        if (!tryLockCase(p, def)) return false;
        if (p.getLevel() < levels) { unlockCase(p, def); return false; }

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
        if (w == null) { openingPlayers.remove(p.getUniqueId()); return; }

        var fh = plugin.getFancyHolograms();
        if (fh != null) fh.hideCase(def);

        Reward finalReward = pickReward(def.rewards());
        AnimationType animType = getPlayerAnimation(p.getUniqueId());

        animationRegistry.get(animType).play(p, def, finalReward, base, () -> {
            giveReward(p, finalReward);
            openingPlayers.remove(p.getUniqueId());
            unlockCase(p, def);
            if (fh != null) fh.showCase(def);
        });
    }

    public void giveKey(Player p, String keyId, int amount) {
        if (amount <= 0) return;
        plugin.getKeyStorage().add(p.getUniqueId(), keyId.toLowerCase(Locale.ROOT), amount);
    }

    public String getKeyDisplayName(String keyId) {
        return keyNames.getOrDefault(keyId.toLowerCase(Locale.ROOT), keyId);
    }

    private void giveReward(Player p, Reward reward) {
        String rewardLabel = color(reward.displayName() != null ? reward.displayName() : "&fНаграда");

        if (reward.type() == Reward.Type.ITEM) {
            giveToInventoryOrDrop(p, reward.item().clone());
            String msg = reward.message();
            if (msg != null && !msg.isBlank()) {
                p.sendMessage(color(msg));
            } else {
                p.sendMessage(plugin.getMessages().get("reward-default", "reward", rewardLabel));
            }
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
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
                p.sendMessage(color(msg));
            } else {
                p.sendMessage(plugin.getMessages().get("reward-luckperms", "reward", rewardLabel));
            }
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.9f, 1.2f);
        }

        List<String> broadcast = plugin.getMessages().getList("broadcast",
                "player", p.getName(),
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
        for (Reward rw : rewards) { cur += rw.chance(); if (r <= cur) return rw; }
        return rewards.get(rewards.size() - 1);
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    private static int asInt(Object o, int def) {
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return def; }
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
        public static BlockKey of(Block b)    { return new BlockKey(b.getWorld().getName(), b.getX(), b.getY(), b.getZ()); }
        public static BlockKey of(Location l) { return new BlockKey(l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ()); }
    }
}