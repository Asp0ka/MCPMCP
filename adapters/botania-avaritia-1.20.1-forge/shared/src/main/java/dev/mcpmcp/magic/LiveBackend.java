package dev.mcpmcp.magic;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import java.util.*;

final class LiveBackend implements MagicProvider.Backend {
 private final String mod;private final Cursors cursors=new Cursors();private final SyncEvidence evidence;
 private List<Recipe<?>> recipes=List.of();private Object manager;private Object world;
 LiveBackend(String mod){this.mod=mod;this.evidence=new SyncEvidence(mod);MinecraftForge.EVENT_BUS.addListener(this::tick);}
 private void tick(TickEvent.ClientTickEvent e){if(e.phase==TickEvent.Phase.END)evidence.attach();}
 public JsonObject read(String kind,JsonObject a)throws Exception{
  Minecraft mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return J.fail("not_in_world");
  evidence.attach();return switch(kind){case ".recipes"->recipes(a);case ".blocks"->blocks(a);case ".menu"->Resources.menu(mod,evidence);default->J.fail("unknown_kind");};
 }
 public JsonObject interact(JsonObject a,boolean packet)throws Exception{return SafeInteraction.run(mod,a,packet);}
 private JsonObject recipes(JsonObject a){
  Minecraft mc=Minecraft.getInstance();Object rm=mc.level.getRecipeManager();
  List<Recipe<?>> now=mc.level.getRecipeManager().getRecipes().stream().sorted(Comparator.comparing(r->r.getId().toString())).toList();
  if(world!=mc.level||manager!=rm||!now.equals(recipes)){recipes=now;manager=rm;world=mc.level;}
  String sig=Cursors.signature(mod+".recipes",a);int at=cursors.offset(J.str(a,"cursor",""),mc.level,recipes,sig),scanned=0;JsonArray rows=new JsonArray();
  String q=J.str(a,"query","").toLowerCase(Locale.ROOT),exact=J.str(a,"recipe_id",""),serializer=J.str(a,"serializer","");
  while(at<recipes.size()&&scanned<J.num(a,"scan_budget",256)&&rows.size()<J.num(a,"limit",16)){
   Recipe<?> r=recipes.get(at++);scanned++;ResourceLocation sid=BuiltInRegistries.RECIPE_SERIALIZER.getKey(r.getSerializer());
   boolean owned=r.getId().getNamespace().equals(mod)||sid!=null&&sid.getNamespace().equals(mod)||r.getClass().getName().startsWith(mod.equals("botania")?"vazkii.botania.":"committee.nova.mods.avaritia.");
   if(!owned||!r.getId().toString().toLowerCase(Locale.ROOT).contains(q)||!exact.isEmpty()&&!r.getId().toString().equals(exact)||!serializer.isEmpty()&&!serializer.equals(Objects.toString(sid)))continue;
   try{rows.add(RecipeView.read(r,mod));}catch(Exception|LinkageError ex){rows.add(J.obj("recipe_id",r.getId().toString(),"serializer",sid,"details",J.unknown(ex.getClass().getSimpleName())));}
  }
  return page(rows,at,recipes.size(),scanned,mc.level,recipes,sig,"client RecipeManager; presence does not mean executable by core container.craft");
 }
 private JsonObject blocks(JsonObject a)throws Exception{
  Minecraft mc=Minecraft.getInstance();int x=J.num(a,"x",0),y=J.num(a,"y",0),z=J.num(a,"z",0),r=J.num(a,"radius",0);
  BlockPos center=new BlockPos(x,y,z);if(mc.level.isOutsideBuildHeight(center)||mc.player.position().distanceToSqr(x+0.5,y+0.5,z+0.5)>4096)return J.fail("center_outside_build_height_or_64_block_range");
  int side=2*r+1,total=side*side*side;String sig=Cursors.signature(mod+".blocks",a);Object level=mc.level;
  int at=cursors.offset(J.str(a,"cursor",""),level,level,sig),scanned=0,unloaded=0;JsonArray rows=new JsonArray();
  while(at<total&&scanned<J.num(a,"scan_budget",512)&&rows.size()<J.num(a,"limit",16)){
   int i=at++;scanned++;BlockPos p=center.offset(i%side-r,(i/side)%side-r,i/(side*side)-r);
   if(mc.level.isOutsideBuildHeight(p)||!mc.level.hasChunkAt(p)){unloaded++;continue;}
   var state=mc.level.getBlockState(p);ResourceLocation id=BuiltInRegistries.BLOCK.getKey(state.getBlock());
   if(!id.getNamespace().equals(mod)||a.has("block_id")&&!id.toString().equals(J.str(a,"block_id","")))continue;
   BlockEntity be=mc.level.getBlockEntity(p);JsonObject row=J.obj("x",p.getX(),"y",p.getY(),"z",p.getZ(),"block_id",id.toString(),"state",state.toString(),"loaded",true);
   if(be!=null)row.add("resources",Resources.block(mod,be,evidence));else row.add("resources",J.unknown("no_block_entity"));rows.add(row);
  }
  JsonObject out=page(rows,at,total,scanned,level,level,sig,"loaded client blocks; live pagination, not an atomic area snapshot");out.addProperty("unloaded_or_outside_positions",unloaded);out.addProperty("force_loaded",false);out.addProperty("all_positions_loaded",unloaded==0);return out;
 }
 private JsonObject page(JsonArray rows,int at,int total,int scanned,Object w,Object source,String sig,String note){return J.obj("ok",true,"entries",rows,"scanned",scanned,"complete",at>=total,"scope",note,"next_cursor",at<total?cursors.next(w,source,sig,at):null);}
}
