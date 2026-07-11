package ru.privatenull.update;

import java.util.Locale;

final class VersionComparator {

    private VersionComparator() {
    }

    static int compare(String leftValue, String rightValue) {
        Version left = Version.parse(leftValue);
        Version right = Version.parse(rightValue);
        for (int index = 0; index < Math.max(left.parts().length, right.parts().length); index++) {
            int leftPart = index < left.parts().length ? left.parts()[index] : 0;
            int rightPart = index < right.parts().length ? right.parts()[index] : 0;
            if (leftPart != rightPart) return Integer.compare(leftPart, rightPart);
        }
        if (left.snapshot() != right.snapshot()) return left.snapshot() ? -1 : 1;
        return 0;
    }

    private record Version(int[] parts, boolean snapshot) {
        static Version parse(String value) {
            String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            boolean snapshot = normalized.contains("snapshot");
            String base = normalized.replaceFirst("^v", "").split("[-+]", 2)[0];
            String[] rawParts = base.split("\\.");
            int[] parts = new int[rawParts.length];
            for (int index = 0; index < rawParts.length; index++) {
                String digits = rawParts[index].replaceAll("\\D", "");
                try {
                    parts[index] = digits.isEmpty() ? 0 : Integer.parseInt(digits);
                } catch (NumberFormatException ignored) {
                    parts[index] = 0;
                }
            }
            return new Version(parts, snapshot);
        }
    }
}
