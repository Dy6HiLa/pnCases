package ru.privatenull.cases.reward;

import ru.privatenull.cases.model.Reward;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class RewardSelector {

    public Reward select(List<Reward> rewards) {
        if (rewards == null || rewards.isEmpty()) {
            throw new IllegalArgumentException("Reward list must not be empty");
        }
        int totalWeight = rewards.stream().mapToInt(reward -> Math.max(0, reward.chance())).sum();
        if (totalWeight <= 0) return rewards.get(0);
        int roll = ThreadLocalRandom.current().nextInt(totalWeight) + 1;
        return selectByRoll(rewards, roll);
    }

    Reward selectByRoll(List<Reward> rewards, int roll) {
        int current = 0;
        for (Reward reward : rewards) {
            current += Math.max(0, reward.chance());
            if (roll <= current) return reward;
        }
        return rewards.get(rewards.size() - 1);
    }
}
