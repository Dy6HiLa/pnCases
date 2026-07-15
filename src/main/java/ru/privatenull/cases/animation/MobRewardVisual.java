package ru.privatenull.cases.animation;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.List;

interface MobRewardVisual {
    List<Entity> entities();

    void tick(Location center, int tick);

    void remove();
}
