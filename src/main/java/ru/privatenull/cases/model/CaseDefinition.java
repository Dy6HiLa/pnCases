package ru.privatenull.cases.model;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class CaseDefinition {

    public enum CostType { NONE, XP_LEVELS, KEY }

    private final String name;
    private final Location blockLocation;

    private final String guiTitle;
    private final ItemStack openButton;

    private final CostType costType;
    private final int costAmount;
    private final String costKeyId;

    private final int buyKeyWithXpLevels;
    private final int durationTicks;
    private final int cycleEveryTicks;
    private final double riseBlocks;
    private final float spinDegreesPerTick;
    private final List<ItemStack> animationItems;

    private final List<Reward> rewards;

    public CaseDefinition(
            String name,
            Location blockLocation,
            String guiTitle,
            ItemStack openButton,
            CostType costType,
            int costAmount,
            String costKeyId,
            int buyKeyWithXpLevels,
            int durationTicks,
            int cycleEveryTicks,
            double riseBlocks,
            float spinDegreesPerTick,
            List<ItemStack> animationItems,
            List<Reward> rewards
    ) {
        this.name = name;
        this.blockLocation = blockLocation;
        this.guiTitle = guiTitle;
        this.openButton = openButton;
        this.costType = costType;
        this.costAmount = costAmount;
        this.costKeyId = costKeyId;
        this.buyKeyWithXpLevels = buyKeyWithXpLevels;
        this.durationTicks = durationTicks;
        this.cycleEveryTicks = cycleEveryTicks;
        this.riseBlocks = riseBlocks;
        this.spinDegreesPerTick = spinDegreesPerTick;
        this.animationItems = animationItems;
        this.rewards = rewards;
    }

    public String name() { return name; }
    public Location blockLocation() { return blockLocation; }
    public String guiTitle() { return guiTitle; }
    public ItemStack openButton() { return openButton; }

    public CostType costType() { return costType; }
    public int costAmount() { return costAmount; }
    public String costKeyId() { return costKeyId; }

    public int buyKeyWithXpLevels() { return buyKeyWithXpLevels; }

    public int durationTicks() { return durationTicks; }
    public int cycleEveryTicks() { return cycleEveryTicks; }
    public double riseBlocks() { return riseBlocks; }
    public float spinDegreesPerTick() { return spinDegreesPerTick; }
    public List<ItemStack> animationItems() { return animationItems; }

    public List<Reward> rewards() { return rewards; }
}