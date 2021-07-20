package com.songoda.epicanchors.listener;

import com.songoda.core.compatibility.CompatibleHand;
import com.songoda.core.compatibility.CompatibleParticleHandler;
import com.songoda.core.compatibility.CompatibleSound;
import com.songoda.epicanchors.Anchor;
import com.songoda.epicanchors.AnchorManager;
import com.songoda.epicanchors.EpicAnchors;
import com.songoda.epicanchors.files.Settings;
import com.songoda.epicanchors.guis.AnchorGui;
import com.songoda.epicanchors.guis.DestroyConfirmationGui;
import com.songoda.epicanchors.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class AnchorListener implements Listener {
    private final EpicAnchors plugin;

    public AnchorListener(EpicAnchors instance) {
        this.plugin = instance;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        ItemStack item = e.getItemInHand();

        if (item.hasItemMeta() &&
                item.getItemMeta().hasDisplayName() &&
                Settings.MATERIAL.getMaterial().getMaterial() == e.getBlock().getType()) {
            if (!plugin.getAnchorManager().isReady(e.getBlock().getWorld())) {
                e.setCancelled(true);
                e.getPlayer().sendMessage("Anchors are still being initialized - Please wait a moment");    // TODO
            } else {
                int ticksLeft = AnchorManager.getTicksFromItem(item);

                if (ticksLeft != 0) {
                    boolean dropOnErr = e.getPlayer().getGameMode() != GameMode.CREATIVE;

                    plugin.getAnchorManager().createAnchor(e.getBlock().getLocation(), e.getPlayer().getUniqueId(), ticksLeft,
                            (ex, result) -> {
                                if (ex != null) {
                                    Utils.logException(this.plugin, ex, "SQLite");
                                    e.getPlayer().sendMessage("Error creating anchor!"); // TODO

                                    Bukkit.getScheduler().runTask(this.plugin, () -> {
                                        if (Settings.MATERIAL.getMaterial().getMaterial() == e.getBlock().getType()) {
                                            e.getBlock().setType(Material.AIR);
                                        }

                                        if (dropOnErr) {
                                            e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation(),
                                                    plugin.getAnchorManager().createAnchorItem(ticksLeft, item.getType()));
                                        }
                                    });
                                }
                            });
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null ||
                !plugin.getAnchorManager().isReady(e.getClickedBlock().getWorld())) return;

        Player p = e.getPlayer();
        Anchor anchor = plugin.getAnchorManager().getAnchor(e.getClickedBlock());

        if (anchor != null) {
            e.setCancelled(true);

            if (p.hasPermission("EpicAnchors.admin") ||
                    this.plugin.getAnchorManager().hasAccess(anchor, p)) {
                if (e.getAction() == Action.LEFT_CLICK_BLOCK) { // Destroy anchor
                    this.plugin.getGuiManager().showGUI(e.getPlayer(),
                            new DestroyConfirmationGui(this.plugin, anchor, (ex, result) -> {
                                if (result) {
                                    BlockBreakEvent blockBreakEvent = new BlockBreakEvent(e.getClickedBlock(), p);
                                    Bukkit.getPluginManager().callEvent(blockBreakEvent);

                                    if (!blockBreakEvent.isCancelled()) {
                                        plugin.getAnchorManager().destroyAnchor(anchor);
                                    }
                                }
                            }));
                } else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) { // Manage anchor
                    ItemStack item = CompatibleHand.MAIN_HAND.getItem(e.getPlayer());
                    int itemTicks = AnchorManager.getTicksFromItem(item);

                    if (itemTicks != 0) {
                        if (!anchor.isInfinite()) {
                            if (itemTicks == -1) {
                                anchor.setTicksLeft(-1);
                            } else {
                                anchor.addTicksLeft(itemTicks);
                            }

                            if (p.getGameMode() != GameMode.CREATIVE) {
                                CompatibleHand.MAIN_HAND.takeItem(p, 1);
                            }

                            p.playSound(p.getLocation(), CompatibleSound.ENTITY_PLAYER_LEVELUP.getSound(), .6F, 15);
                            CompatibleParticleHandler.spawnParticles(CompatibleParticleHandler.ParticleType.getParticle(Settings.PARTICLE_UPGRADE.getString()),
                                    anchor.getLocation().add(.5, .5, .5), 100, .5, .5, .5);
                        }
                    } else {
                        plugin.getGuiManager().showGUI(p, new AnchorGui(plugin, anchor));
                    }
                }
            } else {
                plugin.getLocale().getMessage("event.general.nopermission").sendMessage(p);
            }
        }
    }
}
