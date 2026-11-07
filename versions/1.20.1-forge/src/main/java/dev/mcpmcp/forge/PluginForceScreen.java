package dev.mcpmcp.forge;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
/** A local confirmation; guarded from MCP exactly like the settings list. */
public final class PluginForceScreen extends ConfirmScreen implements McpmcpSettingsScreen {
 public PluginForceScreen(BooleanConsumer callback,String id){super(callback,Component.translatable("mcpmcp.plugins.force_title"),Component.translatable("mcpmcp.plugins.force_confirm",id),Component.translatable("mcpmcp.plugins.force"),Component.translatable("gui.cancel"));}
}
