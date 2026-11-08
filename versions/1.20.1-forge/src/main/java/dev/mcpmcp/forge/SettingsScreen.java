package dev.mcpmcp.forge;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class SettingsScreen extends Screen implements McpmcpSettingsScreen {
 private final Screen parent;
 private Button mode,transport,debug;
 public SettingsScreen(Screen parent){super(Component.translatable("mcpmcp.settings.title"));this.parent=parent;}
 protected void init(){
  String[] modes={"NORMAL","HACK","CHEAT","HACK_CHEAT"},transports={"MIXED","PACKET","SIMULATE"};
  int w=Math.min(260,width-32),x=(width-w)/2,y=height/2-80;
  mode=addRenderableWidget(Button.builder(Component.empty(),b->change(c->c.mode=modes[(java.util.Arrays.asList(modes).indexOf(c.mode)+1)%modes.length])).bounds(x,y,w,20).build());
  transport=addRenderableWidget(Button.builder(Component.empty(),b->change(c->c.transport=transports[(java.util.Arrays.asList(transports).indexOf(c.transport)+1)%transports.length])).bounds(x,y+24,w,20).build());
  debug=addRenderableWidget(Button.builder(Component.empty(),b->change(c->c.debug=!c.debug)).bounds(x,y+48,w,20).build());
  addRenderableWidget(Button.builder(Component.translatable("mcpmcp.settings.plugins"),b->minecraft.setScreen(new PluginListScreen(this))).bounds(x,y+72,w,20).build());
  addRenderableWidget(Button.builder(Component.translatable("mcpmcp.server.title"),b->minecraft.setScreen(new ServerSettingsScreen(this))).bounds(x,y+96,w,20).build());
  addRenderableWidget(Button.builder(Component.translatable("gui.done"),b->onClose()).bounds(x,y+128,w,20).build());refreshLabels();
 }
 private void change(java.util.function.Consumer<Configuration> edit){UiSettings.change(Runtime::config,Runtime::saveConfig,edit);refreshLabels();}
 private void refreshLabels(){var c=Runtime.config().publicView();mode.setMessage(Component.translatable("mcpmcp.settings.mode",Component.translatable("mcpmcp.mode."+c.get("mode").getAsString().toLowerCase(java.util.Locale.ROOT))));transport.setMessage(Component.translatable("mcpmcp.settings.transport",Component.translatable("mcpmcp.transport."+c.get("transport").getAsString().toLowerCase(java.util.Locale.ROOT))));debug.setMessage(Component.translatable("mcpmcp.settings.debug",Component.translatable(c.get("debug").getAsBoolean()?"options.on":"options.off")));}
 public void render(GuiGraphics g,int mx,int my,float partial){refreshLabels();renderBackground(g);g.drawCenteredString(font,title,width/2,height/2-108,0xffffff);super.render(g,mx,my,partial);}
 public void onClose(){minecraft.setScreen(parent);}
}
