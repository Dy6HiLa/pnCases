package ru.privatenull.cases.animation;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.util.VisualEntity;

import java.util.ArrayList;
import java.util.List;

final class LegacyMobRewardVisual implements MobRewardVisual {
    private final VisualEntity reward;
    private final VisualEntity label;

    LegacyMobRewardVisual(Location center, ItemStack item, String name) {
        reward = VisualEntity.item(center.clone().add(0.0, -3.0, 0.0), item);
        label = VisualEntity.text(center.clone().add(0.0, -5.0, 0.0), name);
    }

    @Override
    public List<Entity> entities() {
        List<Entity> result = new ArrayList<>(2);
        result.add(reward.entity());
        result.add(label.entity());
        return result;
    }

    @Override
    public void tick(Location center, int tick) {
        double reveal = ease(Math.min(1.0, tick / 28.0));
        reward.teleport(center.clone().add(0.0, -0.65 + reveal * 0.82
                + Math.sin(tick * 0.14) * 0.06 * reveal, 0.0));
        reward.setRotation((float) ((tick * 5.0) % 360.0), 0.0f);
        reward.setScale((float) (0.20 + reveal * 0.55));
        label.teleport(reveal > 0.72 ? center.clone().add(0.0, 1.25, 0.0) : center.clone().add(0.0, -5.0, 0.0));
    }

    @Override
    public void remove() {
        reward.remove();
        label.remove();
    }

    private static double ease(double value) {
        double clamped = Math.max(0.0, Math.min(1.0, value));
        return 1.0 - Math.pow(1.0 - clamped, 3.0);
    }
}
