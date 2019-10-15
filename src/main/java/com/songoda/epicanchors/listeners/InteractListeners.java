package com.songoda.epicanchors.listeners;

import com.songoda.core.compatibility.CompatibleMaterial;
import com.songoda.core.compatibility.CompatibleParticleHandler;
import com.songoda.core.compatibility.CompatibleSound;
import com.songoda.core.utils.ItemUtils;
import com.songoda.epicanchors.EpicAnchors;
import com.songoda.epicanchors.anchor.Anchor;
import com.songoda.epicanchors.gui.GUIOverview;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class InteractListeners implements Listener {

    private final EpicAnchors instance;

    public InteractListeners(EpicAnchors instance) {
        this.instance = instance;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;

        Anchor anchor = instance.getAnchorManager().getAnchor(event.getClickedBlock().getLocation());

        if (anchor == null) return;
        event.setCancelled(true);

        Player player = event.getPlayer();

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            BlockBreakEvent blockBreakEvent = new BlockBreakEvent(event.getClickedBlock(), player);
            Bukkit.getPluginManager().callEvent(blockBreakEvent);
            if (blockBreakEvent.isCancelled())
                return;

            anchor.bust();
            return;
        }

        ItemStack item = player.getItemInHand();

        if (instance.getCoreConfig().getMaterial("Main.Anchor Block Material", CompatibleMaterial.AIR).matches(item)) {
            if (instance.getTicksFromItem(item) == 0) return;

            anchor.setTicksLeft(anchor.getTicksLeft() + instance.getTicksFromItem(item));

            if (player.getGameMode() != GameMode.CREATIVE)
                ItemUtils.takeActiveItem(player);

            player.playSound(player.getLocation(), CompatibleSound.ENTITY_PLAYER_LEVELUP.getSound(), 0.6F, 15.0F);

            CompatibleParticleHandler.spawnParticles(CompatibleParticleHandler.ParticleType.SPELL_WITCH, anchor.getLocation().add(.5, .5, .5), 100, .5, .5, .5);

        } else {
            instance.getGuiManager().showGUI(player, new GUIOverview(EpicAnchors.getInstance(), anchor, player));
        }
    }

}
