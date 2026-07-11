package ru.privatenull.cases.config;

import ru.privatenull.cases.model.Reward;

import java.util.Locale;
import java.util.Map;

public final class RewardConfigValues {

    private RewardConfigValues() {
    }

    public static Reward.Type inferType(String rawType, Map<?, ?> reward) {
        Reward.Type parsed = parseType(rawType);
        if (parsed == Reward.Type.ITEM && hasVaultData(reward)) return Reward.Type.VAULT;
        if (parsed == Reward.Type.ITEM && hasPlayerPointsData(reward)) return Reward.Type.PLAYERPOINTS;
        return parsed;
    }

    public static Reward.Type parseType(String rawType) {
        if (rawType == null) return Reward.Type.ITEM;
        return switch (rawType.trim().toUpperCase(Locale.ROOT)) {
            case "ITEM" -> Reward.Type.ITEM;
            case "LUCKPERMS" -> Reward.Type.LUCKPERMS;
            case "VAULT", "MONEY", "ECONOMY", "ECO" -> Reward.Type.VAULT;
            case "PLAYERPOINTS", "POINTS", "PLAYER_POINTS", "PLAYER-POINTS" -> Reward.Type.PLAYERPOINTS;
            default -> null;
        };
    }

    public static boolean hasVaultData(Map<?, ?> reward) {
        Map<?, ?> vault = nestedMap(reward, "vault", "money", "economy", "eco");
        return firstPresent(vault, reward, "amount", "money", "value", "vault_amount", "vault-amount") != null;
    }

    public static boolean hasPlayerPointsData(Map<?, ?> reward) {
        Map<?, ?> points = nestedMap(reward, "playerpoints", "player_points", "player-points", "points");
        return firstPresent(points, reward, "amount", "points", "value", "player_points", "player-points") != null;
    }

    public static Map<?, ?> nestedMap(Map<?, ?> map, String... keys) {
        if (map == null) return null;
        for (String key : keys) {
            if (map.get(key) instanceof Map<?, ?> nested) return nested;
        }
        return null;
    }

    public static Object firstPresent(Map<?, ?> primary, Map<?, ?> fallback, String... keys) {
        Object value = firstPresent(primary, keys);
        return value != null ? value : firstPresent(fallback, keys);
    }

    public static Object firstPresent(Map<?, ?> map, String... keys) {
        if (map == null) return null;
        for (String key : keys) {
            if (map.containsKey(key)) return map.get(key);
        }
        return null;
    }
}
