package com.songoda.epicanchors.anchor;

import com.songoda.core.compatibility.ServerVersion;
import com.songoda.core.hooks.EconomyManager;
import com.songoda.epicanchors.EpicAnchors;
import com.songoda.epicanchors.settings.Settings;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class Anchor {

    private Location location;
    private String worldName;   // May be null if location.getWorld() is null too

    private final int chunkX;
    private final int chunkZ;

    private int ticksLeft;
    private boolean isInfinite;

    public Anchor(Location location, String worldName, int ticksLeft) {
        this.location = location;
        this.worldName = worldName;

        this.chunkX = location.getBlockX() >> 4;
        this.chunkZ = location.getBlockZ() >> 4;

        this.ticksLeft = ticksLeft;
        this.isInfinite = (ticksLeft == -99);
    }

    public void addTime(String type, Player player) {
        EpicAnchors instance = EpicAnchors.getInstance();

        if (type.equals("ECO")) {
            if (!EconomyManager.isEnabled()) return;
            double cost = instance.getConfig().getDouble("Main.Economy Cost");
            if (EconomyManager.hasBalance(player, cost)) {
                EconomyManager.withdrawBalance(player, cost);
            } else {
                instance.getLocale().getMessage("event.upgrade.cannotafford").sendPrefixedMessage(player);
                return;
            }
        } else if (type.equals("XP")) {
            int cost = instance.getConfig().getInt("Main.XP Cost");
            if (player.getLevel() >= cost || player.getGameMode() == GameMode.CREATIVE) {
                if (player.getGameMode() != GameMode.CREATIVE) {
                    player.setLevel(player.getLevel() - cost);
                }
            } else {
                instance.getLocale().getMessage("event.upgrade.cannotafford").sendPrefixedMessage(player);
                return;
            }
        }

        ticksLeft = ticksLeft + 20 * 60 * 30;
        Sound sound = ServerVersion.isServerVersionAtLeast(ServerVersion.V1_9) ? Sound.ENTITY_PLAYER_LEVELUP : Sound.valueOf("LEVEL_UP");
        player.playSound(player.getLocation(), sound, 0.6F, 15.0F);

        if (ServerVersion.isServerVersionAtLeast(ServerVersion.V1_9))
            player.getWorld().spawnParticle(Particle.SPELL_WITCH, getLocation().add(.5, .5, .5), 100, .5, .5, .5);
    }

    public void bust() {
        EpicAnchors plugin = EpicAnchors.getInstance();

        if (Settings.ALLOW_ANCHOR_BREAKING.getBoolean() && getTicksLeft() > 0) {
            ItemStack item = plugin.makeAnchorItem(getTicksLeft());
            getWorld().dropItemNaturally(getLocation(), item);
        }

        Location loc = getLocation();

        plugin.clearHologram(this);
        loc.getBlock().setType(Material.AIR);

        if (ServerVersion.isServerVersionAtLeast(ServerVersion.V1_9))
            getWorld().spawnParticle(Particle.LAVA, loc.clone().add(.5, .5, .5), 5, 0, 0, 0, 5);

        getWorld().playSound(loc, ServerVersion.isServerVersionAtLeast(ServerVersion.V1_9)
                ? Sound.ENTITY_GENERIC_EXPLODE : Sound.valueOf("EXPLODE"), 10, 10);

        plugin.getAnchorManager().removeAnchor(loc);
    }

    public Location getLocation() {
        if (location.getWorld() == null) {
            location.setWorld(Bukkit.getWorld(worldName));
        }

        return location.clone();
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    public World getWorld() {
        return getLocation().getWorld();
    }

    public String getWorldName() {
        if (location.getWorld() == null) {
            return this.worldName;
        }

        return location.getWorld().getName();
    }

    public int getTicksLeft() {
        return ticksLeft;
    }

    public void setTicksLeft(int ticksLeft) {
        this.ticksLeft = ticksLeft;
    }

    public boolean isInfinite() {
        return isInfinite;
    }

    public void setInfinite(boolean infinite) {
        isInfinite = infinite;
    }
}
