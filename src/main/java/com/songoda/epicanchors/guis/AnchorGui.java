package com.songoda.epicanchors.guis;

import com.songoda.core.compatibility.CompatibleMaterial;
import com.songoda.core.compatibility.CompatibleParticleHandler;
import com.songoda.core.compatibility.CompatibleSound;
import com.songoda.core.gui.Gui;
import com.songoda.core.gui.GuiUtils;
import com.songoda.core.hooks.EconomyManager;
import com.songoda.core.utils.TextUtils;
import com.songoda.core.utils.TimeUtils;
import com.songoda.epicanchors.Anchor;
import com.songoda.epicanchors.EpicAnchors;
import com.songoda.epicanchors.files.Settings;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class AnchorGui extends Gui {
    private final EpicAnchors plugin;
    private final Anchor anchor;

    public AnchorGui(EpicAnchors plugin, Anchor anchor) {
        this.plugin = plugin;
        this.anchor = anchor;

        setRows(3);
        setTitle(TextUtils.formatText(plugin.getLocale().getMessage("interface.anchor.title").getMessage()));

        constructGUI();
        runPreparedGuiTask(this.plugin, this, this.anchor);
    }

    private void constructGUI() {
        prepareGui(this.plugin, this, this.anchor);

        if (Settings.ADD_TIME_WITH_XP.getBoolean()) {
            String itemName = plugin.getLocale().getMessage("interface.button.addtimewithxp").getMessage();
            String itemLore = plugin.getLocale().getMessage("interface.button.addtimewithxplore")
                    .processPlaceholder("cost", Settings.XP_COST.getInt())
                    .getMessage();

            setButton(11,
                    GuiUtils.createButtonItem(Settings.XP_ICON.getMaterial(CompatibleMaterial.EXPERIENCE_BOTTLE), itemName, itemLore),
                    event -> buyTime(anchor, event.player, false));
        }

        if (EconomyManager.isEnabled() && Settings.ADD_TIME_WITH_ECONOMY.getBoolean()) {
            String itemName = plugin.getLocale().getMessage("interface.button.addtimewitheconomy").getMessage();
            String itemLore = plugin.getLocale().getMessage("interface.button.addtimewitheconomylore")
                    // EconomyManager#formatEconomy adds its own prefix/suffix
                    .processPlaceholder("cost", EconomyManager.formatEconomy(Settings.ECONOMY_COST.getDouble()))
                    .getMessage();

            setButton(15,
                    GuiUtils.createButtonItem(Settings.ECO_ICON.getMaterial(CompatibleMaterial.SUNFLOWER), itemName, itemLore),
                    event -> buyTime(anchor, event.player, true));
        }
    }

    private void buyTime(Anchor anchor, Player p, boolean eco) {
        if (anchor.isInfinite()) {
            this.plugin.getLocale().getMessage("interface.button.infinite").sendPrefixedMessage(p);
        } else {
            boolean success = false;

            if (eco) {
                double cost = Settings.ECONOMY_COST.getDouble();

                success = EconomyManager.withdrawBalance(p, cost);
            } else {
                int cost = Settings.XP_COST.getInt();

                if (p.getLevel() >= cost || p.getGameMode() == GameMode.CREATIVE) {
                    if (p.getGameMode() != GameMode.CREATIVE) {
                        p.setLevel(p.getLevel() - cost);
                    }

                    success = true;
                }
            }

            if (success) {
                anchor.addTicksLeft(20 * 60 * 30);  // 30 minutes

                p.playSound(p.getLocation(), CompatibleSound.ENTITY_PLAYER_LEVELUP.getSound(), 0.6F, 15);
                CompatibleParticleHandler.spawnParticles(CompatibleParticleHandler.ParticleType.getParticle(Settings.PARTICLE_UPGRADE.getString()),
                        anchor.getLocation().add(.5, .5, .5), 100, .5, .5, .5);
            } else {
                this.plugin.getLocale().getMessage("event.upgrade.cannotafford").sendPrefixedMessage(p);
            }
        }
    }

    protected static void prepareGui(EpicAnchors plugin, Gui gui, Anchor anchor) {
        ItemStack glass1 = GuiUtils.getBorderItem(Settings.GLASS_TYPE_1.getMaterial());
        ItemStack glass2 = GuiUtils.getBorderItem(Settings.GLASS_TYPE_2.getMaterial());
        ItemStack glass3 = GuiUtils.getBorderItem(Settings.GLASS_TYPE_3.getMaterial());

        gui.setDefaultItem(glass1);

        gui.mirrorFill(0, 0, true, true, glass2);
        gui.mirrorFill(0, 1, true, true, glass2);
        gui.mirrorFill(0, 2, true, true, glass3);
        gui.mirrorFill(1, 0, false, true, glass2);
        gui.mirrorFill(1, 1, false, true, glass3);

        String itemName = plugin.getLocale().getMessage("interface.anchor.smalltitle").getMessage();
        String itemLore = anchor.isInfinite() ?
                ChatColor.GRAY + "Infinite" :
                ChatColor.GRAY + TimeUtils.makeReadable((long) ((anchor.getTicksLeft() / 20.0) * 1000)) + " remaining.";

        gui.setItem(13, GuiUtils.createButtonItem(plugin.getAnchorManager().createAnchorItem(
                anchor.getTicksLeft(), anchor.getLocation().getBlock().getType()),
                itemName, itemLore));
    }

    protected static void runPreparedGuiTask(EpicAnchors plugin, Gui gui, Anchor anchor) {
        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (anchor.getTicksLeft() == 0) {
                gui.close();
            } else {
                String itemName = plugin.getLocale().getMessage("interface.anchor.smalltitle").getMessage();
                String itemLore = anchor.isInfinite() ?
                        ChatColor.GRAY + "Infinite" :
                        ChatColor.GRAY + TimeUtils.makeReadable((long) ((anchor.getTicksLeft() / 20.0) * 1000)) + " remaining.";

                gui.updateItem(13, itemName, itemLore);
            }
        }, 0, 20);

        gui.setOnClose(action -> Bukkit.getScheduler().cancelTask(taskId));
    }
}
