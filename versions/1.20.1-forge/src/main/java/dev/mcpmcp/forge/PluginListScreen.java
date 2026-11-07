package dev.mcpmcp.forge;

import com.google.gson.*;
import dev.mcpmcp.core.Jsons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;

/** Native scroll/select list. Compatibility overrides are local UI decisions only. */
public final class PluginListScreen extends Screen implements McpmcpSettingsScreen {
 private final Screen parent;
 private PluginList list;
 private Button force;
 private Component feedback=Component.empty();private String selectedId="";private double savedScroll;
 public PluginListScreen(Screen parent){super(Component.translatable("mcpmcp.plugins.title"));this.parent=parent;}
 protected void init(){
  list=addRenderableWidget(new PluginList(minecraft,width,height,38,height-92,72));
  int total=Math.min(420,width-24),gap=6,w=(total-gap*2)/3,x=(width-total)/2;
  addRenderableWidget(Button.builder(Component.translatable("mcpmcp.plugins.refresh"),b->{JsonObject r=Runtime.refreshPluginsLocal();feedback=Component.translatable(r.has("error")?"mcpmcp.plugins.reload_failed":"mcpmcp.plugins.reloaded");reloadRows();}).bounds(x,height-28,w,20).build());
  force=addRenderableWidget(Button.builder(Component.empty(),b->toggle()).bounds(x+w+gap,height-28,w,20).build());
  addRenderableWidget(Button.builder(Component.translatable("gui.done"),b->onClose()).bounds(x+(w+gap)*2,height-28,w,20).build());
  reloadRows();
 }
 private void reloadRows(){
  String selected=list.getSelected()==null?selectedId:Jsons.str(list.getSelected().data,"id","");double scroll=list.getSelected()==null?savedScroll:list.getScrollAmount();
  list.clear();for(JsonElement e:Runtime.plugins().status().getAsJsonArray("plugins")){Row row=new Row(e.getAsJsonObject());list.add(row);if(Jsons.str(row.data,"id","").equals(selected))list.setSelected(row);}
  list.setScrollAmount(scroll);updateButton();
 }
 private void updateButton(){if(force==null)return;Row row=list.getSelected();boolean forced=row!=null&&Jsons.bool(row.data,"forced",false);force.setMessage(Component.translatable(forced?"mcpmcp.plugins.revoke":"mcpmcp.plugins.force"));force.active=row!=null&&(forced||Jsons.bool(row.data,"can_force",false));}
 private void toggle(){
  Row row=list.getSelected();if(row==null)return;selectedId=Jsons.str(row.data,"id","");savedScroll=list.getScrollAmount();JsonObject data=row.data;String id=Jsons.str(data,"id",""),binding=Jsons.str(data,"override_binding","");
  if(Jsons.bool(data,"forced",false)){apply(id,binding,false);return;}
  minecraft.setScreen(new PluginForceScreen(yes->{minecraft.setScreen(this);if(yes)apply(id,binding,true);},id));
 }
 private void apply(String id,String binding,boolean enabled){JsonObject r=Runtime.setPluginOverride(id,binding,enabled);feedback=Component.translatable(r.has("error")?"mcpmcp.plugins.override_failed":"mcpmcp.plugins.override_saved");reloadRows();}
 private Component value(String s){return s.isEmpty()?Component.translatable("mcpmcp.plugins.unspecified"):Component.literal(s);}
 private Component version(JsonObject p){return Component.translatable("mcpmcp.plugins.versions",value(Jsons.str(p,"expected_version","")),value(Jsons.str(p,"actual_version","")));}
 private Component state(JsonObject p){return Component.translatable("mcpmcp.plugins.status."+Jsons.str(p,"status","unknown"));}
 public void render(GuiGraphics g,int mx,int my,float partial){
  renderBackground(g);super.render(g,mx,my,partial);g.drawCenteredString(font,title,width/2,12,0xffffff);
  if(list.children().isEmpty())g.drawCenteredString(font,Component.translatable("mcpmcp.plugins.empty"),width/2,52,0xdddddd);
  Row row=list.getSelected();int y=height-82;
  Component hint=row==null?Component.translatable("mcpmcp.plugins.select"):Jsons.bool(row.data,"java_restart_required",false)||Jsons.bool(row.data,"java_present",false)||Jsons.bool(row.data,"java_loaded",false)?Component.translatable("mcpmcp.plugins.java_restart"):Component.translatable("mcpmcp.plugins.force_hint");
  for(var line:font.split(hint,width-32)){if(y>height-50)break;g.drawCenteredString(font,line,width/2,y,0xdddddd);y+=10;}
  if(!feedback.getString().isEmpty())g.drawCenteredString(font,font.plainSubstrByWidth(feedback.getString(),width-24),width/2,height-43,0xffffaa);
  updateButton();
 }
 public void onClose(){minecraft.setScreen(parent);}
 private final class PluginList extends ObjectSelectionList<Row> {
  PluginList(Minecraft mc,int width,int height,int top,int bottom,int rowHeight){super(mc,width,height,top,bottom,rowHeight);}
  public int getRowWidth(){return Math.min(560,PluginListScreen.this.width-40);}
  protected int getScrollbarPosition(){return (PluginListScreen.this.width+getRowWidth())/2+4;}
  void clear(){clearEntries();}
  void add(Row row){addEntry(row);}
 }
 private final class Row extends ObjectSelectionList.Entry<Row> {
  final JsonObject data;
  Row(JsonObject data){this.data=data.deepCopy();}
  public Component getNarration(){return Component.literal(Jsons.str(data,"id","")).append(". ").append(version(data)).append(". ").append(state(data));}
  public boolean mouseClicked(double x,double y,int button){if(button!=0)return false;list.setSelected(this);selectedId=Jsons.str(data,"id","");savedScroll=list.getScrollAmount();updateButton();return true;}
  public void render(GuiGraphics g,int index,int y,int x,int rowWidth,int rowHeight,int mx,int my,boolean hover,float partial){
   int color=Jsons.bool(data,"forced",false)?0xffcc66:Jsons.str(data,"status","").equals("active")?0x99ee99:0xffaaaa;
   Component installed=Component.translatable(Jsons.bool(data,"mod_installed",false)?"mcpmcp.plugins.installed":"mcpmcp.plugins.missing");
   List<Component> lines=List.of(Component.translatable("mcpmcp.plugins.identity",Jsons.str(data,"id",""),Jsons.str(data,"mod_id","")),Component.translatable("mcpmcp.plugins.mod_state",installed),version(data),Component.translatable("mcpmcp.plugins.version_state",Component.translatable(!Jsons.bool(data,"version_declared",false)?"mcpmcp.plugins.unspecified":Jsons.bool(data,"version_match",false)?"mcpmcp.plugins.version_match":"mcpmcp.plugins.version_mismatch")),Component.translatable("mcpmcp.plugins.hash_state",Component.translatable(Jsons.str(data,"expected_sha256","").isEmpty()?"mcpmcp.plugins.unspecified":Jsons.bool(data,"hash_match",false)?"mcpmcp.plugins.match":"mcpmcp.plugins.mismatch")),Component.translatable("mcpmcp.plugins.state",state(data)));
   for(int i=0;i<lines.size();i++)g.drawString(font,font.plainSubstrByWidth(lines.get(i).getString(),rowWidth-12),x+4,y+3+i*11,i==5?color:0xffffff);
   if(hover){Component detail=Component.empty();for(Component line:lines)detail=detail.copy().append(line).append("\n");PluginListScreen.this.setTooltipForNextRenderPass(font.split(detail,Math.min(380,PluginListScreen.this.width-24)));}
  }
 }
}
