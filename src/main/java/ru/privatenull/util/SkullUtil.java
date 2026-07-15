package ru.privatenull.util;

import ru.privatenull.pnlibrary.text.ColorUtil;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;

import java.lang.reflect.Field;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SkullUtil {

    private static final Pattern TEXTURE_URL = Pattern.compile(
            "https?://textures\\.minecraft\\.net/texture/[A-Za-z0-9]+"
    );

    public static ItemStack fromBase64(String base64OrUrlOrHash, String displayName) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) {
            return head;
        }

        meta.setDisplayName(ColorUtil.colorize(displayName));

        try {
            URL skinUrl = extractSkinUrl(base64OrUrlOrHash);
            if (skinUrl != null && !applyPaperProfile(meta, skinUrl)) {
                applyGameProfile(meta, skinUrl);
            }
        } catch (Exception exception) {
            Bukkit.getLogger().log(Level.FINE, "pnCases could not apply skull texture", exception);
        }

        head.setItemMeta(meta);
        return head;
    }

    private static boolean applyPaperProfile(SkullMeta meta, URL skinUrl) {
        if (!ServerCompatibility.currentVersion().isAtLeast(1, 19, 0)) {
            return false;
        }

        try {
            UUID uuid = UUID.randomUUID();
            PlayerProfile profile = Bukkit.createPlayerProfile(uuid, shortProfileName(uuid));
            PlayerTextures textures = profile.getTextures();
            textures.setSkin(skinUrl);
            profile.setTextures(textures);
            meta.setOwnerProfile(profile);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static void applyGameProfile(SkullMeta meta, URL skinUrl) throws ReflectiveOperationException {
        UUID uuid = UUID.randomUUID();
        GameProfile profile = new GameProfile(uuid, shortProfileName(uuid));
        String textureJson = "{\"textures\":{\"SKIN\":{\"url\":\"" + skinUrl + "\"}}}";
        String encoded = Base64.getEncoder().encodeToString(textureJson.getBytes(StandardCharsets.UTF_8));
        profile.getProperties().put("textures", new Property("textures", encoded));

        Field profileField = findField(meta.getClass(), "profile");
        profileField.setAccessible(true);
        profileField.set(meta, profile);
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static String shortProfileName(UUID uuid) {
        return "pn" + uuid.toString().replace("-", "").substring(0, 14);
    }

    private static URL extractSkinUrl(String input) {
        if (input == null) return null;
        String s = input.trim();
        if (s.isEmpty()) return null;

        try {
            Matcher m1 = TEXTURE_URL.matcher(s);
            if (m1.find()) return new URL(m1.group());

            if (s.matches("[A-Fa-f0-9]{32,}")) {
                return new URL("http://textures.minecraft.net/texture/" + s);
            }

            String decoded = new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8);
            Matcher m2 = TEXTURE_URL.matcher(decoded);
            if (m2.find()) return new URL(m2.group());
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }
}
