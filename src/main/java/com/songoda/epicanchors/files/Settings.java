package com.songoda.epicanchors.files;

import com.songoda.core.compatibility.CompatibleMaterial;
import com.songoda.core.compatibility.CompatibleParticleHandler;
import com.songoda.core.configuration.Config;
import com.songoda.core.configuration.ConfigSetting;
import com.songoda.core.hooks.EconomyManager;
import com.songoda.epicanchors.EpicAnchors;

public class Settings {
    private Settings() {
        throw new IllegalStateException("Utility class");
    }

    public static final Config config = EpicAnchors.getPlugin(EpicAnchors.class).getCoreConfig();

    public static final ConfigSetting NAME_TAG = new ConfigSetting(config, "Main.Name Tag",
            "&6Anchor &8(&7{REMAINING}&8)",
            "The anchor name tag used on the item and in the hologram.");

    public static final ConfigSetting LORE = new ConfigSetting(config, "Main.Anchor Lore",
            "&7Place down to keep that chunk\n&7loaded until the time runs out.",
            "The lore on the anchor item.");

    public static final ConfigSetting MATERIAL = new ConfigSetting(config, "Main.Anchor Block Material",
            CompatibleMaterial.END_PORTAL_FRAME.name(), "The material an anchor is represented with?");

    public static final ConfigSetting ADD_TIME_WITH_XP = new ConfigSetting(config, "Main.Add Time With XP", true,
            "Should players be able to add time to their anchors by using experience?");

    public static final ConfigSetting XP_COST = new ConfigSetting(config, "Main.XP Cost", 10,
            "The cost in experience levels to add 30 minutes to an anchor.");

    public static final ConfigSetting ADD_TIME_WITH_ECONOMY = new ConfigSetting(config, "Main.Add Time With Economy", true,
            "Should players be able to add time to their anchors by using economy?");

    public static final ConfigSetting ECONOMY_COST = new ConfigSetting(config, "Main.Economy Cost", 5000.0,
            "The cost in economy to add 30 minutes to an anchor.");

    public static final ConfigSetting ALLOW_ANCHOR_BREAKING = new ConfigSetting(config, "Main.Allow Anchor Breaking", false,
            "Should players be able to break anchors and get an item dropped?");

    public static final ConfigSetting HOLOGRAMS = new ConfigSetting(config, "Main.Holograms", true,
            "Toggle holograms showing above anchors.");

    @SuppressWarnings("unchecked")
    public static final ConfigSetting ECONOMY_PLUGIN = new ConfigSetting(config, "Main.Economy", EconomyManager.getEconomy() == null ? "Vault" : EconomyManager.getEconomy().getName(),
            "Which economy plugin should be used?",
            "Supported plugins you have installed: \"" + String.join(", ", EconomyManager.getManager().getRegisteredPlugins()) + "\".");

    public static final ConfigSetting ECO_ICON = new ConfigSetting(config, "Interfaces.Economy Icon", CompatibleMaterial.SUNFLOWER.name(),
            "Item to be displayed as the icon for economy upgrades.");

    public static final ConfigSetting XP_ICON = new ConfigSetting(config, "Interfaces.XP Icon", CompatibleMaterial.EXPERIENCE_BOTTLE.name(),
            "Item to be displayed as the icon for XP upgrades.");

    public static final ConfigSetting GLASS_TYPE_1 = new ConfigSetting(config, "Interfaces.Glass Type 1", CompatibleMaterial.GRAY_STAINED_GLASS_PANE.name());
    public static final ConfigSetting GLASS_TYPE_2 = new ConfigSetting(config, "Interfaces.Glass Type 2", CompatibleMaterial.BLUE_STAINED_GLASS_PANE.name());
    public static final ConfigSetting GLASS_TYPE_3 = new ConfigSetting(config, "Interfaces.Glass Type 3", CompatibleMaterial.LIGHT_BLUE_STAINED_GLASS_PANE.name());

    public static final ConfigSetting PARTICLE_DESTROY = new ConfigSetting(config, "Particles.Destroy",
            CompatibleParticleHandler.ParticleType.LAVA.name());
    public static final ConfigSetting PARTICLE_UPGRADE = new ConfigSetting(config, "Particles.Upgrade",
            CompatibleParticleHandler.ParticleType.SPELL_WITCH.name());
    public static final ConfigSetting PARTICLE_VISUALIZER = new ConfigSetting(config, "Particles.Visualizer",
            CompatibleParticleHandler.ParticleType.VILLAGER_HAPPY.name());

    public static final ConfigSetting LANGUAGE = new ConfigSetting(config, "System.Language Mode", "en_US",
            "The enabled language file.",
            "More language files (if available) can be found in the plugins data folder.");

    /**
     * In order to set dynamic economy comment correctly, this needs to be
     * called after EconomyManager load
     */
    public static void setupConfig() {
        config.load();
        config.setAutoremove(true)
                .setAutosave(true);

        // convert glass pane settings
        int color;
        if ((color = GLASS_TYPE_1.getInt(-1)) != -1) {
            config.set(GLASS_TYPE_1.getKey(), CompatibleMaterial.getGlassPaneColor(color).name());
        }
        if ((color = GLASS_TYPE_2.getInt(-1)) != -1) {
            config.set(GLASS_TYPE_2.getKey(), CompatibleMaterial.getGlassPaneColor(color).name());
        }
        if ((color = GLASS_TYPE_3.getInt(-1)) != -1) {
            config.set(GLASS_TYPE_3.getKey(), CompatibleMaterial.getGlassPaneColor(color).name());
        }

        // convert economy settings
        if (config.getBoolean("Economy.Use Vault Economy") && EconomyManager.getManager().isEnabled("Vault")) {
            config.set("Main.Economy", "Vault");
        } else if (config.getBoolean("Economy.Use Reserve Economy") && EconomyManager.getManager().isEnabled("Reserve")) {
            config.set("Main.Economy", "Reserve");
        } else if (config.getBoolean("Economy.Use Player Points Economy") && EconomyManager.getManager().isEnabled("PlayerPoints")) {
            config.set("Main.Economy", "PlayerPoints");
        }

        config.saveChanges();
    }
}
