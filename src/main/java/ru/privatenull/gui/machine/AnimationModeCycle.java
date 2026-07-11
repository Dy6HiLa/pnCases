package ru.privatenull.gui.machine;

import ru.privatenull.cases.model.AnimationType;

final class AnimationModeCycle {

    private AnimationModeCycle() {
    }

    static AnimationType next(AnimationType current, AnimationType[] available, boolean backwards) {
        int optionCount = available.length + 1;
        int currentIndex = 0;
        if (current != null) {
            for (int index = 0; index < available.length; index++) {
                if (available[index] == current) {
                    currentIndex = index + 1;
                    break;
                }
            }
        }
        int nextIndex = backwards
                ? (currentIndex - 1 + optionCount) % optionCount
                : (currentIndex + 1) % optionCount;
        return nextIndex == 0 ? null : available[nextIndex - 1];
    }
}
