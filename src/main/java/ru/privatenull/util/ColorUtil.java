package ru.privatenull.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Map;

public final class ColorUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.builder()
            .character('\u00A7')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();
    private static final Map<Character, String> LEGACY_TAGS = Map.ofEntries(
            Map.entry('0', "black"), Map.entry('1', "dark_blue"),
            Map.entry('2', "dark_green"), Map.entry('3', "dark_aqua"),
            Map.entry('4', "dark_red"), Map.entry('5', "dark_purple"),
            Map.entry('6', "gold"), Map.entry('7', "gray"),
            Map.entry('8', "dark_gray"), Map.entry('9', "blue"),
            Map.entry('a', "green"), Map.entry('b', "aqua"),
            Map.entry('c', "red"), Map.entry('d', "light_purple"),
            Map.entry('e', "yellow"), Map.entry('f', "white"),
            Map.entry('k', "obfuscated"), Map.entry('l', "bold"),
            Map.entry('m', "strikethrough"), Map.entry('n', "underlined"),
            Map.entry('o', "italic"), Map.entry('r', "reset")
    );

    private ColorUtil() {
    }

    /**
     * Supports legacy ampersand/section codes, RGB values and MiniMessage tags in YAML strings.
     */
    public static String colorize(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        Component component = MINI_MESSAGE.deserialize(toMiniMessage(value));
        return LEGACY_SERIALIZER.serialize(component);
    }

    private static String toMiniMessage(String value) {
        StringBuilder result = new StringBuilder(value.length() + 16);
        for (int index = 0; index < value.length();) {
            char current = value.charAt(index);
            if (!isLegacyPrefix(current)) {
                result.append(current);
                index++;
                continue;
            }

            if (isAmpersandHex(value, index)) {
                result.append("<#").append(value, index + 2, index + 8).append('>');
                index += 8;
                continue;
            }

            if (isLegacyHex(value, index)) {
                result.append("<#");
                for (int hexIndex = index + 3; hexIndex <= index + 13; hexIndex += 2) {
                    result.append(value.charAt(hexIndex));
                }
                result.append('>');
                index += 14;
                continue;
            }

            if (index + 1 < value.length()) {
                String tag = LEGACY_TAGS.get(Character.toLowerCase(value.charAt(index + 1)));
                if (tag != null) {
                    result.append('<').append(tag).append('>');
                    index += 2;
                    continue;
                }
            }

            result.append(current);
            index++;
        }
        return result.toString();
    }

    private static boolean isAmpersandHex(String value, int index) {
        if (value.charAt(index) != '&' || index + 7 >= value.length() || value.charAt(index + 1) != '#') {
            return false;
        }
        for (int hexIndex = index + 2; hexIndex < index + 8; hexIndex++) {
            if (!isHex(value.charAt(hexIndex))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isLegacyHex(String value, int index) {
        if (index + 13 >= value.length() || Character.toLowerCase(value.charAt(index + 1)) != 'x') {
            return false;
        }
        for (int hexIndex = index + 2; hexIndex <= index + 12; hexIndex += 2) {
            if (!isLegacyPrefix(value.charAt(hexIndex)) || !isHex(value.charAt(hexIndex + 1))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isLegacyPrefix(char value) {
        return value == '&' || value == '\u00A7';
    }

    private static boolean isHex(char value) {
        return (value >= '0' && value <= '9')
                || (value >= 'a' && value <= 'f')
                || (value >= 'A' && value <= 'F');
    }
}
