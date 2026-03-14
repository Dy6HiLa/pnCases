package ru.privatenull.integrations;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.joml.Vector3f;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.pnCases;
import ru.privatenull.util.ItemFactory;

import java.lang.reflect.Method;
import java.util.*;

public final class FancyHologramsHook {

    private static final String FH_PLUGIN_CLASS  = "de.oliver.fancyholograms.api.FancyHologramsPlugin";
    private static final String FH_HOLOGRAM_CLASS = "de.oliver.fancyholograms.api.hologram.Hologram";
    private static final String FH_TEXT_DATA_CLASS  = "de.oliver.fancyholograms.api.data.TextHologramData";
    private static final String FH_ITEM_DATA_CLASS  = "de.oliver.fancyholograms.api.data.ItemHologramData";
    private static final String FH_BLOCK_DATA_CLASS = "de.oliver.fancyholograms.api.data.BlockHologramData";

    private final pnCases plugin;

    private boolean available;
    private Object hologramManager;

    private final Set<String> managedNames = new HashSet<>();

    public FancyHologramsHook(pnCases plugin) {
        this.plugin = plugin;
        detect();
    }

    public void shutdown() {
        clearManaged();
        available = false;
        hologramManager = null;
    }

    public void clearManaged() {
        if (!available || hologramManager == null) {
            managedNames.clear();
            return;
        }
        for (String name : new ArrayList<>(managedNames)) {
            removeHologram(name);
        }
        managedNames.clear();
    }

    public void syncCases(Collection<CaseDefinition> defs) {
        detect();
        clearManaged();
        if (!available || hologramManager == null) return;

        for (CaseDefinition def : defs) {
            try {
                ConfigurationSection cs = plugin.getConfig().getConfigurationSection("cases." + def.name());
                if (cs == null) continue;
                ConfigurationSection hs = cs.getConfigurationSection("hologram");
                if (hs == null) continue;
                if (!hs.getBoolean("enabled", false)) continue;
                createCaseHologram(def, hs);
            } catch (Throwable t) {
                plugin.getLogger().warning("FancyHolograms: ошибка создания голограммы для кейса '" + def.name() + "': " + t.getMessage());
            }
        }
    }

    public void hideCase(CaseDefinition def) {
        detect();
        if (!available || hologramManager == null || def == null) return;
        String holoName = "pncases_" + def.name();
        removeHologram(holoName);
        managedNames.remove(holoName);
    }

    public void showCase(CaseDefinition def) {
        detect();
        if (!available || hologramManager == null || def == null) return;
        try {
            ConfigurationSection cs = plugin.getConfig().getConfigurationSection("cases." + def.name());
            if (cs == null) return;
            ConfigurationSection hs = cs.getConfigurationSection("hologram");
            if (hs == null) return;
            if (!hs.getBoolean("enabled", false)) return;
            createCaseHologram(def, hs);
        } catch (Throwable t) {
            plugin.getLogger().warning("FancyHolograms: ошибка showCase для '" + def.name() + "': " + t.getMessage());
        }
    }

    private void detect() {
        if (Bukkit.getPluginManager().getPlugin("FancyHolograms") == null) {
            available = false;
            hologramManager = null;
            return;
        }
        try {
            Class<?> fh = Class.forName(FH_PLUGIN_CLASS);
            boolean enabled = (boolean) fh.getMethod("isEnabled").invoke(null);
            if (!enabled) {
                available = false;
                hologramManager = null;
                return;
            }
            Object api = fh.getMethod("get").invoke(null);
            Object manager = api.getClass().getMethod("getHologramManager").invoke(api);
            available = manager != null;
            hologramManager = manager;
        } catch (Throwable ignored) {
            available = false;
            hologramManager = null;
        }
    }

    private void createCaseHologram(CaseDefinition def, ConfigurationSection hs) throws Exception {
        String type = hs.getString("type", "TEXT");
        if (type == null) type = "TEXT";
        type = type.toUpperCase(Locale.ROOT);

        Location loc = def.blockLocation().clone().add(0.5, 0.0, 0.5);

        double ox = hs.getDouble("x", 0.0);
        double oy = hs.contains("height") ? hs.getDouble("height") : hs.getDouble("y", 1.8);
        double oz = hs.getDouble("z", 0.0);
        ConfigurationSection off = hs.getConfigurationSection("offset");
        if (off != null) {
            ox = off.getDouble("x", ox);
            oy = off.contains("height") ? off.getDouble("height") : off.getDouble("y", oy);
            oz = off.getDouble("z", oz);
        }
        loc.add(ox, oy, oz);

        String holoName = "pncases_" + def.name();
        removeHologram(holoName);

        Object data = switch (type) {
            case "ITEM"  -> buildItemData(holoName, loc, hs);
            case "BLOCK" -> buildBlockData(holoName, loc, hs);
            default      -> buildTextData(holoName, loc, def, hs);
        };

        invoke(data, "setPersistent", boolean.class, false);
        applyCommonStyles(data, hs);

        Object hologram = invoke(hologramManager, "create",
                Class.forName("de.oliver.fancyholograms.api.data.HologramData"), data);
        invoke(hologramManager, "addHologram", Class.forName(FH_HOLOGRAM_CLASS), hologram);

        managedNames.add(holoName);
    }

