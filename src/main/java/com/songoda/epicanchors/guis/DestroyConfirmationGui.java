package com.songoda.epicanchors.guis;

import com.songoda.core.compatibility.CompatibleMaterial;
import com.songoda.core.gui.Gui;
import com.songoda.core.gui.GuiUtils;
import com.songoda.core.gui.methods.Closable;
import com.songoda.core.utils.TextUtils;
import com.songoda.epicanchors.Anchor;
import com.songoda.epicanchors.EpicAnchors;
import com.songoda.epicanchors.files.Settings;
import com.songoda.epicanchors.utils.Callback;

public class DestroyConfirmationGui extends Gui {
    private final EpicAnchors plugin;
    private final Anchor anchor;

    private Callback<Boolean> handler;

    public DestroyConfirmationGui(EpicAnchors plugin, Anchor anchor, Callback<Boolean> callback) {
        this.plugin = plugin;
        this.anchor = anchor;

        this.handler = (ex, result) -> {
            this.handler = null;
            this.close();

            callback.accept(ex, result);
        };

        this.setRows(3);
        this.setTitle(TextUtils.formatText(plugin.getLocale().getMessage("interface.anchor.title").getMessage()));

        constructGUI();
        AnchorGui.runPreparedGuiTask(this.plugin, this, this.anchor);

        Closable currClosable = this.closer;
        this.closer = event -> {
            currClosable.onClose(event);

            if (this.handler != null) {
                this.handler.accept(null, false);
            }
        };
    }

    private void constructGUI() {
        AnchorGui.prepareGui(this.plugin, this, this.anchor);

        String cancelLore = plugin.getLocale().getMessage("interface.button.cancelDestroyLore").getMessage();
        String confirmLore = plugin.getLocale().getMessage("interface.button." +
                (Settings.ALLOW_ANCHOR_BREAKING.getBoolean() ? "confirmDestroyLore" : "confirmDestroyLoreNoDrops"))
                .getMessage();

        setButton(11, GuiUtils.createButtonItem(CompatibleMaterial.RED_TERRACOTTA,
                plugin.getLocale().getMessage("interface.button.cancelDestroy").getMessage(),
                cancelLore.isEmpty() ? new String[0] : new String[] {cancelLore}),
                event -> this.handler.accept(null, false));

        setButton(15, GuiUtils.createButtonItem(CompatibleMaterial.GREEN_TERRACOTTA,
                plugin.getLocale().getMessage("interface.button.confirmDestroy").getMessage(),
                confirmLore.isEmpty() ? new String[0] : new String[] {confirmLore}),
                event -> this.handler.accept(null, true));
    }
}
