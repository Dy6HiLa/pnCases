package ru.privatenull.cases.model;

import org.bukkit.inventory.ItemStack;

import java.util.Locale;

public class Reward {

    public enum Type { ITEM, LUCKPERMS, VAULT, PLAYERPOINTS, PERSONAL }

    public enum Rarity {
        COMMON("Обычная", "§7"),
        UNCOMMON("Необычная", "§a"),
        RARE("Редкая", "§9"),
        EPIC("Эпическая", "§5"),
        LEGENDARY("Легендарная", "§6"),
        MYTHIC("Мифическая", "§d");

        private final String displayName;
        private final String color;

        Rarity(String displayName, String color) {
            this.displayName = displayName;
            this.color = color;
        }

        public String displayName() { return displayName; }
        public String color() { return color; }
        public String coloredName() { return color + displayName; }

        public static Rarity parse(String value, int chance) {
            Rarity known = parseKnown(value);
            return known == null ? fromChance(chance) : known;
        }

        public static Rarity parseKnown(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            return switch (value.trim().toUpperCase(Locale.ROOT)) {
                case "COMMON", "ОБЫЧНАЯ", "DEFAULT" -> COMMON;
                case "UNCOMMON", "НЕОБЫЧНАЯ" -> UNCOMMON;
                case "RARE", "РЕДКАЯ" -> RARE;
                case "EPIC", "ЭПИЧЕСКАЯ" -> EPIC;
                case "LEGENDARY", "ЛЕГЕНДАРНАЯ" -> LEGENDARY;
                case "MYTHIC", "MYTHICAL", "МИФИЧЕСКАЯ" -> MYTHIC;
                default -> null;
            };
        }

        public static Rarity fromChance(int chance) {
            if (chance <= 3) return MYTHIC;
            if (chance <= 10) return LEGENDARY;
            if (chance <= 20) return EPIC;
            if (chance <= 35) return RARE;
            if (chance <= 55) return UNCOMMON;
            return COMMON;
        }
    }

    private final int chance;
    private final Type type;
    private final ItemStack item;
    private final ItemStack visualItem;
    private final String lpGroup;
    private final String lpNode;
    private final String lpDuration;
    private final double vaultAmount;
    private final int playerPointsAmount;
    private final String command;
    private final String message;
    private final String displayName;
    private final Rarity rarity;
    private final String rarityId;

    public Reward(int chance, Type type, ItemStack item, String lpGroup, String lpNode, String lpDuration, String message, String displayName) {
        this(chance, type, item, lpGroup, lpNode, lpDuration, 0.0, 0, message, displayName);
    }

    public Reward(int chance, Type type, ItemStack item, String lpGroup, String lpNode, String lpDuration, String message, String displayName, Rarity rarity) {
        this(chance, type, item, lpGroup, lpNode, lpDuration, 0.0, 0, message, displayName, rarity);
    }

    public Reward(
            int chance,
            Type type,
            ItemStack item,
            String lpGroup,
            String lpNode,
            String lpDuration,
            double vaultAmount,
            int playerPointsAmount,
            String message,
            String displayName
    ) {
        this(chance, type, item, lpGroup, lpNode, lpDuration, vaultAmount, playerPointsAmount, message, displayName,
                Rarity.fromChance(chance));
    }

    public Reward(
            int chance,
            Type type,
            ItemStack item,
            String lpGroup,
            String lpNode,
            String lpDuration,
            double vaultAmount,
            int playerPointsAmount,
            String message,
            String displayName,
            Rarity rarity
    ) {
        this(chance, type, item, lpGroup, lpNode, lpDuration, vaultAmount, playerPointsAmount,
                message, displayName, rarity, rarity == null ? null : rarity.name(), item, null);
    }

    public Reward(
            int chance,
            Type type,
            ItemStack item,
            String lpGroup,
            String lpNode,
            String lpDuration,
            double vaultAmount,
            int playerPointsAmount,
            String message,
            String displayName,
            String rarityId
    ) {
        this(chance, type, item, lpGroup, lpNode, lpDuration, vaultAmount, playerPointsAmount,
                message, displayName, Rarity.parse(rarityId, chance), rarityId, item, null);
    }

    public Reward(
            int chance,
            Type type,
            ItemStack item,
            String lpGroup,
            String lpNode,
            String lpDuration,
            double vaultAmount,
            int playerPointsAmount,
            String message,
            String displayName,
            String rarityId,
            ItemStack visualItem
    ) {
        this(chance, type, item, lpGroup, lpNode, lpDuration, vaultAmount, playerPointsAmount,
                message, displayName, rarityId, visualItem, null);
    }

    public Reward(
            int chance,
            Type type,
            ItemStack item,
            String lpGroup,
            String lpNode,
            String lpDuration,
            double vaultAmount,
            int playerPointsAmount,
            String message,
            String displayName,
            String rarityId,
            ItemStack visualItem,
            String command
    ) {
        this(chance, type, item, lpGroup, lpNode, lpDuration, vaultAmount, playerPointsAmount,
                message, displayName, Rarity.parse(rarityId, chance), rarityId, visualItem, command);
    }

    private Reward(
            int chance,
            Type type,
            ItemStack item,
            String lpGroup,
            String lpNode,
            String lpDuration,
            double vaultAmount,
            int playerPointsAmount,
            String message,
            String displayName,
            Rarity rarity,
            String rarityId,
            ItemStack visualItem,
            String command
    ) {
        this.chance = chance;
        this.type = type;
        this.item = item;
        this.visualItem = visualItem == null ? item : visualItem;
        this.lpGroup = lpGroup;
        this.lpNode = lpNode;
        this.lpDuration = lpDuration;
        this.vaultAmount = vaultAmount;
        this.playerPointsAmount = playerPointsAmount;
        this.command = command;
        this.message = message;
        this.displayName = displayName;
        this.rarity = rarity == null ? Rarity.fromChance(chance) : rarity;
        this.rarityId = normalizeRarityId(rarityId, chance);
    }

    public static String normalizeRarityId(String value, int chance) {
        if (value == null || value.isBlank()) {
            return Rarity.fromChance(chance).name().toLowerCase(Locale.ROOT);
        }
        Rarity known = Rarity.parseKnown(value);
        if (known != null) {
            return known.name().toLowerCase(Locale.ROOT);
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}_-]", "_")
                .replaceAll("_+", "_");
        return normalized.isBlank() ? Rarity.fromChance(chance).name().toLowerCase(Locale.ROOT) : normalized;
    }

    public int chance() { return chance; }
    public Type type() { return type; }
    public ItemStack item() { return type == Type.ITEM ? item : null; }
    public ItemStack visualItem() { return visualItem; }
    public String lpGroup() { return lpGroup; }
    public String lpNode() { return lpNode; }
    public String lpDuration() { return lpDuration; }
    public double vaultAmount() { return vaultAmount; }
    public int playerPointsAmount() { return playerPointsAmount; }
    public String command() { return command; }
    public String message() { return message; }
    public String displayName() { return displayName; }
    public Rarity rarity() { return rarity; }
    public String rarityId() { return rarityId; }
}
