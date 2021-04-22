package com.songoda.epicanchors.tasks;

import com.songoda.core.compatibility.CompatibleParticleHandler;
import com.songoda.core.compatibility.ServerVersion;
import com.songoda.epicanchors.EpicAnchors;
import com.songoda.epicanchors.anchor.Anchor;
import com.songoda.epicanchors.settings.Settings;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AnchorTask extends BukkitRunnable {

    private static EpicAnchors plugin;

    private final Map<Location, Integer> delays = new HashMap<>();

    private Class<?> clazzCraftEntity;

    private Method methodTick, methodGetHandle;

    private Field fieldCurrentTick, fieldActivatedTick;

    private final boolean epicSpawners;

    public AnchorTask(EpicAnchors plug) {
        plugin = plug;
        epicSpawners = Bukkit.getPluginManager().getPlugin("EpicSpawners") != null;

        try {
            String ver = Bukkit.getServer().getClass().getPackage().getName().substring(23);

            Class<?> clazzMinecraftServer = Class.forName("net.minecraft.server." + ver + ".MinecraftServer");
            Class<?> clazzEntity = Class.forName("net.minecraft.server." + ver + ".Entity");
            clazzCraftEntity = Class.forName("org.bukkit.craftbukkit." + ver + ".entity.CraftEntity");

            if (ServerVersion.isServerVersionAtLeast(ServerVersion.V1_13))
                methodTick = clazzEntity.getDeclaredMethod("tick");
            else if (ServerVersion.isServerVersion(ServerVersion.V1_12))
                methodTick = clazzEntity.getDeclaredMethod("B_");
            else if (ServerVersion.isServerVersion(ServerVersion.V1_11))
                methodTick = clazzEntity.getDeclaredMethod("A_");
            else if (ServerVersion.isServerVersionAtLeast(ServerVersion.V1_9))
                methodTick = clazzEntity.getDeclaredMethod("m");
            else
                methodTick = clazzEntity.getDeclaredMethod("t_");

            methodGetHandle = clazzCraftEntity.getDeclaredMethod("getHandle");

            fieldCurrentTick = clazzMinecraftServer.getDeclaredField("currentTick");
            fieldActivatedTick = clazzEntity.getDeclaredField("activatedTick");
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
        }

        this.runTaskTimer(plugin, 0, 3);
    }

    private void doParticle(Location anchorLocation) {
        CompatibleParticleHandler.redstoneParticles(anchorLocation.clone().add(.5, .5, .5),
                255, 255, 255, 1.2F, 5, .75F);
    }

    @Override
    public void run() {
        Set<Chunk> chunks = new HashSet<>();
        List<Anchor> toUpdate = new ArrayList<>();

        for (Anchor anchor : new ArrayList<>(plugin.getAnchorManager().getAnchors().values())) {
            Location location = anchor.getLocation();

            if (location.getWorld() == null) continue;

            doParticle(location);
            toUpdate.add(anchor);

            if (location.getBlock().getType() != Settings.MATERIAL.getMaterial().getMaterial())
                continue;

            Chunk chunk = location.getChunk();

            boolean processChunk = chunks.add(chunk);

            if (processChunk) {
                if (!chunk.isLoaded()) {
                    chunk.load();
                }

                // Load entities
                entityLoop:
                for (Entity entity : chunk.getEntities()) {
                    if (!(entity instanceof LivingEntity) || entity instanceof Player) continue;

                    for (Player inWorld : entity.getWorld().getPlayers()) {
                        // Not sure where the 32 is from
                        if (inWorld.getLocation().distanceSquared(entity.getLocation()) <= 32) {
                            continue entityLoop;
                        }
                    }

                    try {
                        Object objCraftEntity = clazzCraftEntity.cast(entity);
                        Object objEntity = methodGetHandle.invoke(objCraftEntity);

                        fieldActivatedTick.set(objEntity, fieldCurrentTick.getLong(objEntity));
                        methodTick.invoke(objEntity);
                    } catch (ReflectiveOperationException e) {
                        e.printStackTrace();
                    }
                }
            }

            int ticksLeft = anchor.getTicksLeft();

            if (!anchor.isInfinite()) {
                anchor.setTicksLeft(ticksLeft - 3);
            }

            if (ticksLeft <= 0 && !anchor.isInfinite()) {
                anchor.bust();
                chunk.unload();
                continue;
            }

            if (!processChunk || !epicSpawners ||
                    com.songoda.epicspawners.EpicSpawners.getInstance().getSpawnerManager() == null) continue;

            com.songoda.epicspawners.EpicSpawners.getInstance().getSpawnerManager().getSpawners().stream()
                    .filter(spawner -> spawner.getWorld().isChunkLoaded(spawner.getX() >> 4, spawner.getZ() >> 4)
                            && chunk == spawner.getLocation().getChunk()).forEach(spawner -> {
                Block block = spawner.getLocation().getBlock();

                if (!delays.containsKey(block.getLocation())) {
                    delays.put(block.getLocation(), spawner.updateDelay());
                    return;
                }
                int delay = delays.get(block.getLocation());
                delay -= 1;
                delays.put(block.getLocation(), delay);
                if (delay <= 0) {
                    spawner.spawn();
                    delays.remove(block.getLocation());
                }
            });
        }

        plugin.updateHolograms(toUpdate);
    }
}
