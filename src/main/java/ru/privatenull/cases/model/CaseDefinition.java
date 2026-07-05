package ru.privatenull.cases.model;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.cases.animation.AnimationType;

import java.util.List;

public class CaseDefinition {

    public enum CostType { NONE, XP_LEVELS, KEY }

    private final String name;
    private final Location blockLocation;
    private final List<Location> blockLocations;

    private final String guiTitle;
    private final ItemStack openButton;
    private final CaseGuiLayout guiLayout;
    private final IdleParticleSettings idleParticles;

    private final CostType costType;
    private final int costAmount;
    private final String costKeyId;

    private final int buyKeyWithXpLevels;
    private final int durationTicks;
    private final int cycleEveryTicks;
    private final double riseBlocks;
    private final float spinDegreesPerTick;
    private final AnimationType fixedAnimation;
    private final List<ItemStack> animationItems;

    private final List<Reward> rewards;

    public CaseDefinition(
            String name,
            List<Location> blockLocations,
            String guiTitle,
            ItemStack openButton,
            CaseGuiLayout guiLayout,
            IdleParticleSettings idleParticles,
            CostType costType,
            int costAmount,
            String costKeyId,
            int buyKeyWithXpLevels,
            int durationTicks,
            int cycleEveryTicks,
            double riseBlocks,
            float spinDegreesPerTick,
            AnimationType fixedAnimation,
            List<ItemStack> animationItems,
            List<Reward> rewards
    ) {
        this.name = name;
        this.blockLocations = blockLocations == null
                ? List.of()
                : List.copyOf(blockLocations.stream().filter(location -> location != null).toList());
        this.blockLocation = this.blockLocations.isEmpty() ? null : this.blockLocations.get(0);
        this.guiTitle = guiTitle;
        this.openButton = openButton;
        this.guiLayout = guiLayout == null ? CaseGuiLayout.defaults() : guiLayout;
        this.idleParticles = idleParticles == null ? IdleParticleSettings.defaults() : idleParticles;
        this.costType = costType;
        this.costAmount = costAmount;
        this.costKeyId = costKeyId;
        this.buyKeyWithXpLevels = buyKeyWithXpLevels;
        this.durationTicks = durationTicks;
        this.cycleEveryTicks = cycleEveryTicks;
        this.riseBlocks = riseBlocks;
        this.spinDegreesPerTick = spinDegreesPerTick;
        this.fixedAnimation = fixedAnimation;
        this.animationItems = animationItems;
        this.rewards = rewards;
    }

    public String name() { return name; }
    public Location blockLocation() { return blockLocation; }
    public List<Location> blockLocations() { return blockLocations; }
    public String guiTitle() { return guiTitle; }
    public ItemStack openButton() { return openButton; }
    public CaseGuiLayout guiLayout() { return guiLayout; }
    public IdleParticleSettings idleParticles() { return idleParticles; }

    public CostType costType() { return costType; }
    public int costAmount() { return costAmount; }
    public String costKeyId() { return costKeyId; }

    public int buyKeyWithXpLevels() { return buyKeyWithXpLevels; }

    public int durationTicks() { return durationTicks; }
    public int cycleEveryTicks() { return cycleEveryTicks; }
    public double riseBlocks() { return riseBlocks; }
    public float spinDegreesPerTick() { return spinDegreesPerTick; }
    public AnimationType fixedAnimation() { return fixedAnimation; }
    public List<ItemStack> animationItems() { return animationItems; }

    public List<Reward> rewards() { return rewards; }
}
