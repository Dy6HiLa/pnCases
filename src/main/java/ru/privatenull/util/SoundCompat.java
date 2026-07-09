package ru.privatenull.util;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class SoundCompat {

    private SoundCompat() {
    }

    public static void play(Player player, String[] names, float volume, float pitch) {
        if (player == null) {
            return;
        }
        Sound sound = first(names);
        if (sound != null) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    public static void play(World world, Location location, String[] names, float volume, float pitch) {
        if (world == null || location == null) {
            return;
        }
        Sound sound = first(names);
        if (sound != null) {
            world.playSound(location, sound, volume, pitch);
        }
    }

    public static Sound first(String... names) {
        if (names != null) {
            for (String name : names) {
                Sound sound = byName(name);
                if (sound != null) {
                    return sound;
                }
            }
        }
        return byName("UI_BUTTON_CLICK");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Sound byName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String normalized = name.trim().toUpperCase();
        try {
            Class<?> soundClass = Class.forName("org.bukkit.Sound", false, SoundCompat.class.getClassLoader());
            Object value;
            if (soundClass.isEnum()) {
                Class<? extends Enum> enumClass = soundClass.asSubclass(Enum.class);
                value = Enum.valueOf(enumClass, normalized);
            } else {
                value = soundClass.getMethod("valueOf", String.class).invoke(null, normalized);
            }
            return soundClass.isInstance(value) ? (Sound) value : null;
        } catch (ReflectiveOperationException | IllegalArgumentException | ClassCastException ignored) {
            return null;
        }
    }
}
