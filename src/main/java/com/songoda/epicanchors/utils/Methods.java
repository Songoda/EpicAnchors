package com.songoda.epicanchors.utils;

import com.songoda.core.utils.TextUtils;
import com.songoda.core.utils.TimeUtils;
import com.songoda.epicanchors.EpicAnchors;
import com.songoda.epicanchors.settings.Settings;
import org.bukkit.*;
import org.bukkit.block.Block;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Methods {

    private static Map<String, Location> serializeCache = new HashMap<>();

    public static String formatName(int ticks) {

        String remaining = TimeUtils.makeReadable((ticks / 20L) * 1000L);

        String name = Settings.NAMETAG.getString().replace("{REMAINING}", (ticks <= 0)
                ? EpicAnchors.getInstance().getLocale().getMessage("infinite").getMessage() : remaining);

        return TextUtils.formatText(name);
    }

    /**
     * Serializes the location specified.
     *
     * @param location The location that is to be saved.
     * @return The serialized data.
     */
    public static String serializeLocation(Location location, String worldName) {
        if (location == null)
            return "";

        String w = location.getWorld() != null ? location.getWorld().getName() : worldName;
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();

        String str = w + ":" + x + ":" + y + ":" + z;
        str = str.replace(".0", "")
                .replace("/", "");

        return str;
    }

    /**
     * Deserializes a location from the string.
     *
     * @param str The string to parse.
     * @return The location that was serialized in the string.
     */
    public static Location unserializeLocation(String str) {
        if (str == null || str.equals(""))
            return null;
        if (serializeCache.containsKey(str)) {
            return serializeCache.get(str).clone();
        }
        String cacheKey = str;
        str = str.replace("y:", ":").replace("z:", ":").replace("w:", "").replace("x:", ":").replace("/", ".");
        List<String> args = Arrays.asList(str.split("\\s*:\\s*"));

        World world = Bukkit.getWorld(args.get(0));
        double x = Double.parseDouble(args.get(1)), y = Double.parseDouble(args.get(2)), z = Double.parseDouble(args.get(3));
        Location location = new Location(world, x, y, z, 0, 0);
        serializeCache.put(cacheKey, location.clone());
        return location;
    }

    public static boolean isInt(String number) {
        if (number != null && !number.equals("")) {
            try {
                Integer.parseInt(number);
                return true;
            } catch (NumberFormatException var2) {
                return false;
            }
        } else {
            return false;
        }
    }
}
