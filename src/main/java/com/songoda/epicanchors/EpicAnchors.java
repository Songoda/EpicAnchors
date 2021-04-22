package com.songoda.epicanchors;

import com.songoda.core.SongodaCore;
import com.songoda.core.SongodaPlugin;
import com.songoda.core.commands.CommandManager;
import com.songoda.core.compatibility.CompatibleMaterial;
import com.songoda.core.compatibility.ServerVersion;
import com.songoda.core.configuration.Config;
import com.songoda.core.gui.GuiManager;
import com.songoda.core.hooks.EconomyManager;
import com.songoda.core.hooks.HologramManager;
import com.songoda.core.nms.NmsManager;
import com.songoda.core.nms.nbt.NBTItem;
import com.songoda.core.utils.TextUtils;
import com.songoda.epicanchors.anchor.Anchor;
import com.songoda.epicanchors.anchor.AnchorManager;
import com.songoda.epicanchors.commands.*;
import com.songoda.epicanchors.listeners.BlockListeners;
import com.songoda.epicanchors.listeners.InteractListeners;
import com.songoda.epicanchors.listeners.PortalListeners;
import com.songoda.epicanchors.settings.Settings;
import com.songoda.epicanchors.tasks.AnchorTask;
import com.songoda.epicanchors.tasks.VisualizeTask;
import com.songoda.epicanchors.utils.Methods;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.PluginManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EpicAnchors extends SongodaPlugin {

    private static EpicAnchors INSTANCE;

    private final Config dataFile = new Config(this, "data.yml");

    private final GuiManager guiManager = new GuiManager(this);
    private AnchorManager anchorManager;
    private CommandManager commandManager;

    public static EpicAnchors getInstance() {
        return INSTANCE;
    }

    @Override
    public void onPluginLoad() {
        INSTANCE = this;
    }

    @Override
    public void onPluginDisable() {
        saveToFile();
        HologramManager.removeAllHolograms();
    }

    @Override
    public void onDataLoad() {}

    @Override
    public void onPluginEnable() {
        // Run Songoda Updater
        SongodaCore.registerPlugin(this, 31, CompatibleMaterial.END_PORTAL_FRAME);

        // Load Economy
        EconomyManager.load();

        // Setup Config
        Settings.setupConfig();
        this.setLocale(Settings.LANGUGE_MODE.getString(), false);

        // Set economy preference
        EconomyManager.getManager().setPreferredHook(Settings.ECONOMY_PLUGIN.getString());

        // Register commands
        this.commandManager = new CommandManager(this);
        this.commandManager.addCommand(new CommandEpicAnchors(this))
                .addSubCommands(
                        new CommandGive(this),
                        new CommandReload(this),
                        new CommandSettings(this, guiManager),
                        new CommandShow(this)
                );

        anchorManager = new AnchorManager();
        Bukkit.getScheduler().runTaskLater(this, this::loadAnchorsFromFile, 5L);

        // Start tasks
        new AnchorTask(this);
        new VisualizeTask(this);

        // Register Listeners
        guiManager.init();
        PluginManager pluginManager = Bukkit.getPluginManager();
        pluginManager.registerEvents(new BlockListeners(this), this);
        pluginManager.registerEvents(new InteractListeners(this), this);
        if (ServerVersion.isServerVersionAtLeast(ServerVersion.V1_9))
            pluginManager.registerEvents(new PortalListeners(this), this);

        // Register Hologram Plugin
        HologramManager.load(this);

        if (Settings.HOLOGRAMS.getBoolean())
            loadHolograms();

        Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::saveToFile, 6000, 6000);
    }

    @Override
    public void onConfigReload() {
        this.setLocale(Settings.LANGUGE_MODE.getString(), true);
        this.loadAnchorsFromFile();
    }

    @Override
    public List<Config> getExtraConfig() {
        return null;
    }

    void loadHolograms() {
        Collection<Anchor> anchors = getAnchorManager().getAnchors().values();
        if (anchors.size() == 0) return;

        for (Anchor anchor : anchors) {
            if (anchor.getWorld() == null) continue;
            updateHologram(anchor);
        }
    }

    public void clearHologram(Anchor anchor) {
        HologramManager.removeHologram(correctHeight(anchor.getLocation()));
    }

    public void updateHologram(Anchor anchor) {
        updateHolograms(Collections.singletonList(anchor));
    }

    public void updateHolograms(List<Anchor> anchors) {
        // are holograms enabled?
        if (!Settings.HOLOGRAMS.getBoolean() || !HologramManager.getManager().isEnabled()) return;

        Map<Location, List<String>> hologramData = new HashMap<>(anchors.size());

        for (Anchor anchor : anchors) {
            // verify that this is a anchor
            if (anchor.getLocation().getBlock().getType() != Settings.MATERIAL.getMaterial().getMaterial()) continue;

            // grab the name
            String name = Methods.formatName(anchor.getTicksLeft()).trim();
            Location location = correctHeight(anchor.getLocation());

            hologramData.put(location, Collections.singletonList(name));
        }

        // create the holograms
        HologramManager.bulkUpdateHolograms(hologramData);
    }

    private Location correctHeight(Location location) {
        if (location.getBlock().getType() != CompatibleMaterial.END_PORTAL_FRAME.getMaterial())
            location.add(0, .05, 0);
        return location;
    }

    private void loadAnchorsFromFile() {
        dataFile.load();
        if (!dataFile.contains("Anchors")) return;
        for (String locationStr : dataFile.getConfigurationSection("Anchors").getKeys(false)) {
            Location location = Methods.unserializeLocation(locationStr);

            String worldName = location.getWorld() == null ? locationStr.substring(0, locationStr.indexOf(':')) : null;
            int ticksLeft = dataFile.getInt("Anchors." + locationStr + ".ticksLeft");

            anchorManager.addAnchor(location, new Anchor(location, worldName, ticksLeft));
        }
    }

    private void saveToFile() {
        dataFile.clearConfig(true);
        for (Anchor anchor : anchorManager.getAnchors().values()) {
            String locationStr = Methods.serializeLocation(anchor.getLocation(), anchor.getWorldName());
            dataFile.set("Anchors." + locationStr + ".ticksLeft", anchor.getTicksLeft());
        }
        dataFile.save();
    }

    public int getTicksFromItem(ItemStack item) {
        NBTItem nbtItem = NmsManager.getNbt().of(item);
        if (nbtItem.has("ticks")) {
            return nbtItem.getNBTObject("ticks").asInt();
        }

        // Legacy code. Tries to get the ticks remaining from hidden text.
        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return 0;
        if (item.getItemMeta().getDisplayName().contains(":")) {
            return Integer.parseInt(item.getItemMeta().getDisplayName().replace("\u00A7", "").split(":")[0]);
        }
        return 0;
    }

    public ItemStack makeAnchorItem(int ticks) {
        ItemStack item = Settings.MATERIAL.getMaterial().getItem();
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Methods.formatName(ticks));
        ArrayList<String> lore = new ArrayList<>();
        String[] parts = Settings.LORE.getString().split("\\|");
        for (String line : parts) {
            lore.add(TextUtils.formatText(line));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);

        NBTItem nbtItem = NmsManager.getNbt().of(item);
        nbtItem.set("ticks", ticks);
        return nbtItem.finish();
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }

    public GuiManager getGuiManager() {
        return guiManager;
    }

    public AnchorManager getAnchorManager() {
        return anchorManager;
    }
}
