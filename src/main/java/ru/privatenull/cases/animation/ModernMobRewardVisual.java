package ru.privatenull.cases.animation;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class ModernMobRewardVisual implements MobRewardVisual {
    private final ItemDisplay reward;
    private final TextDisplay label;

    public ModernMobRewardVisual(Location center, ItemStack item, String name) {
        reward = (ItemDisplay) center.getWorld().spawnEntity(center.clone().add(0.0, -3.0, 0.0), EntityType.ITEM_DISPLAY);
        reward.setItemStack(item);
        reward.setBillboard(Display.Billboard.FIXED);
        prepare(reward);
        setItemTransform(reward, 0.02f, 0.0f);

        label = (TextDisplay) center.getWorld().spawnEntity(center.clone().add(0.0, -5.0, 0.0), EntityType.TEXT_DISPLAY);
        label.setText(name);
        label.setBillboard(Display.Billboard.CENTER);
        label.setShadowed(true);
        label.setSeeThrough(true);
        label.setDefaultBackground(false);
        label.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        prepare(label);
    }

    @Override
    public List<Entity> entities() {
        List<Entity> result = new ArrayList<>(2);
        result.add(reward);
        result.add(label);
        return result;
    }

    @Override
    public void tick(Location center, int tick) {
        double reveal = ease(Math.min(1.0, tick / 30.0));
        Location rewardLocation = center.clone().add(0.0,
                -0.72 + reveal * 0.90 + Math.sin(tick * 0.14) * 0.07 * reveal, 0.0);
        reward.teleport(rewardLocation);
        setItemTransform(reward, (float) (0.02 + reveal * 0.88), (float) (tick * 0.075));

        if (reveal > 0.72) {
            label.teleport(center.clone().add(0.0, 1.28 + Math.sin(tick * 0.10) * 0.04, 0.0));
        } else {
            label.teleport(center.clone().add(0.0, -5.0, 0.0));
        }
    }

    @Override
    public void remove() {
        reward.remove();
        label.remove();
    }

    private static void prepare(Display display) {
        display.setPersistent(false);
        display.setInvulnerable(true);
        display.setViewRange(32.0f);
        display.setShadowRadius(0.0f);
        display.setInterpolationDuration(2);
        display.setTeleportDuration(2);
        display.setBrightness(new Display.Brightness(15, 15));
    }

    private static void setItemTransform(ItemDisplay display, float scale, float yaw) {
        display.setTransformation(new Transformation(
                new Vector3f(),
                new AxisAngle4f(yaw, 0.0f, 1.0f, 0.0f),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f()
        ));
        display.setInterpolationDelay(0);
    }

    private static double ease(double value) {
        double clamped = Math.max(0.0, Math.min(1.0, value));
        return 1.0 - Math.pow(1.0 - clamped, 3.0);
    }
}
