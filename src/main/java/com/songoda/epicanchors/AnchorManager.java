package com.songoda.epicanchors;

import com.songoda.core.SongodaPlugin;
import com.songoda.core.compatibility.CompatibleMaterial;
import com.songoda.core.compatibility.CompatibleParticleHandler;
import com.songoda.core.compatibility.CompatibleSound;
import com.songoda.core.hooks.HologramManager;
import com.songoda.core.nms.NmsManager;
import com.songoda.core.nms.nbt.NBTItem;
import com.songoda.core.utils.TextUtils;
import com.songoda.core.utils.TimeUtils;
import com.songoda.epicanchors.api.AnchorAccessCheck;
import com.songoda.epicanchors.files.DataManager;
import com.songoda.epicanchors.files.Settings;
import com.songoda.epicanchors.utils.Callback;
import com.songoda.epicanchors.utils.UpdateCallback;
import com.songoda.epicanchors.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class AnchorManager {
    private static final String ERR_WORLD_NOT_READY = "EpicAnchors has not finished initializing that world yet";
    private static final String NBT_TICKS_KEY = "EpicAnchors_Ticks".toLowerCase();

    private final SongodaPlugin plugin;
    private final DataManager dataManager;

    private final Map<World, Set<Anchor>> anchors = new HashMap<>(3);
    private final Set<Player> visualizedChunk = new HashSet<>();
    private final List<AnchorAccessCheck> accessChecks = new LinkedList<>();

    private boolean ready;

    public AnchorManager(SongodaPlugin plugin, DataManager dataManager) {
        this.plugin = Objects.requireNonNull(plugin);
        this.dataManager = Objects.requireNonNull(dataManager);
    }

    protected void saveAll() {
        for (Set<Anchor> anchorSet : anchors.values()) {
            this.dataManager.updateAnchors(anchorSet, null);
        }
    }

    protected void deInitAll() {
        for (World world : anchors.keySet().toArray(new World[0])) {
            deInitAnchors(world);
        }
    }

    protected void initAnchorsAsync(@NotNull World world, @Nullable UpdateCallback callback) {
        if (this.anchors.containsKey(world)) {
            if (callback != null) {
                callback.accept(null);
            }

            return;
        }

        long start = System.nanoTime();

        this.dataManager.getAnchors(world, (ex, result) -> {
            if (ex == null) {
                this.anchors.computeIfAbsent(world, k -> new HashSet<>());

                for (Anchor anchor : result) {
                    anchor.init(this.plugin);

                    this.anchors.computeIfAbsent(anchor.getWorld(), k -> new HashSet<>())
                            .add(anchor);
                }

                long end = System.nanoTime();
                this.plugin.getLogger().info("Initialized anchors in world '" + world.getName() + "' " +
                        "(" + TimeUnit.NANOSECONDS.toMillis(end - start) + "ms)");

                if (callback != null) {
                    callback.accept(null);
                }
            } else {
                if (callback != null) {
                    callback.accept(ex);
                } else {
                    Utils.logException(this.plugin, ex, "SQLite");
                }
            }
        });
    }

    protected void deInitAnchors(@NotNull World world) {
        Set<Anchor> tmpAnchors = this.anchors.remove(world);

        if (tmpAnchors != null) {
            this.dataManager.updateAnchors(tmpAnchors, null);

            for (Anchor anchor : tmpAnchors) {
                anchor.deInit(this.plugin);
            }
        }
    }

    protected void setReady() {
        this.ready = true;

        Bukkit.getScheduler().runTaskTimer(plugin, this::saveAll, 20L * 60 * 5, 20L * 60 * 5);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isReady(World world) {
        return this.ready && anchors.containsKey(world);
    }

    /* Getter */

    public Anchor[] getAnchors(@NotNull World world) {
        Set<Anchor> set = anchors.get(world);

        if (set != null) {
            return set.toArray(new Anchor[0]);
        }

        return new Anchor[0];
    }

    public @Nullable Anchor getAnchor(@NotNull Block b) {
        if (!isReady(b.getWorld())) {
            throw new IllegalStateException(ERR_WORLD_NOT_READY);
        }

        Location bLoc = b.getLocation();
        Set<Anchor> set = anchors.get(b.getWorld());

        if (set != null) {
            for (Anchor anchor : set) {
                if (anchor.getLocation().equals(bLoc)) {
                    return anchor;
                }
            }
        }

        return null;
    }

    public boolean isAnchor(@NotNull Block b) {
        return getAnchor(b) != null;
    }

    public boolean hasAnchor(@NotNull Chunk chunk) {
        if (!isReady(chunk.getWorld())) {
            throw new IllegalStateException(ERR_WORLD_NOT_READY);
        }

        Set<Anchor> set = anchors.get(chunk.getWorld());

        if (set != null) {
            for (Anchor anchor : set) {
                if (anchor.getChunk().equals(chunk)) {
                    return true;
                }
            }
        }

        return false;
    }

    @SuppressWarnings("unused")
    public List<Anchor> searchAnchors(Location center, double searchRadius) {
        return searchAnchors(center, searchRadius, false);
    }

    public List<Anchor> searchAnchors(Location center, double searchRadius, boolean ignoreHeight) {
        List<Anchor> result = new ArrayList<>();

        if (ignoreHeight) {
            center = center.clone();
            center.setY(0);
        }

        for (Anchor anchor : getAnchors(center.getWorld())) {
            Location loc = anchor.getLocation();

            if (ignoreHeight) {
                loc.setY(0);
            }

            if (center.distance(loc) <= searchRadius) {
                result.add(anchor);
            }
        }

        return result;
    }

    /* Create 'n Destroy */

    /**
     * Creates a new anchor at a given location
     *
     * @param loc   The block location for the anchor
     * @param ticks The amount of ticks the anchor lives or -1 for infinite
     */
    public void createAnchor(@NotNull Location loc, @NotNull UUID owner, int ticks, @Nullable Callback<Anchor> callback) {
        if (!isReady(loc.getWorld())) {
            throw new IllegalStateException(ERR_WORLD_NOT_READY);
        }

        this.dataManager.insertAnchorAsync(loc, Objects.requireNonNull(owner), ticks, (ex, anchor) -> {
            if (ex != null) {
                if (callback != null) {
                    callback.accept(ex, null);
                } else {
                    Utils.logException(this.plugin, ex, "SQLite");
                }
            } else {
                Bukkit.getScheduler().runTask(this.plugin, () -> {
                    Block b = loc.getBlock();
                    b.setType(Settings.MATERIAL.getMaterial().getMaterial());

                    anchors.computeIfAbsent(anchor.getWorld(), k -> new HashSet<>())
                            .add(anchor);

                    updateHologram(anchor);

                    if (callback != null) {
                        callback.accept(null, anchor);
                    }
                });
            }
        });
    }

    public void destroyAnchor(@NotNull Anchor anchor) {
        destroyAnchor(anchor, false);
    }

    public void destroyAnchor(@NotNull Anchor anchor, boolean forceSkipItemDrop) {
        if (!isReady(anchor.getWorld())) {
            throw new IllegalStateException(ERR_WORLD_NOT_READY);
        }

        for (Set<Anchor> value : anchors.values()) {
            value.remove(anchor);
        }

        removeHologram(anchor);

        Location anchorLoc = anchor.getLocation();
        Block anchorBlock = anchorLoc.getBlock();
        Material anchorMaterial = anchorBlock.getType();

        if (anchorBlock.getType() == Settings.MATERIAL.getMaterial().getMaterial()) {
            anchorBlock.setType(Material.AIR);
        }

        // Drop anchor as an item
        if (!forceSkipItemDrop &&
                Settings.ALLOW_ANCHOR_BREAKING.getBoolean() &&
                (anchor.isInfinite() || anchor.getTicksLeft() >= 20)) {
            anchor.getWorld().dropItemNaturally(anchorLoc, createAnchorItem(anchor.getTicksLeft(), anchorMaterial));
        }

        // Particles & Sound
        anchor.getWorld().playSound(anchorLoc, CompatibleSound.ENTITY_GENERIC_EXPLODE.getSound(), 10, 10);
        CompatibleParticleHandler.spawnParticles(CompatibleParticleHandler.ParticleType.getParticle(Settings.PARTICLE_DESTROY.getString()),
                anchor.getLocation().add(.5, .5, .5), 100, .5, .5, .5);

        anchor.deInit(this.plugin);
        this.dataManager.deleteAnchorAsync(anchor);
    }

    /* Anchor access */

    @SuppressWarnings("unused")
    public void registerAccessCheck(AnchorAccessCheck accessCheck) {
        if (!accessChecks.contains(accessCheck)) {
            // Adding at the start of the list makes sure the default check is
            accessChecks.add(accessCheck);
        }
    }

    /**
     * @param accessCheck The {@link AnchorAccessCheck} to remove
     *
     * @return true if the {@link AnchorAccessCheck} has been found and removed, false otherwise
     */
    @SuppressWarnings("unused")
    public boolean unregisterAccessCheck(AnchorAccessCheck accessCheck) {
        return accessChecks.remove(accessCheck);
    }

    public boolean hasAccess(@NotNull Anchor anchor, @NotNull OfflinePlayer p) {
        return hasAccess(anchor, p.getUniqueId());
    }

    /**
     * Checks if a player has access to an Anchor. By default only the owner has access to an Anchor.
     * <br>
     * Other plugins can grant access to other players (e.g. friends).
     * <br>
     * Legacy anchors without an owner automatically grant access to all players.
     *
     * @return true if the player may access the Anchor, false otherwise
     *
     * @see #registerAccessCheck(AnchorAccessCheck)
     */
    public boolean hasAccess(@NotNull Anchor anchor, @NotNull UUID uuid) {
        if (anchor.isLegacy() || anchor.getOwner().equals(uuid)) return true;

        for (AnchorAccessCheck accessCheck : this.accessChecks) {
            if (accessCheck.check(anchor, uuid)) {
                return true;
            }
        }

        return false;
    }

    /* Anchor item */

    public ItemStack createAnchorItem(int ticks) {
        return createAnchorItem(ticks, Settings.MATERIAL.getMaterial());
    }

    public ItemStack createAnchorItem(int ticks, Material material) {
        return createAnchorItem(ticks, CompatibleMaterial.getMaterial(material));
    }

    public ItemStack createAnchorItem(int ticks, CompatibleMaterial material) {
        if (ticks <= 0 && ticks != -1) throw new IllegalArgumentException();

        ItemStack item = material.getItem();
        ItemMeta meta = item.getItemMeta();

        assert meta != null;
        meta.setDisplayName(formatAnchorText(ticks, false));
        meta.setLore(TextUtils.formatText(Settings.LORE.getString().split("\r?\n")));
        item.setItemMeta(meta);

        NBTItem nbtItem = NmsManager.getNbt().of(item);
        nbtItem.set(NBT_TICKS_KEY, ticks);

        return nbtItem.finish();
    }

    public static int getTicksFromItem(ItemStack item) {
        NBTItem nbtItem = NmsManager.getNbt().of(item);

        if (nbtItem.has(NBT_TICKS_KEY)) {
            return nbtItem.getInt(NBT_TICKS_KEY);
        }

        // Legacy code (pre v2) to stay cross-version compatible
        if (Settings.MATERIAL.getMaterial().getMaterial() == item.getType()) {

            if (nbtItem.has("ticks")) {
                int result = nbtItem.getInt("ticks");

                return result == -99 ? -1 : result;
            }

            // Tries to get the ticks remaining from hidden text
            if (item.hasItemMeta() &&
                    item.getItemMeta().hasDisplayName() &&
                    item.getItemMeta().getDisplayName().contains(":")) {
                try {
                    int result = Integer.parseInt(item.getItemMeta().getDisplayName().replace("§", "").split(":")[0]);

                    return result == -99 ? -1 : result;
                } catch (NumberFormatException ignore) {
                }
            }
        }

        return 0;
    }

    /* Chunk visualization */

    public boolean toggleChunkVisualized(Player p) {
        boolean visualize = !hasChunksVisualized(p);

        setChunksVisualized(p, visualize);

        return visualize;
    }

    public void setChunksVisualized(Player p, boolean visualize) {
        if (visualize) {
            this.visualizedChunk.add(p);
        } else {
            this.visualizedChunk.remove(p);
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean hasChunksVisualized(Player p) {
        return this.visualizedChunk.contains(p);
    }

    /* Holograms */

    public void updateHolograms(List<Anchor> anchors) {
        // are holograms enabled?
        if (!Settings.HOLOGRAMS.getBoolean() || !HologramManager.getManager().isEnabled()) return;

        Map<Location, List<String>> hologramData = new HashMap<>(anchors.size());

        for (Anchor anchor : anchors) {
            hologramData.put(anchor.getLocation(),
                    Collections.singletonList(formatAnchorText(anchor.getTicksLeft(), true)));
        }

        // Create the holograms
        HologramManager.bulkUpdateHolograms(hologramData);
    }

    private void updateHologram(Anchor anchor) {
        updateHolograms(Collections.singletonList(anchor));
    }

    private String formatAnchorText(int ticks, boolean shorten) {
        String remaining;

        if (ticks < 0) {
            remaining = this.plugin.getLocale().getMessage("Infinite").getMessage();
        } else {
            long millis = (ticks / 20L) * 1000L;

            remaining = TimeUtils.makeReadable(millis);

            if (shorten && millis > 60 * 5 * 1000 /* 5 minutes */ &&
                    remaining.charAt(remaining.length() - 1) == 's') {
                int i = remaining.lastIndexOf(' ');

                remaining = remaining.substring(0, i);
            }

            if (remaining.isEmpty()) {
                remaining = "0s";
            }
        }

        return TextUtils.formatText(Settings.NAME_TAG.getString().replace("{REMAINING}", remaining));
    }

    private static void removeHologram(Anchor anchor) {
        HologramManager.removeHologram(anchor.getLocation());
    }
}
