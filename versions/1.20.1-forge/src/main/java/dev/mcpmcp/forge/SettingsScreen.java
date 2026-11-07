package dev.mcpmcp.forge;
import net.minecraft.client.gui.screens.Screen;import net.minecraft.client.gui.components.Button;import net.minecraft.client.gui.GuiGraphics;import net.minecraft.network.chat.Component;
public final class SettingsScreen extends Screen {
 private final Screen parent;private Button mode,transport,debug;
 public SettingsScreen(Screen p){super(Component.literal("MCP Minecraft Plus"));parent=p;}
 protected void init(){String[] modes={"NORMAL","HACK","CHEAT","HACK_CHEAT"},transports={"MIXED","PACKET","SIMULATE"};int x=width/2-130,y=height/2-65;
  mode=addRenderableWidget(Button.builder(Component.empty(),b->change(c->c.mode=modes[(java.util.Arrays.asList(modes).indexOf(c.mode)+1)%modes.length])).bounds(x,y,260,20).build());
  transport=addRenderableWidget(Button.builder(Component.empty(),b->change(c->c.transport=transports[(java.util.Arrays.asList(transports).indexOf(c.transport)+1)%transports.length])).bounds(x,y+24,260,20).build());
  debug=addRenderableWidget(Button.builder(Component.empty(),b->change(c->c.debug=!c.debug)).bounds(x,y+48,260,20).build());
  addRenderableWidget(Button.builder(Component.literal("Done"),b->onClose()).bounds(x,y+86,260,20).build());refreshLabels();}
 private void change(java.util.function.Consumer<Configuration> edit){UiSettings.change(Runtime::config,Runtime::saveConfig,edit);refreshLabels();}
 private void refreshLabels(){var c=Runtime.config().publicView();mode.setMessage(Component.literal("Mode: "+c.get("mode").getAsString()));transport.setMessage(Component.literal("Transport: "+c.get("transport").getAsString()));debug.setMessage(Component.literal("DEBUG: "+c.get("debug").getAsBoolean()));}
 public void render(GuiGraphics g,int mx,int my,float partial){refreshLabels();renderBackground(g);g.drawCenteredString(font,title,width/2,height/2-95,0xffffff);super.render(g,mx,my,partial);}
 public void onClose(){minecraft.setScreen(parent);}
}
