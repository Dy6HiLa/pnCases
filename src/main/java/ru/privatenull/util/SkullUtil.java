package ru.privatenull.util;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SkullUtil {

    private static final Pattern TEXTURE_URL = Pattern.compile(
            "https?://textures\\.minecraft\\.net/texture/[A-Za-z0-9]+"
    );

    public static ItemStack fromBase64(String base64OrUrlOrHash, String displayName) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);

        head.editMeta(SkullMeta.class, meta -> {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));

            try {
                URL skinUrl = extractSkinUrl(base64OrUrlOrHash);
                if (skinUrl == null) return;

                UUID uuid = UUID.randomUUID();
                String name = "pn" + uuid.toString().replace("-", "").substring(0, 14);
                PlayerProfile profile = org.bukkit.Bukkit.getServer().createPlayerProfile(uuid, name);

                PlayerTextures textures = profile.getTextures();
                textures.setSkin(skinUrl);
                profile.setTextures(textures);

                meta.setOwnerProfile(profile);
            } catch (Exception ignored) {
            }
        });

        return head;
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
        }

        return null;
    }
}