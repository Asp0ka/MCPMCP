package dev.mcpmcp.forge;
import net.minecraft.client.gui.screens.Screen;import net.minecraft.client.gui.components.Button;import net.minecraft.client.gui.GuiGraphics;import net.minecraft.network.chat.Component;
public final class SettingsScreen extends Screen {
 private final Screen parent;public SettingsScreen(Screen p){super(Component.literal("MCP Minecraft Plus"));parent=p;}
 protected void init(){Configuration c=Runtime.config();String[] modes={"NORMAL","HACK","CHEAT","HACK_CHEAT"},transports={"MIXED","PACKET","SIMULATE"};int x=width/2-130,y=height/2-65;
  addRenderableWidget(Button.builder(Component.literal("Mode: "+c.mode),b->{c.mode=modes[(java.util.Arrays.asList(modes).indexOf(c.mode)+1)%modes.length];b.setMessage(Component.literal("Mode: "+c.mode));Runtime.saveConfig();}).bounds(x,y,260,20).build());
  addRenderableWidget(Button.builder(Component.literal("Transport: "+c.transport),b->{c.transport=transports[(java.util.Arrays.asList(transports).indexOf(c.transport)+1)%transports.length];b.setMessage(Component.literal("Transport: "+c.transport));Runtime.saveConfig();}).bounds(x,y+24,260,20).build());
  addRenderableWidget(Button.builder(Component.literal("DEBUG: "+c.debug),b->{c.debug=!c.debug;b.setMessage(Component.literal("DEBUG: "+c.debug));Runtime.saveConfig();}).bounds(x,y+48,260,20).build());
  addRenderableWidget(Button.builder(Component.literal("Done"),b->onClose()).bounds(x,y+86,260,20).build());}
 public void render(GuiGraphics g,int mx,int my,float partial){renderBackground(g);g.drawCenteredString(font,title,width/2,height/2-95,0xffffff);super.render(g,mx,my,partial);}
 public void onClose(){minecraft.setScreen(parent);}
}
