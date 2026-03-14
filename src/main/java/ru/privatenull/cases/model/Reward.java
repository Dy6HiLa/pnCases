package ru.privatenull.cases.model;

import org.bukkit.inventory.ItemStack;

public class Reward {

    public enum Type { ITEM, LUCKPERMS }

    private final int chance;
    private final Type type;
    private final ItemStack item;
    private final String lpGroup;
    private final String lpNode;
    private final String lpDuration;
    private final String message;
    private final String displayName;

    public Reward(int chance, Type type, ItemStack item, String lpGroup, String lpNode, String lpDuration, String message, String displayName) {
        this.chance = chance;
        this.type = type;
        this.item = item;
        this.lpGroup = lpGroup;
        this.lpNode = lpNode;
        this.lpDuration = lpDuration;
        this.message = message;
        this.displayName = displayName;
    }

    public int chance()          { return chance; }
    public Type type()           { return type; }
    public ItemStack item()      { return item; }
    public String lpGroup()      { return lpGroup; }
    public String lpNode()       { return lpNode; }
    public String lpDuration()   { return lpDuration; }
    public String message()      { return message; }
    public String displayName()  { return displayName; }
}