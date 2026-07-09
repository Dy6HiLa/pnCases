package ru.privatenull.cases.animation;

import org.bukkit.Location;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.cases.model.Reward;
import ru.privatenull.util.ColorUtil;
import ru.privatenull.pnCases;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public abstract class CaseAnimation {

    protected final pnCases plugin;

    private final Set<Entity> trackedEntities = ConcurrentHashMap.newKeySet();
    private final Set<BukkitTask> trackedTasks = ConcurrentHashMap.newKeySet();

    protected CaseAnimation(pnCases plugin) {
        this.plugin = plugin;
    }

    public abstract void play(Player player, CaseDefinition def, Reward reward, Location base, Runnable onFinish);

    protected void track(Entity e) {
        if (e != null) trackedEntities.add(e);
    }

    protected void track(BukkitTask t) {
        if (t != null) trackedTasks.add(t);
    }

    protected void untrack(Entity e) {
        trackedEntities.remove(e);
    }

    protected void untrack(BukkitTask t) {
        trackedTasks.remove(t);
    }

    protected ItemStack resolveRewardVisual(Reward reward, CaseDefinition def) {
        if (reward != null && reward.visualItem() != null) {
            return reward.visualItem().clone();
        }

        ItemStack matched = findMatchingAnimationItem(reward, def);
        if (matched != null) {
            return matched;
        }

        return buildFallbackRewardVisual(reward);
    }

    protected String resolveRewardName(Reward reward, ItemStack visual) {
        String name = reward == null ? null : reward.displayName();
        String visualName = getCustomDisplayName(visual);
        if (isGenericLuckPermsName(reward, name) && visualName != null) {
            name = visualName;
        }
        if ((name == null || name.isBlank()) && visual != null) {
            name = visualName;
        }
        if (name == null || name.isBlank()) {
            name = visual != null ? "&f" + visual.getType().name() : "&fНаграда";
        }
        return ColorUtil.colorize(name);
    }

    private ItemStack findMatchingAnimationItem(Reward reward, CaseDefinition def) {
        if (reward == null || def == null || def.animationItems() == null) {
            return null;
        }

        String rewardName = normalizeName(reward.displayName());
        String groupName = normalizeName(reward.lpGroup());
        String nodeName = normalizeName(reward.lpNode());

        for (ItemStack item : def.animationItems()) {
            if (item == null) continue;

            String itemName = normalizeName(getDisplayName(item));
            if (matchesName(itemName, rewardName) || matchesName(itemName, groupName) || matchesName(itemName, nodeName)) {
                return item.clone();
            }
        }

        return null;
    }

    private ItemStack buildFallbackRewardVisual(Reward reward) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(resolveRewardName(reward, null));
            item.setItemMeta(meta);
        }
        return item;
    }

    private String getDisplayName(ItemStack item) {
        String customName = getCustomDisplayName(item);
        if (customName != null) {
            return customName;
        }
        return item.getType().name();
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
        String stripped = ChatColor.stripColor(ColorUtil.colorize(value));
        if (stripped == null) {
            return false;
        }
        String normalized = stripped.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "");
        return normalized.equals("luckperms") || normalized.equals("привилегия");
    }

    private boolean matchesName(String itemName, String rewardName) {
        if (itemName.length() < 2 || rewardName.length() < 2) {
            return false;
        }
        return itemName.equals(rewardName) || itemName.contains(rewardName) || rewardName.contains(itemName);
    }

    private String normalizeName(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String colored = ColorUtil.colorize(value);
        String stripped = ChatColor.stripColor(colored);
        if (stripped == null) {
            stripped = colored;
        }
        return stripped.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "");
    }

    public void cancelAll() {
        for (BukkitTask task : trackedTasks) {
            try { task.cancel(); } catch (Exception ignored) {}
        }
        trackedTasks.clear();

        for (Entity e : trackedEntities) {
            try { if (!e.isDead()) e.remove(); } catch (Exception ignored) {}
        }
        trackedEntities.clear();
    }
}
