package ru.privatenull.cases.reward;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.cases.model.Reward;
import ru.privatenull.PnCasesPlugin;
import ru.privatenull.util.ColorUtil;
import ru.privatenull.util.EnchantmentCompat;
import ru.privatenull.util.MaterialCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static ru.privatenull.util.ItemNames.readableItemName;

public final class RewardPresentationService {

    private final PnCasesPlugin plugin;

    public RewardPresentationService(PnCasesPlugin plugin) {
        this.plugin = plugin;
    }

    public ItemStack buildPreviewItem(CaseDefinition definition, Reward reward, int totalChance) {
        ItemStack item = buildDisplayItem(definition, reward);
        PreviewRarityStyle rarity = resolveRarity(reward);
        PreviewTypeStyle type = resolveType(reward.type());
        if (rarity.material() != null) {
            item.setType(rarity.material());
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String rewardName = color(resolveViewName(reward, item));
        String amount = reward.type() == Reward.Type.VAULT
                ? formatVaultAmount(reward.vaultAmount())
                : reward.type() == Reward.Type.PLAYERPOINTS
                ? formatPlayerPointsAmount(reward.playerPointsAmount()) : "";
        String duration = reward.lpDuration() == null ? "" : reward.lpDuration();
        int itemAmount = reward.item() == null ? 0 : reward.item().getAmount();
        String[] replacements = {
                "case", definition == null ? "" : color(definition.displayName()),
                "case_id", definition == null ? "" : definition.name(),
                "case-id", definition == null ? "" : definition.name(),
                "reward", rewardName,
                "rarity", rarity.color() + rarity.name(),
                "rarity_name", rarity.name(),
                "rarity-name", rarity.name(),
                "rarity_id", rarity.id(),
                "rarity-id", rarity.id(),
                "rarity_color", rarity.color(),
                "rarity-color", rarity.color(),
                "rarity_symbol", rarity.symbol(),
                "rarity-symbol", rarity.symbol(),
                "rarity_description", rarity.description(),
                "rarity-description", rarity.description(),
                "type", type.color() + type.name(),
                "type_name", type.name(),
                "type-name", type.name(),
                "type_id", type.id(),
                "type-id", type.id(),
                "type_color", type.color(),
                "type-color", type.color(),
                "type_symbol", type.symbol(),
                "type-symbol", type.symbol(),
                "type_description", type.description(),
                "type-description", type.description(),
                "chance", formatChancePercent(reward.chance(), totalChance),
                "weight", String.valueOf(reward.chance()),
                "amount", amount,
                "duration", duration,
                "item_amount", String.valueOf(itemAmount),
                "item-amount", String.valueOf(itemAmount)
        };
        meta.setDisplayName(plugin.getGuiConfig().text(
                "preview.reward.name", "{rarity_color}{rarity_symbol} &f{reward}", replacements));

        List<String> originalLore = meta.hasLore() && meta.getLore() != null
                ? new ArrayList<>(meta.getLore()) : List.of();
        List<String> configuredLore = new ArrayList<>(plugin.getGuiConfig().list(
                "preview.reward.lore", defaultPreviewLore(), replacements));
        configuredLore.addAll(plugin.getGuiConfig().list(
                "preview.reward.type-lore." + type.id(), defaultTypeLore(reward.type()), replacements));
        if (plugin.getGuiConfig().bool("preview.reward.hide-empty-lines", true)) {
            configuredLore.removeIf(this::isEmptyValueLine);
        }

        meta.setLore(mergeLore(originalLore, configuredLore));
        if (plugin.getGuiConfig().bool("preview.reward.hide-attributes", true)) {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        }
        int customModelData = rarity.customModelData() > 0
                ? rarity.customModelData()
                : plugin.getGuiConfig().integer("preview.reward.custom-model-data", 0);
        if (customModelData > 0) {
            meta.setCustomModelData(customModelData);
        }
        if (rarity.glow() || plugin.getGuiConfig().bool("preview.reward.glow", false)) {
            var enchantment = EnchantmentCompat.unbreaking();
            if (enchantment != null) {
                meta.addEnchant(enchantment, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
        }
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack buildDisplayItem(CaseDefinition definition, Reward reward) {
        if (reward.visualItem() != null) {
            return reward.visualItem().clone();
        }

        ItemStack matched = findMatchingAnimationItem(definition, reward);
        if (matched != null) {
            return matched;
        }

        Material fallbackMaterial = switch (reward.type()) {
            case VAULT -> Material.EMERALD;
            case PLAYERPOINTS -> MaterialCompat.first("AMETHYST_SHARD", "EMERALD");
            case LUCKPERMS -> Material.NETHER_STAR;
            case ITEM -> Material.CHEST;
        };
        String typePath = "preview.types." + reward.type().name().toLowerCase(Locale.ROOT) + ".material";
        Material material = configuredMaterial(plugin.getGuiConfig().raw(typePath, ""), fallbackMaterial);

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

    public ItemStack findDisplayItem(CaseDefinition definition, String rewardName) {
        if (definition == null || rewardName == null) return null;
        String targetName = normalizeDisplayName(rewardName);
        for (Reward reward : definition.rewards()) {
            if (matchesDisplayName(normalizeDisplayName(reward.displayName()), targetName)) {
                return buildDisplayItem(definition, reward);
            }
        }
        return null;
    }

    public String resolveViewName(Reward reward, ItemStack visual) {
        String configured = reward == null ? null : reward.displayName();
        String visualName = customDisplayName(visual);
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

    public String formatVaultAmount(double amount) {
        String symbol = plugin.getConfig().getString("reward-symbols.vault", "$");
        return (symbol == null ? "$" : symbol) + formatAmount(amount);
    }

    public String formatPlayerPointsAmount(int amount) {
        String symbol = plugin.getConfig().getString("reward-symbols.playerpoints", "✦");
        return (symbol == null ? "✦" : symbol) + amount;
    }

    private ItemStack findMatchingAnimationItem(CaseDefinition definition, Reward reward) {
        if (definition == null || reward == null || definition.animationItems() == null) {
            return null;
        }
        String rewardName = normalizeDisplayName(reward.displayName());
        String groupName = normalizeDisplayName(reward.lpGroup());
        String nodeName = normalizeDisplayName(reward.lpNode());
        for (ItemStack item : definition.animationItems()) {
            if (item == null) continue;
            String itemName = normalizeDisplayName(readableItemName(item));
            if (matchesDisplayName(itemName, rewardName)
                    || matchesDisplayName(itemName, groupName)
                    || matchesDisplayName(itemName, nodeName)) {
                return item.clone();
            }
        }
        return null;
    }

    private PreviewRarityStyle resolveRarity(Reward reward) {
        String id = reward.rarityId();
        Reward.Rarity known = Reward.Rarity.parseKnown(id);
        String fallbackName = known == null ? humanizeId(id) : known.displayName();
        String fallbackColor = known == null ? "&f" : known.color();
        String path = "preview.rarities." + id;
        String humanized = humanizeId(id);
        String name = styleValue(path + ".name", "preview.rarities.default.name", fallbackName)
                .replace("{rarity_id}", humanized).replace("{rarity-id}", humanized);
        String color = styleValue(path + ".color", "preview.rarities.default.color", fallbackColor);
        String symbol = styleValue(path + ".symbol", "preview.rarities.default.symbol", "◆");
        String description = styleValue(path + ".description", "preview.rarities.default.description", "");
        Material material = configuredMaterial(
                styleValue(path + ".material", "preview.rarities.default.material", ""), null);
        boolean glow = styleBoolean(path + ".glow", "preview.rarities.default.glow", false);
        int customModelData = styleInteger(path + ".custom-model-data",
                "preview.rarities.default.custom-model-data", 0);
        return new PreviewRarityStyle(id, name, color, symbol, description, material, glow, customModelData);
    }

    private PreviewTypeStyle resolveType(Reward.Type rewardType) {
        String id = rewardType.name().toLowerCase(Locale.ROOT);
        String path = "preview.types." + id;
        return new PreviewTypeStyle(
                id,
                plugin.getGuiConfig().raw(path + ".name", formatRewardType(rewardType)),
                plugin.getGuiConfig().raw(path + ".color", "&f"),
                plugin.getGuiConfig().raw(path + ".symbol", "◆"),
                plugin.getGuiConfig().raw(path + ".description", "")
        );
    }

    private List<String> defaultPreviewLore() {
        return List.of(
                "", "&6 «Основное»",
                "&f- Тип: {type_color}{type_name}",
                "&f- Редкость: {rarity_color}{rarity_name}",
                "", "&6 «Шанс»",
                "&f- Выпадение: &e{chance}",
                "&f- Вес: &e{weight}"
        );
    }

    private List<String> defaultTypeLore(Reward.Type type) {
        return switch (type) {
            case ITEM -> List.of("", "&6 «Награда»", "&f- Количество: &e{item_amount}", "");
            case VAULT -> List.of("", "&6 «Награда»", "&f- Монеты: &a{amount}", "");
            case PLAYERPOINTS -> List.of("", "&6 «Награда»", "&f- Поинты: &b{amount}", "");
            case LUCKPERMS -> List.of("", "&6 «Награда»", "&f- Срок: &d{duration}", "");
        };
    }

    private List<String> mergeLore(List<String> original, List<String> configured) {
        if (!plugin.getGuiConfig().bool("preview.reward.keep-original-lore", true) || original.isEmpty()) {
            return configured;
        }
        boolean separator = plugin.getGuiConfig().bool("preview.reward.original-lore-separator", true);
        String position = plugin.getGuiConfig().raw("preview.reward.original-lore-position", "BEFORE");
        List<String> result = new ArrayList<>(original.size() + configured.size() + 1);
        if ("AFTER".equalsIgnoreCase(position)) {
            result.addAll(configured);
            addSeparator(result, separator);
            result.addAll(original);
        } else {
            result.addAll(original);
            addSeparator(result, separator);
            result.addAll(configured);
        }
        return result;
    }

    private static void addSeparator(List<String> lines, boolean enabled) {
        if (enabled && !lines.isEmpty() && !lines.get(lines.size() - 1).isBlank()) {
            lines.add("");
        }
    }

    private boolean isEmptyValueLine(String line) {
        if (line == null || line.isBlank()) return false;
        String plain = ChatColor.stripColor(line);
        if (plain == null) plain = line;
        String normalized = plain.trim();
        return normalized.endsWith(":") || normalized.endsWith(": -");
    }

    private String styleValue(String path, String defaultPath, String fallback) {
        return plugin.getGuiConfig().contains(path)
                ? plugin.getGuiConfig().raw(path, fallback)
                : plugin.getGuiConfig().raw(defaultPath, fallback);
    }

    private boolean styleBoolean(String path, String defaultPath, boolean fallback) {
        return plugin.getGuiConfig().contains(path)
                ? plugin.getGuiConfig().bool(path, fallback)
                : plugin.getGuiConfig().bool(defaultPath, fallback);
    }

    private int styleInteger(String path, String defaultPath, int fallback) {
        return plugin.getGuiConfig().contains(path)
                ? plugin.getGuiConfig().integer(path, fallback)
                : plugin.getGuiConfig().integer(defaultPath, fallback);
    }

    private Material configuredMaterial(String raw, Material fallback) {
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("ORIGINAL") || raw.equalsIgnoreCase("KEEP")) {
            return fallback;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("MINECRAFT:")) {
            normalized = normalized.substring("MINECRAFT:".length());
        }
        Material material = Material.matchMaterial(normalized);
        return material == null || material.isAir() ? fallback : material;
    }

    private String customDisplayName(ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() ? meta.getDisplayName() : null;
    }

    private boolean isGenericLuckPermsName(Reward reward, String value) {
        if (reward == null || reward.type() != Reward.Type.LUCKPERMS || value == null || value.isBlank()) {
            return false;
        }
        String stripped = ChatColor.stripColor(color(value));
        if (stripped == null) return false;
        String normalized = stripped.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "");
        return normalized.equals("luckperms") || normalized.equals("привилегия");
    }

    private String normalizeDisplayName(String value) {
        if (value == null || value.isBlank()) return "";
        String colored = color(value);
        String stripped = ChatColor.stripColor(colored);
        if (stripped == null) stripped = colored;
        return stripped.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "");
    }

    private boolean matchesDisplayName(String itemName, String rewardName) {
        if (itemName.length() < 2 || rewardName.length() < 2) return false;
        return itemName.equals(rewardName) || itemName.contains(rewardName) || rewardName.contains(itemName);
    }

    private String humanizeId(String id) {
        if (id == null || id.isBlank()) return "Своя";
        String value = id.replace('_', ' ').replace('-', ' ').trim();
        if (value.isBlank()) return "Своя";
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
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
            return (int) Math.rint(percent) + "%";
        }
        return String.format(Locale.US, "%.1f%%", percent);
    }

    private static String formatAmount(double amount) {
        if (Math.abs(amount - Math.rint(amount)) < 0.000001D) {
            return String.valueOf((long) Math.rint(amount));
        }
        return String.format(Locale.US, "%.2f", amount).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String color(String value) {
        return ColorUtil.colorize(value);
    }

    private record PreviewRarityStyle(String id, String name, String color, String symbol, String description,
                                      Material material, boolean glow, int customModelData) {
    }

    private record PreviewTypeStyle(String id, String name, String color, String symbol, String description) {
    }
}