    private Object buildTextData(String name, Location loc, CaseDefinition def, ConfigurationSection hs) throws Exception {
        Class<?> cls = Class.forName(FH_TEXT_DATA_CLASS);
        Object data = cls.getConstructor(String.class, Location.class).newInstance(name, loc);

        List<String> lines = hs.getStringList("lines");
        if (lines == null || lines.isEmpty()) {
            String one = hs.getString("line", null);
            if (one != null && !one.isBlank()) lines = List.of(one);
        }
        if (lines == null || lines.isEmpty()) {
            lines = List.of("&e" + def.name());
        }

        List<String> out = new ArrayList<>(lines.size());
        for (String s : lines) out.add(applyPlaceholders(s, def));
        invoke(data, "setText", List.class, out);

        if (hs.contains("text_shadow")) invoke(data, "setTextShadow", boolean.class, hs.getBoolean("text_shadow"));
        if (hs.contains("see_through")) invoke(data, "setSeeThrough", boolean.class, hs.getBoolean("see_through"));

        if (hs.contains("text_alignment")) {
            String a = hs.getString("text_alignment", "CENTER");
            if (a != null) {
                try {
                    TextDisplay.TextAlignment align = TextDisplay.TextAlignment.valueOf(a.toUpperCase(Locale.ROOT));
                    invoke(data, "setTextAlignment", TextDisplay.TextAlignment.class, align);
                } catch (Exception ignored) {}
            }
        }

        if (hs.contains("background")) {
            Color c = parseColor(hs.getString("background", ""));
            if (c != null) invoke(data, "setBackground", Color.class, c);
        }

        if (hs.contains("text_update_interval")) {
            int interval = Math.max(0, hs.getInt("text_update_interval", 0));
            invoke(data, "setTextUpdateInterval", int.class, interval);
        }

        return data;
    }

    private Object buildItemData(String name, Location loc, ConfigurationSection hs) throws Exception {
        Class<?> cls = Class.forName(FH_ITEM_DATA_CLASS);
        Object data = cls.getConstructor(String.class, Location.class).newInstance(name, loc);

        ItemStack it = ItemFactory.fromSection(hs.getConfigurationSection("item"));
        if (it == null) {
            String mat = hs.getString("material", "CHEST");
            Material m = mat == null ? null : Material.matchMaterial(mat);
            if (m == null) m = Material.CHEST;
            it = new ItemStack(m);
        }
        invoke(data, "setItemStack", ItemStack.class, it);
        return data;
    }

    private Object buildBlockData(String name, Location loc, ConfigurationSection hs) throws Exception {
        Class<?> cls = Class.forName(FH_BLOCK_DATA_CLASS);
        Object data = cls.getConstructor(String.class, Location.class).newInstance(name, loc);

        String mat = hs.getString("block", hs.getString("block_material", "CHEST"));
        Material m = mat == null ? null : Material.matchMaterial(mat);
        if (m == null) m = Material.CHEST;
        invoke(data, "setBlock", Material.class, m);
        return data;
    }

    private void applyCommonStyles(Object data, ConfigurationSection hs) {
        if (hs.contains("billboard")) {
            String bb = hs.getString("billboard", "CENTER");
            if (bb != null) {
                try {
                    Display.Billboard billboard = Display.Billboard.valueOf(bb.toUpperCase(Locale.ROOT));
                    invoke(data, "setBillboard", Display.Billboard.class, billboard);
                } catch (Throwable ignored) {}
            }
        }

        if (hs.contains("shadow_radius")) invoke(data, "setShadowRadius", float.class, (float) hs.getDouble("shadow_radius"));
        if (hs.contains("shadow_strength")) invoke(data, "setShadowStrength", float.class, (float) hs.getDouble("shadow_strength"));

        if (hs.contains("visibility_distance")) {
            int dist = Math.max(1, hs.getInt("visibility_distance"));
            invoke(data, "setVisibilityDistance", int.class, dist);
        }

        if (hs.contains("interpolation_duration")) {
            int d = Math.max(0, hs.getInt("interpolation_duration"));
            invoke(data, "setInterpolationDuration", int.class, d);
        }

        Vector3f scale = parseVector(hs, "scale");
        if (scale != null) invokeQuiet(data, "setScale", Vector3f.class, scale);

        Vector3f translation = parseVector(hs, "translation");
        if (translation != null) invokeQuiet(data, "setTranslation", Vector3f.class, translation);

        ConfigurationSection br = hs.getConfigurationSection("brightness");
        if (br != null && (br.contains("block") || br.contains("sky"))) {
            try {
                int block = clamp(br.getInt("block", 15), 0, 15);
                int sky = clamp(br.getInt("sky", 15), 0, 15);
                Display.Brightness b = new Display.Brightness(block, sky);
                invoke(data, "setBrightness", Display.Brightness.class, b);
            } catch (Throwable ignored) {}
        }
    }

