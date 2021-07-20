package com.songoda.epicanchors.tasks;

import com.songoda.core.nms.NmsManager;
import com.songoda.epicanchors.Anchor;
import com.songoda.epicanchors.AnchorManager;
import com.songoda.epicanchors.EpicAnchors;
import com.songoda.epicanchors.utils.Utils;
import com.songoda.epicanchors.utils.WorldUtils;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

/**
 * More information about what types of game ticks there are and what does what: https://minecraft.fandom.com/wiki/Tick
 */
public class AnchorTask extends BukkitRunnable {
    private static final int TASK_INTERVAL = 3;

    private final EpicAnchors plugin;
    private final AnchorManager anchorManager;

    private boolean randomTicksFailed;
    private boolean spawnerTicksFailed;

    public AnchorTask(EpicAnchors plugin) {
        this.plugin = plugin;
        this.anchorManager = plugin.getAnchorManager();
    }

    public void startTask() {
        runTaskTimer(this.plugin, TASK_INTERVAL, TASK_INTERVAL);
    }

    @Override
    public void run() {
        try {
            for (World world : Bukkit.getWorlds()) {
                if (!this.anchorManager.isReady(world)) return;

                int randomTicks = WorldUtils.getRandomTickSpeed(world) * TASK_INTERVAL;

                Set<Chunk> alreadyTicked = new HashSet<>();
                Anchor[] anchorsInWorld = this.anchorManager.getAnchors(world);
                List<Anchor> toUpdateHolo = new ArrayList<>(anchorsInWorld.length);

                // Skip all chunks with players in them
                for (Player pInWorld : world.getPlayers()) {
                    alreadyTicked.add(pInWorld.getLocation().getChunk());
                }

                for (Anchor anchor : anchorsInWorld) {
                    Chunk chunk = anchor.getChunk();

                    // Tick the anchor's chunk (but not multiple times)
                    if (alreadyTicked.add(chunk)) {
                        // Having a chunk loaded takes care of entities and weather (https://minecraft.fandom.com/wiki/Tick#Chunk_tick)
                        if (!chunk.isLoaded()) {
                            // Loading an already loaded chunk still fires the ChunkLoadEvent and might have a huge
                            // impact on performance if other plugins do not expect that either...

                            WorldUtils.loadAnchoredChunk(chunk, this.plugin);
                        }

                        if (!randomTicksFailed) {
                            try {
                                NmsManager.getWorld().randomTickChunk(chunk, randomTicks);
                            } catch (NoSuchFieldException | IllegalAccessException ex) {
                                this.plugin.getLogger().log(Level.FINER, ex,
                                        () -> "Failed to do random ticks on this server implementation(/version) - " +
                                                "Skipping further random ticks.");

                                randomTicksFailed = true;
                            }
                        }

                        if (!spawnerTicksFailed) {
                            try {
                                NmsManager.getWorld().tickInactiveSpawners(chunk, TASK_INTERVAL);
                            } catch (NoSuchFieldException | IllegalAccessException ex) {
                                this.plugin.getLogger().log(Level.FINER, ex,
                                        () -> "Failed to do spawner ticks on this server implementation(/version) - " +
                                                "Skipping further spawner ticks.");

                                spawnerTicksFailed = true;
                            }
                        }
                    }

                    // TODO: Only update hologram if a player is nearby
                    //       Simplify player location to chunks to potentially group players
                    //       Use the server view distance to calculate minimum distance to count as not-nearby

                    // Destroy anchors and queue hologram update
                    if (!anchor.isInfinite()) {
                        int ticksLeft = anchor.removeTicksLeft(3);

                        if (ticksLeft == 0) {
                            this.anchorManager.destroyAnchor(anchor);
                        } else {
                            toUpdateHolo.add(anchor);
                        }
                    } else {
                        toUpdateHolo.add(anchor);
                    }
                }

                // Update holograms on queued anchors
                anchorManager.updateHolograms(toUpdateHolo);
            }
        } catch (Exception ex) {
            Utils.logException(this.plugin, ex);
        }
    }
}
