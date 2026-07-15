package ru.privatenull.cases.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.privatenull.cases.model.Reward;
import ru.privatenull.cases.reward.RewardPresentationService;
import ru.privatenull.util.ItemFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ru.privatenull.config.ConfigValues.decimal;
import static ru.privatenull.config.ConfigValues.integer;
import static ru.privatenull.cases.config.RewardConfigValues.firstPresent;
import static ru.privatenull.cases.config.RewardConfigValues.inferType;
import static ru.privatenull.cases.config.RewardConfigValues.nestedMap;

final class RewardConfigParser {

    private final RewardPresentationService presentation;

    RewardConfigParser(RewardPresentationService presentation) {
        this.presentation = presentation;
    }

    List<Reward> parse(ConfigurationSection caseSection) {
        List<Reward> rewards = new ArrayList<>();
        if (caseSection.isList("rewards")) {
            for (Object raw : caseSection.getList("rewards", List.of())) {
                if (!(raw instanceof Map<?, ?> map)) continue;
                Reward reward = parseReward(map);
                if (reward != null) rewards.add(reward);
            }
        }
        if (rewards.isEmpty()) {
            rewards.add(new Reward(
                    100,
                    Reward.Type.ITEM,
                    new ItemStack(Material.DIAMOND),
                    null,
                    null,
                    null,
                    "&aТы получил алмаз!",
                    "&bАлмаз",
                    Reward.Rarity.COMMON
            ));
        }
        return rewards;
    }

    private Reward parseReward(Map<?, ?> map) {
        int chance = integer(map.get("chance"), 0);
        String rawType = String.valueOf(map.containsKey("type") ? map.get("type") : "ITEM");
        Reward.Type type = inferType(rawType, map);
        if (type == null) type = Reward.Type.ITEM;
        String message = value(map.get("message"));
        Object rarity = firstPresent(map, "rarity", "rare");
        String rarityId = Reward.normalizeRarityId(rarity == null ? null : String.valueOf(rarity), chance);

        ParsedReward parsed = switch (type) {
            case ITEM -> parseItem(map);
            case LUCKPERMS -> parseLuckPerms(map);
            case VAULT -> parseVault(map);
            case PLAYERPOINTS -> parsePlayerPoints(map);
        };
        if (!isValid(chance, type, parsed)) return null;

        return new Reward(
                chance,
                type,
                parsed.item(),
                parsed.group(),
                parsed.node(),
                parsed.duration(),
                parsed.vaultAmount(),
                parsed.playerPointsAmount(),
                message,
                parsed.displayName(),
                rarityId,
                parsed.visualItem()
        );
    }

    private ParsedReward parseItem(Map<?, ?> map) {
        ItemStack item = null;
        String displayName = null;
        Object itemObject = firstPresent(map, "item", "items");
        if (itemObject instanceof Map<?, ?> itemMap) {
            item = ItemFactory.fromMap(itemMap);
            displayName = value(itemMap.get("name"));
        }
        if (displayName == null && item != null) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName()) displayName = meta.getDisplayName();
        }
        if (displayName == null && item != null) displayName = "&f" + item.getType().name();
        ItemStack visual = visualItem(map, displayName);
        if (item == null) item = visual;
        if (visual == null) visual = item;
        return new ParsedReward(item, visual, null, null, null, 0.0, 0, displayName);
    }

    private ParsedReward parseLuckPerms(Map<?, ?> map) {
        String group = null;
        String node = null;
        String duration = null;
        String displayName = null;
        Object raw = map.get("luckperms");
        if (raw instanceof Map<?, ?> luckPerms) {
            group = value(luckPerms.get("group"));
            node = value(luckPerms.get("node"));
            duration = value(luckPerms.get("duration"));
            displayName = value(firstPresent(luckPerms, map, "display_name", "display-name", "displayName", "name"));
        }
        ItemStack visual = visualItem(map, displayName);
        displayName = visualName(visual, displayName == null ? "&dПривилегия" : displayName);
        return new ParsedReward(null, visual, group, node, duration, 0.0, 0, displayName);
    }

    private ParsedReward parseVault(Map<?, ?> map) {
        Map<?, ?> vault = nestedMap(map, "vault", "money", "economy", "eco");
        double amount = decimal(firstPresent(vault, map, "amount", "money", "value"), 0.0);
        String displayName = "&a" + presentation.formatVaultAmount(amount);
        ItemStack visual = visualItem(map, displayName);
        return new ParsedReward(null, visual, null, null, null, amount, 0, visualName(visual, displayName));
    }

    private ParsedReward parsePlayerPoints(Map<?, ?> map) {
        Map<?, ?> points = nestedMap(map, "playerpoints", "player_points", "player-points", "points");
        int amount = Math.max(0, integer(firstPresent(points, map, "amount", "points", "value"), 0));
        String displayName = "&b" + presentation.formatPlayerPointsAmount(amount);
        ItemStack visual = visualItem(map, displayName);
        return new ParsedReward(null, visual, null, null, null, 0.0, amount, visualName(visual, displayName));
    }

    private ItemStack visualItem(Map<?, ?> reward, String displayName) {
        Object raw = firstPresent(reward, "visual", "visual_item", "visual-item", "display_item", "display-item",
                "icon", "icon_item", "icon-item", "item", "items");
        if (raw instanceof Map<?, ?> map) return ItemFactory.fromMap(map);
        if (raw instanceof String value && !value.isBlank()) {
            Map<String, Object> icon = new HashMap<>();
            if (looksLikeBase64(value)) {
                icon.put("base64", value);
            } else {
                icon.put("material", value);
            }
            if (displayName != null && !displayName.isBlank()) icon.put("name", displayName);
            return ItemFactory.fromMap(icon);
        }
        if (!reward.containsKey("base64") && !reward.containsKey("material")) return null;

        Map<String, Object> values = new HashMap<>();
        for (Map.Entry<?, ?> entry : reward.entrySet()) {
            if (entry.getKey() != null) values.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        if (!values.containsKey("name") && displayName != null && !displayName.isBlank()) {
            values.put("name", displayName);
        }
        return ItemFactory.fromMap(values);
    }

    private boolean looksLikeBase64(String value) {
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.startsWith("base64:") || normalized.startsWith("base64-") || normalized.startsWith("eyj");
    }

    private String visualName(ItemStack item, String fallback) {
        if (item == null) return fallback;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() ? meta.getDisplayName() : fallback;
    }

    private boolean isValid(int chance, Reward.Type type, ParsedReward parsed) {
        if (chance <= 0) return false;
        return switch (type) {
            case ITEM -> parsed.item() != null;
            case VAULT -> parsed.vaultAmount() > 0.0;
            case PLAYERPOINTS -> parsed.playerPointsAmount() > 0;
            case LUCKPERMS -> notBlank(parsed.group()) || notBlank(parsed.node());
        };
    }

    private String value(Object raw) {
        return raw == null ? null : String.valueOf(raw);
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private record ParsedReward(
            ItemStack item,
            ItemStack visualItem,
            String group,
            String node,
            String duration,
            double vaultAmount,
            int playerPointsAmount,
            String displayName
    ) {
    }
}