    private void removeHologram(String name) {
        if (!available || hologramManager == null) return;
        try {
            Method get = hologramManager.getClass().getMethod("getHologram", String.class);
            Object opt = get.invoke(hologramManager, name);
            if (!(opt instanceof Optional<?> optional)) return;
            if (optional.isEmpty()) return;
            Object hologram = optional.get();
            Method remove = hologramManager.getClass().getMethod("removeHologram", Class.forName(FH_HOLOGRAM_CLASS));
            remove.invoke(hologramManager, hologram);
        } catch (Throwable ignored) {}
    }

    private static String applyPlaceholders(String s, CaseDefinition def) {
        if (s == null) return "";
        String guiRaw = def.guiTitle() == null ? "" : def.guiTitle();
        String guiPlain = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', guiRaw));
        return s
                .replace("{case}", def.name())
                .replace("{gui_title}", guiRaw)
                .replace("{gui_title_plain}", guiPlain == null ? "" : guiPlain)
                .replace("{world}", def.blockLocation().getWorld() == null ? "world" : def.blockLocation().getWorld().getName())
                .replace("{x}", String.valueOf(def.blockLocation().getBlockX()))
                .replace("{y}", String.valueOf(def.blockLocation().getBlockY()))
                .replace("{z}", String.valueOf(def.blockLocation().getBlockZ()));
    }

    private static Color parseColor(String s) {
        if (s == null) return null;
        String v = s.trim();
        if (v.isEmpty() || v.equalsIgnoreCase("none") || v.equalsIgnoreCase("null")) return null;
        try {
            if (v.startsWith("#")) v = v.substring(1);
            if (v.matches("^[0-9a-fA-F]{6}$")) {
                return Color.fromRGB(Integer.parseInt(v, 16));
            }
        } catch (Exception ignored) {}
        try {
            String[] parts = v.split("[,; ]+");
            if (parts.length >= 3) {
                int r = clamp(Integer.parseInt(parts[0]), 0, 255);
                int g = clamp(Integer.parseInt(parts[1]), 0, 255);
                int b = clamp(Integer.parseInt(parts[2]), 0, 255);
                return Color.fromRGB(r, g, b);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static Vector3f parseVector(ConfigurationSection root, String key) {
        if (root == null || key == null) return null;
        if (root.isConfigurationSection(key)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) return null;
            if (!s.contains("x") && !s.contains("y") && !s.contains("z")) return null;
            return new Vector3f(
                    (float) s.getDouble("x", 0.0),
                    (float) s.getDouble("y", 0.0),
                    (float) s.getDouble("z", 0.0)
            );
        }
        if (root.isList(key)) {
            List<?> raw = root.getList(key);
            if (raw == null || raw.size() < 3) return null;
            try {
                return new Vector3f(
                        (float) Double.parseDouble(String.valueOf(raw.get(0))),
                        (float) Double.parseDouble(String.valueOf(raw.get(1))),
                        (float) Double.parseDouble(String.valueOf(raw.get(2)))
                );
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static Object invoke(Object target, String method, Class<?> paramType, Object arg) throws Exception {
        Method m = target.getClass().getMethod(method, paramType);
        return m.invoke(target, arg);
    }

    private static void invoke(Object target, String method, Class<?> paramType, boolean arg) throws Exception {
        target.getClass().getMethod(method, paramType).invoke(target, arg);
    }

    private static void invoke(Object target, String method, Class<?> paramType, float arg) {
        try { target.getClass().getMethod(method, paramType).invoke(target, arg); } catch (Throwable ignored) {}
    }

    private static void invoke(Object target, String method, Class<?> paramType, int arg) {
        try { target.getClass().getMethod(method, paramType).invoke(target, arg); } catch (Throwable ignored) {}
    }

    private static void invokeQuiet(Object target, String method, Class<?> paramType, Object arg) {
        try { target.getClass().getMethod(method, paramType).invoke(target, arg); } catch (Throwable ignored) {}
    }
}