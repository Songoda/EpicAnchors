package com.songoda.epicanchors.listeners;

import com.songoda.epicanchors.EpicAnchors;
import com.songoda.epicanchors.anchor.Anchor;
import com.songoda.epicanchors.settings.Settings;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

public class BlockListeners implements Listener {

    private EpicAnchors plugin;

    public BlockListeners(EpicAnchors instance) {
        this.plugin = instance;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {

        ItemStack item = event.getItemInHand();

        if (!item.hasItemMeta()
                || !item.getItemMeta().hasDisplayName()
                || Settings.MATERIAL.getMaterial().getMaterial() != event.getBlock().getType()
                || plugin.getTicksFromItem(item) == 0) return;

        Anchor anchor = new Anchor(event.getBlock().getLocation(), null, plugin.getTicksFromItem(item));

        if (plugin.getTicksFromItem(item) == -99) {
            anchor.setInfinite(true);
        }

        plugin.getAnchorManager().addAnchor(event.getBlock().getLocation(), anchor);
        plugin.updateHologram(anchor);
    }
}
