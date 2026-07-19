package ru.privatenull.cases.model;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;

public final class CaseGuiLayoutRules {

    public static final int MIN_SIZE = 9;
    public static final int MAX_SIZE = 54;

    private CaseGuiLayoutRules() {
    }

    public static int normalizeSize(int rowsOrSlots) {
        int requested = rowsOrSlots >= 1 && rowsOrSlots <= 6 ? rowsOrSlots * 9 : rowsOrSlots;
        return normalizeSlotCount(requested);
    }

    public static int normalizeSlotCount(int slots) {
        int clamped = Math.max(MIN_SIZE, Math.min(MAX_SIZE, slots));
        return Math.min(MAX_SIZE, ((clamped + 8) / 9) * 9);
    }

    public static int clampSlot(int slot, int size, int fallback) {
        int safeSize = normalizeSlotCount(size);
        int safeFallback = Math.max(0, Math.min(safeSize - 1, fallback));
        return slot >= 0 && slot < safeSize ? slot : safeFallback;
    }

    public static List<Integer> filterSlots(List<Integer> slots, int size) {
        int safeSize = normalizeSlotCount(size);
        Set<Integer> result = new LinkedHashSet<>();
        if (slots != null) {
            for (Integer slot : slots) {
                if (slot != null && slot >= 0 && slot < safeSize) {
                    result.add(slot);
                }
            }
        }
        return List.copyOf(result);
    }

    public static int reserveSlot(int requested, int fallback, int size, Set<Integer> reserved) {
        int safeSize = normalizeSlotCount(size);
        int safeFallback = clampSlot(fallback, safeSize, safeSize - 1);
        int slot = requested >= 0 && requested < safeSize && !reserved.contains(requested)
                ? requested : safeFallback;
        if (reserved.contains(slot)) {
            for (int candidate = safeSize - 1; candidate >= 0; candidate--) {
                if (!reserved.contains(candidate)) {
                    slot = candidate;
                    break;
                }
            }
        }
        reserved.add(slot);
        return slot;
    }

    /**
     * Resolves every visible role in priority order. Primary buttons are always
     * assigned first, so a malformed layout can never let history or decor
     * overwrite the case-opening button.
     */
    public static ResolvedSlots resolveSlots(
            int size,
            int requestedOpen,
            int fallbackOpen,
            int requestedAnimation,
            int fallbackAnimation,
            List<Integer> requestedHistory,
            List<Integer> requestedDecor
    ) {
        int safeSize = normalizeSlotCount(size);
        Set<Integer> reserved = new LinkedHashSet<>();
        int open = reserveSlot(requestedOpen, fallbackOpen, safeSize, reserved);
        int animation = requestedAnimation < 0
                ? -1
                : reserveSlot(requestedAnimation, fallbackAnimation, safeSize, reserved);
        List<Integer> history = reserveListedSlots(requestedHistory, safeSize, reserved);
        List<Integer> decor = reserveListedSlots(requestedDecor, safeSize, reserved);
        return new ResolvedSlots(open, animation, history, decor);
    }

    private static List<Integer> reserveListedSlots(List<Integer> requested, int size, Set<Integer> reserved) {
        List<Integer> result = new ArrayList<>();
        for (int slot : filterSlots(requested, size)) {
            if (reserved.add(slot)) result.add(slot);
        }
        return List.copyOf(result);
    }

    public record ResolvedSlots(
            int openSlot,
            int animationSlot,
            List<Integer> historySlots,
            List<Integer> decorSlots
    ) {
    }
}
