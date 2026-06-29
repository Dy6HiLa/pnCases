package ru.privatenull.config;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ConfigValidator {

    private ConfigValidator() {
    }

    public static Result validateAndPatch(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<String> patches = new ArrayList<>();

        boolean changed = patchMissing(config, patches, "reward-symbols.vault", "$");
        changed |= patchMissing(config, patches, "reward-symbols.playerpoints", "✦");

        Set<String> knownKeys = validateKeys(config, warnings, errors);
        validateCases(config, knownKeys, warnings, errors);

        if (changed) {
            plugin.saveConfig();
        }

        if (!patches.isEmpty()) {
            plugin.getLogger().info("Config validation: добавлены недостающие поля старого config.yml: " + String.join(", ", patches));
        }
        for (String warning : warnings) {
            plugin.getLogger().warning("Config warning: " + warning);
        }
        for (String error : errors) {
            plugin.getLogger().severe("Config error: " + error);
        }

        if (warnings.isEmpty() && errors.isEmpty()) {
            plugin.getLogger().info("Config validation: config.yml корректен.");
        } else {
            plugin.getLogger().warning("Config validation: найдено ошибок: " + errors.size() + ", предупреждений: " + warnings.size() + ".");
        }

        return new Result(errors.size(), warnings.size(), patches.size());
    }

    private static boolean patchMissing(FileConfiguration config, List<String> patches, String path, Object value) {
        if (config.contains(path)) {
            return false;
        }
        config.set(path, value);
        patches.add(path);
        return true;
    }

    private static Set<String> validateKeys(FileConfiguration config, List<String> warnings, List<String> errors) {
        Set<String> knownKeys = new HashSet<>();
        ConfigurationSection keys = config.getConfigurationSection("keys");
        if (keys == null) {
            errors.add("Секция keys отсутствует. Кейсы с cost.type: KEY не смогут открываться.");
            return knownKeys;
        }

        for (String keyId : keys.getKeys(false)) {
            knownKeys.add(keyId.toLowerCase(Locale.ROOT));
            ConfigurationSection key = keys.getConfigurationSection(keyId);
            if (key == null) {
                warnings.add("keys." + keyId + " должен быть секцией с name, например: name: '&aКлюч'.");
                continue;
            }
            if (!key.isString("name")) {
                warnings.add("keys." + keyId + ".name отсутствует. В игре будет показан технический id ключа.");
            }
        }
        return knownKeys;
    }

    private static void validateCases(FileConfiguration config, Set<String> knownKeys, List<String> warnings, List<String> errors) {
        ConfigurationSection cases = config.getConfigurationSection("cases");
        if (cases == null) {
            errors.add("Секция cases отсутствует. Плагин не загрузит ни одного кейса.");
            return;
        }

        for (String caseName : cases.getKeys(false)) {
            ConfigurationSection section = cases.getConfigurationSection(caseName);
            String path = "cases." + caseName;
            if (section == null) {
                errors.add(path + " должен быть секцией.");
                continue;
            }

            validateBlock(path, section, errors);
            validateGui(path, section, warnings);
            validateCost(path, section, knownKeys, warnings, errors);
            validateAnimation(path, section, warnings);
            validateRewards(path, section, warnings, errors);
        }
    }

    private static void validateBlock(String path, ConfigurationSection section, List<String> errors) {
        ConfigurationSection block = section.getConfigurationSection("block");
        if (block == null) {
            errors.add(path + ".block отсутствует. Кейс будет пропущен.");
            return;
        }

        String world = block.getString("world");
        if (world == null || world.isBlank()) {
            errors.add(path + ".block.world отсутствует.");
        } else if (Bukkit.getWorld(world) == null) {
            errors.add(path + ".block.world = '" + world + "' не найден на сервере. Кейс загрузится только когда мир существует.");
        }

        requireInt(block, path + ".block", "x", errors);
        requireInt(block, path + ".block", "y", errors);
        requireInt(block, path + ".block", "z", errors);
    }

    private static void validateGui(String path, ConfigurationSection section, List<String> warnings) {
        ConfigurationSection gui = section.getConfigurationSection("gui");
        if (gui == null) {
            warnings.add(path + ".gui отсутствует. Будет использована стандартная кнопка CHEST.");
            return;
        }

        ConfigurationSection item = gui.getConfigurationSection("open-item");
        if (item == null) {
            warnings.add(path + ".gui.open-item отсутствует. Будет использована стандартная кнопка CHEST.");
            return;
        }

        validateMaterial(path + ".gui.open-item", item, warnings);
    }

    private static void validateCost(String path, ConfigurationSection section, Set<String> knownKeys, List<String> warnings, List<String> errors) {
        ConfigurationSection cost = section.getConfigurationSection("cost");
        if (cost == null) {
            warnings.add(path + ".cost отсутствует. Кейс будет бесплатным.");
            return;
        }

        String type = cost.getString("type", "NONE").toUpperCase(Locale.ROOT);
        if (!type.equals("NONE") && !type.equals("KEY") && !type.equals("XP_LEVELS")) {
            warnings.add(path + ".cost.type = '" + type + "' неизвестен. Будет использован NONE.");
            return;
        }

        if (type.equals("KEY")) {
            String key = cost.getString("key");
            if (key == null || key.isBlank()) {
                errors.add(path + ".cost.key отсутствует при cost.type: KEY.");
            } else if (!knownKeys.contains(key.toLowerCase(Locale.ROOT))) {
                errors.add(path + ".cost.key = '" + key + "' не найден в секции keys.");
            }

            int amount = cost.getInt("amount", 0);
            if (amount <= 0) {
                warnings.add(path + ".cost.amount <= 0. При открытии будет использовано 1.");
            }
        }

        if (type.equals("XP_LEVELS") && cost.getInt("amount", 0) <= 0) {
            warnings.add(path + ".cost.amount должен быть больше 0 для XP_LEVELS.");
        }

        if (cost.contains("buy-xp-levels")) {
            warnings.add(path + ".cost.buy-xp-levels - старое имя поля, оно поддерживается. Новое имя: buy_xp_levels.");
        }
    }

    private static void validateAnimation(String path, ConfigurationSection section, List<String> warnings) {
        ConfigurationSection animation = section.getConfigurationSection("animation");
        if (animation == null) {
            warnings.add(path + ".animation отсутствует. Будут использованы стандартные параметры и SLIME_BALL.");
            return;
        }

        if (animation.contains("duration-ticks")) {
            warnings.add(path + ".animation.duration-ticks - старое имя поля, оно поддерживается. Новое имя: duration_ticks.");
        }
        if (animation.contains("cycle-every-ticks")) {
            warnings.add(path + ".animation.cycle-every-ticks - старое имя поля, оно поддерживается. Новое имя: cycle_every_ticks.");
        }
        if (animation.contains("rise-blocks")) {
            warnings.add(path + ".animation.rise-blocks - старое имя поля, оно поддерживается. Новое имя: rise_blocks.");
        }
        if (animation.contains("spin-degrees-per-tick")) {
            warnings.add(path + ".animation.spin-degrees-per-tick - старое имя поля, оно поддерживается. Новое имя: spin_degrees_per_tick.");
        }

        List<?> items = animation.getList("items");
        if (items == null || items.isEmpty()) {
            warnings.add(path + ".animation.items пустой. В анимации будет использован SLIME_BALL.");
            return;
        }

        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            if (item instanceof ConfigurationSection itemSection) {
                validateMaterial(path + ".animation.items[" + i + "]", itemSection, warnings);
            } else if (item instanceof java.util.Map<?, ?> map) {
                validateMaterial(path + ".animation.items[" + i + "]", map.get("material"), map.get("base64"), warnings);
            } else {
                warnings.add(path + ".animation.items[" + i + "] должен быть секцией предмета.");
            }
        }
    }

    private static void validateRewards(String path, ConfigurationSection section, List<String> warnings, List<String> errors) {
        List<?> rewards = section.getList("rewards");
        if (rewards == null || rewards.isEmpty()) {
            errors.add(path + ".rewards отсутствует или пустой. Будет использована fallback-награда DIAMOND.");
            return;
        }

        for (int i = 0; i < rewards.size(); i++) {
            Object reward = rewards.get(i);
            String rewardPath = path + ".rewards[" + i + "]";
            if (!(reward instanceof java.util.Map<?, ?> map)) {
                errors.add(rewardPath + " должен быть секцией награды.");
                continue;
            }

            int chance = asInt(map.get("chance"), 0);
            if (chance <= 0) {
                errors.add(rewardPath + ".chance должен быть больше 0. Награда будет пропущена.");
            }

            String rawType = String.valueOf(map.containsKey("type") ? map.get("type") : "ITEM");
            String type = inferRewardType(rawType, map);
            if (type == null) {
                errors.add(rewardPath + ".type = '" + rawType + "' неизвестен. Доступно: ITEM, LUCKPERMS, VAULT, PLAYERPOINTS.");
                continue;
            }
            if (!rawType.equalsIgnoreCase(type)) {
                warnings.add(rewardPath + ".type = '" + rawType + "' - старый алиас, он поддерживается как " + type + ".");
            }

            Object rarity = firstPresent(map, "rarity", "rare");
            if (rarity != null && !isKnownRarity(String.valueOf(rarity))) {
                warnings.add(rewardPath + ".rarity = '" + rarity + "' unknown. Available: COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, MYTHIC.");
            }

            switch (type) {
                case "ITEM" -> validateItemReward(rewardPath, map, warnings, errors);
                case "LUCKPERMS" -> {
                    validateVisualReward(rewardPath, map, warnings);
                    validateLuckPermsReward(rewardPath, map, errors);
                }
                case "VAULT" -> {
                    validateVisualReward(rewardPath, map, warnings);
                    validateVaultReward(rewardPath, map, errors);
                }
                case "PLAYERPOINTS" -> {
                    validateVisualReward(rewardPath, map, warnings);
                    validatePlayerPointsReward(rewardPath, map, errors);
                }
                default -> {
                }
            }
        }
    }

    private static void validateItemReward(String path, java.util.Map<?, ?> map, List<String> warnings, List<String> errors) {
        Object item = map.get("item");
        if (!(item instanceof java.util.Map<?, ?> itemMap)) {
            errors.add(path + ".item отсутствует для type: ITEM.");
            return;
        }
        validateMaterial(path + ".item", itemMap.get("material"), itemMap.get("base64"), warnings);
    }

    private static void validateVisualReward(String path, java.util.Map<?, ?> map, List<String> warnings) {
        if (map.containsKey("item")) {
            warnings.add(path + ".item is an old visual alias for non-ITEM rewards. Use .visual instead.");
        }

        Object visual = firstPresent(map, "visual", "visual_item", "visual-item", "display_item", "display-item", "item");
        if (visual instanceof java.util.Map<?, ?> visualMap) {
            validateMaterial(path + ".visual", visualMap.get("material"), visualMap.get("base64"), warnings);
        }
    }

    private static void validateLuckPermsReward(String path, java.util.Map<?, ?> map, List<String> errors) {
        Object luckPerms = map.get("luckperms");
        if (!(luckPerms instanceof java.util.Map<?, ?> lpMap)) {
            errors.add(path + ".luckperms отсутствует для type: LUCKPERMS.");
            return;
        }

        Object group = lpMap.get("group");
        Object node = lpMap.get("node");
        if (isBlank(group) && isBlank(node)) {
            errors.add(path + ".luckperms должен содержать group или node.");
        }
    }

    private static void validateVaultReward(String path, java.util.Map<?, ?> map, List<String> errors) {
        java.util.Map<?, ?> vault = nestedMap(map, "vault");
        Object amount = firstPresent(vault, map, "amount", "money", "value");
        if (asDouble(amount, 0.0) <= 0.0) {
            errors.add(path + ".vault.amount должен быть больше 0.");
        }
    }

    private static void validatePlayerPointsReward(String path, java.util.Map<?, ?> map, List<String> errors) {
        java.util.Map<?, ?> points = nestedMap(map, "playerpoints", "player_points", "player-points", "points");
        Object amount = firstPresent(points, map, "amount", "points", "value");
        if (asInt(amount, 0) <= 0) {
            errors.add(path + ".playerpoints.amount должен быть больше 0.");
        }
    }

    private static void validateMaterial(String path, ConfigurationSection section, List<String> warnings) {
        validateMaterial(path, section.get("material"), section.get("base64"), warnings);
    }

    private static void validateMaterial(String path, Object materialRaw, Object base64Raw, List<String> warnings) {
        if (hasBase64(base64Raw) || hasMaterialBase64(materialRaw)) {
            return;
        }

        String material = materialRaw == null ? null : String.valueOf(materialRaw);
        if (material == null || material.isBlank()) {
            warnings.add(path + ".material отсутствует и base64 не указан. Будет использован STONE.");
            return;
        }

        if (Material.matchMaterial(material) == null) {
            warnings.add(path + ".material = '" + material + "' не найден в этой версии Paper. Будет использован STONE.");
        }
    }

    private static boolean hasBase64(Object value) {
        return value instanceof String base64 && !base64.isBlank();
    }

    private static boolean hasMaterialBase64(Object value) {
        if (!(value instanceof String material) || material.isBlank()) {
            return false;
        }
        String normalized = material.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("base64-") || normalized.startsWith("base64:");
    }

    private static void requireInt(ConfigurationSection section, String path, String key, List<String> errors) {
        if (!section.isInt(key)) {
            errors.add(path + "." + key + " должен быть целым числом.");
        }
    }

    private static String normalizeRewardType(String rawType) {
        if (rawType == null) return "ITEM";
        return switch (rawType.trim().toUpperCase(Locale.ROOT)) {
            case "ITEM", "LUCKPERMS", "VAULT", "PLAYERPOINTS" -> rawType.trim().toUpperCase(Locale.ROOT);
            case "MONEY", "ECONOMY", "ECO" -> "VAULT";
            case "POINTS", "PLAYER_POINTS", "PLAYER-POINTS" -> "PLAYERPOINTS";
            default -> null;
        };
    }

    private static String inferRewardType(String rawType, java.util.Map<?, ?> rewardMap) {
        String normalized = normalizeRewardType(rawType);
        if ("ITEM".equals(normalized) && hasVaultRewardData(rewardMap)) {
            return "VAULT";
        }
        if ("ITEM".equals(normalized) && hasPlayerPointsRewardData(rewardMap)) {
            return "PLAYERPOINTS";
        }
        return normalized;
    }

    private static boolean hasVaultRewardData(java.util.Map<?, ?> rewardMap) {
        java.util.Map<?, ?> vault = nestedMap(rewardMap, "vault", "money", "economy", "eco");
        return firstPresent(vault, rewardMap, "amount", "money", "value", "vault_amount", "vault-amount") != null;
    }

    private static boolean hasPlayerPointsRewardData(java.util.Map<?, ?> rewardMap) {
        java.util.Map<?, ?> points = nestedMap(rewardMap, "playerpoints", "player_points", "player-points", "points");
        return firstPresent(points, rewardMap, "amount", "points", "value", "player_points", "player-points") != null;
    }

    private static boolean isKnownRarity(String value) {
        if (value == null || value.isBlank()) return true;
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "COMMON", "ОБЫЧНАЯ", "DEFAULT",
                 "UNCOMMON", "НЕОБЫЧНАЯ",
                 "RARE", "РЕДКАЯ",
                 "EPIC", "ЭПИЧЕСКАЯ",
                 "LEGENDARY", "ЛЕГЕНДАРНАЯ",
                 "MYTHIC", "MYTHICAL", "МИФИЧЕСКАЯ" -> true;
            default -> false;
        };
    }

    private static java.util.Map<?, ?> nestedMap(java.util.Map<?, ?> map, String... keys) {
        if (map == null) return null;
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof java.util.Map<?, ?> nested) {
                return nested;
            }
        }
        return null;
    }

    private static Object firstPresent(java.util.Map<?, ?> primary, java.util.Map<?, ?> fallback, String... keys) {
        Object value = firstPresent(primary, keys);
        return value != null ? value : firstPresent(fallback, keys);
    }

    private static Object firstPresent(java.util.Map<?, ?> map, String... keys) {
        if (map == null) return null;
        for (String key : keys) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
        }
        return null;
    }

    private static boolean isBlank(Object value) {
        return value == null || String.valueOf(value).isBlank();
    }

    private static int asInt(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static double asDouble(Object value, double fallback) {
        if (value instanceof Number number) return number.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public record Result(int errors, int warnings, int patches) {
        public boolean hasProblems() {
            return errors > 0 || warnings > 0;
        }
    }
}
