package ru.privatenull.cases.animation;

import org.bukkit.Location;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.cases.model.Reward;
import ru.privatenull.pnlibrary.text.ColorUtil;
import ru.privatenull.util.EntityCleanup;
import ru.privatenull.PnCasesPlugin;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public abstract class CaseAnimation {

    protected final PnCasesPlugin plugin;

    private final Set<Entity> trackedEntities = ConcurrentHashMap.newKeySet();
    private final Set<BukkitTask> trackedTasks = ConcurrentHashMap.newKeySet();
    private final Set<Listener> trackedListeners = ConcurrentHashMap.newKeySet();
    private final Map<BukkitTask, UUID> taskWorlds = new ConcurrentHashMap<>();
    private final Map<Listener, UUID> listenerWorlds = new ConcurrentHashMap<>();
    private final Map<Entity, Runnable> entityOwners = new ConcurrentHashMap<>();
    private final Map<BukkitTask, Runnable> taskOwners = new ConcurrentHashMap<>();
    private final Map<Listener, Runnable> listenerOwners = new ConcurrentHashMap<>();

    protected CaseAnimation(PnCasesPlugin plugin) {
        this.plugin = plugin;
    }

    public abstract void play(Player player, CaseDefinition def, Reward reward, Location base, Runnable onFinish);

    /**
     * Gives animations a chance to restore temporary world state before Bukkit
     * unloads the world. Entity/task cleanup remains owned by each animation.
     */
    public void onWorldUnload(World world) {
    }

    protected void track(Entity e) {
        if (e != null) trackedEntities.add(e);
    }

    protected void track(Entity entity, Runnable owner) {
        track(entity);
        if (entity != null && owner != null) entityOwners.put(entity, owner);
    }

    protected void track(BukkitTask t) {
        if (t != null) trackedTasks.add(t);
    }

    protected void track(BukkitTask task, World world) {
        track(task);
        if (task != null && world != null) taskWorlds.put(task, world.getUID());
    }

    protected void track(BukkitTask task, World world, Runnable owner) {
        track(task, world);
        if (task != null && owner != null) taskOwners.put(task, owner);
    }

    protected void untrack(Entity e) {
        trackedEntities.remove(e);
        entityOwners.remove(e);
    }

    protected void untrack(BukkitTask t) {
        trackedTasks.remove(t);
        taskWorlds.remove(t);
        taskOwners.remove(t);
    }

    /**
     * Tracks a listener whose lifetime is limited to one or more active runs of
     * this animation.  Global listeners should instead be unregistered from
     * {@link #shutdown()}.
     */
    protected void track(Listener listener) {
        if (listener != null) trackedListeners.add(listener);
    }

    protected void track(Listener listener, World world) {
        track(listener);
        if (listener != null && world != null) listenerWorlds.put(listener, world.getUID());
    }

    protected void track(Listener listener, World world, Runnable owner) {
        track(listener, world);
        if (listener != null && owner != null) listenerOwners.put(listener, owner);
    }

    protected void unregister(Listener listener) {
        if (listener == null) return;
        trackedListeners.remove(listener);
        listenerWorlds.remove(listener);
        listenerOwners.remove(listener);
        HandlerList.unregisterAll(listener);
    }

    /** Cancels exactly one play invocation without touching sibling runs. */
    public void cancelRun(Runnable owner) {
        if (owner == null) return;
        onCancelRun(owner);

        for (Listener listener : trackedListeners) {
            if (listenerOwners.get(listener) == owner) unregister(listener);
        }
        for (BukkitTask task : trackedTasks) {
            if (taskOwners.get(task) != owner) continue;
            try {
                task.cancel();
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.FINE, "Could not cancel animation run task", exception);
            }
            untrack(task);
        }
        for (Entity entity : trackedEntities) {
            if (entityOwners.get(entity) != owner) continue;
            EntityCleanup.remove(entity);
            untrack(entity);
        }
    }

    protected void onCancelRun(Runnable owner) {
    }

    /** Cancels only runs owned by the unloading world. */
    public void cancelWorld(World world) {
        if (world == null) return;
        UUID worldId = world.getUID();

        onWorldUnload(world);

        for (Listener listener : trackedListeners) {
            if (worldId.equals(listenerWorlds.get(listener))) unregister(listener);
        }
        for (BukkitTask task : trackedTasks) {
            if (!worldId.equals(taskWorlds.get(task))) continue;
            try {
                task.cancel();
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.FINE, "Could not cancel world animation task", exception);
            }
            untrack(task);
        }
        for (Entity entity : trackedEntities) {
            try {
                if (entity.getWorld().getUID().equals(worldId)) {
                    EntityCleanup.remove(entity);
                    untrack(entity);
                }
            } catch (RuntimeException exception) {
                // Invalid entities from an unloading world are safe to forget.
                untrack(entity);
            }
        }
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
        for (Listener listener : trackedListeners) {
            unregister(listener);
        }
        trackedListeners.clear();

        for (BukkitTask task : trackedTasks) {
            try {
                task.cancel();
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.FINE, "Could not cancel animation task", exception);
            }
        }
        trackedTasks.clear();
        taskWorlds.clear();
        taskOwners.clear();

        for (Entity e : trackedEntities) {
            EntityCleanup.remove(e);
        }
        trackedEntities.clear();
        listenerWorlds.clear();
        entityOwners.clear();
        listenerOwners.clear();
    }

    /**
     * Permanent plugin shutdown hook. Runtime cancellation (for example a
     * world unload) must leave reusable animation listeners registered.
     */
    public void shutdown() {
        cancelAll();
    }
}
