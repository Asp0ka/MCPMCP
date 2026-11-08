package dev.mcpmcp.forge;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Local-only controls: saved startup preferences and transient server state. */
public final class ServerSettingsScreen extends Screen implements McpmcpSettingsScreen {
 private final Screen parent;
 private EditBox first,last;
 private Button autoStart,session;
 private boolean auto;
 private Component feedback=Component.empty();
 private int left,top,wide;
 public ServerSettingsScreen(Screen parent){super(Component.translatable("mcpmcp.server.title"));this.parent=parent;}
 @Override protected void init(){
  wide=Math.min(300,width-32);left=(width-wide)/2;top=Math.max(24,(height-230)/2);
  Configuration c=Runtime.config();
  String draftFirst=first==null?Integer.toString(c.port):first.getValue();
  String draftLast=last==null?Integer.toString(c.portEnd):last.getValue();
  if(first==null)auto=c.serverEnabled;
  first=new EditBox(font,left,top+26,(wide-12)/2,20,Component.translatable("mcpmcp.server.first"));
  last=new EditBox(font,left+(wide+12)/2,top+26,(wide-12)/2,20,Component.translatable("mcpmcp.server.last"));
  first.setMaxLength(5);last.setMaxLength(5);
  first.setValue(draftFirst);last.setValue(draftLast);
  addRenderableWidget(first);addRenderableWidget(last);
  autoStart=addRenderableWidget(Button.builder(Component.empty(),b->{auto=!auto;labels();}).bounds(left,top+52,wide,20).build());
  addRenderableWidget(Button.builder(Component.translatable("mcpmcp.server.save"),b->save()).bounds(left,top+78,wide,20).build());
  session=addRenderableWidget(Button.builder(Component.empty(),b->toggleSession()).bounds(left,top+128,wide,20).build());
  addRenderableWidget(Button.builder(Component.translatable("gui.done"),b->onClose()).bounds(left,top+196,wide,20).build());
  labels();
 }
 private void labels(){
  autoStart.setMessage(Component.translatable("mcpmcp.server.auto",Component.translatable(auto?"options.on":"options.off")));
  boolean running=Runtime.serverStatus().get("running").getAsBoolean();
  session.setMessage(Component.translatable(running?"mcpmcp.server.stop":"mcpmcp.server.start"));
 }
 private void save(){
  try{
   int[] ports=ServerSettingsInput.ports(first.getValue(),last.getValue());
   JsonObject result=Runtime.updateServerSettingsLocal(ports[0],ports[1],auto);
   feedback=result.has("error")?Component.translatable("mcpmcp.server.save_failed"):Component.translatable("mcpmcp.server.saved");
  }catch(IllegalArgumentException ex){feedback=Component.translatable("mcpmcp.server.invalid_ports");}
  labels();
 }
 private void toggleSession(){
  JsonObject result=Runtime.serverStatus().get("running").getAsBoolean()?Runtime.stopServerLocal():Runtime.startServerLocal();
  feedback=result.has("error")?Component.translatable("mcpmcp.server.session_failed"):Component.translatable("mcpmcp.server.session_changed");
  labels();
 }
 @Override public void render(GuiGraphics g,int mx,int my,float partial){
  renderBackground(g);labels();
  g.drawCenteredString(font,title,width/2,top-15,0xffffff);
  g.drawString(font,Component.translatable("mcpmcp.server.first"),left,top+13,0xffffff);
  g.drawString(font,Component.translatable("mcpmcp.server.last"),left+(wide+12)/2,top+13,0xffffff);
  JsonObject state=Runtime.serverStatus();
  boolean running=state.get("running").getAsBoolean();
  Component status=running?Component.translatable("mcpmcp.server.running",state.get("actual_port").getAsInt()):Component.translatable("mcpmcp.server.stopped");
  g.drawCenteredString(font,status,width/2,top+108,0xffffff);
  g.drawCenteredString(font,Component.translatable("mcpmcp.server.session_note"),width/2,top+154,0xffffff);
  g.drawCenteredString(font,feedback,width/2,top+168,0xffffff);
  String error=state.get("error").getAsString();
  if(!error.isEmpty())g.drawCenteredString(font,Component.translatable("mcpmcp.server.failure",error),width/2,top+182,0xff7777);
  super.render(g,mx,my,partial);
 }
 @Override public void onClose(){minecraft.setScreen(parent);}
}
