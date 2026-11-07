package dev.mcpmcp.magic;
import com.google.gson.*;
import dev.mcpmcp.api.*;
import dev.mcpmcp.core.Schema;
import java.util.*;

/** A namespace-scoped provider. Never replaces core recipes or sends mod packets. */
public class MagicProvider implements McpmcpPlugin {
 public interface Backend {JsonObject read(String kind,JsonObject args)throws Exception; default JsonObject interact(JsonObject args,boolean packet)throws Exception {throw new UnsupportedOperationException();}}
 private final String mod; private final Backend backend;
 protected MagicProvider(String mod){this(mod,new LiveBackend(mod));}
 public MagicProvider(String mod,Backend backend){if(!Set.of("botania","avaritia").contains(mod))throw new IllegalArgumentException("mod");this.mod=mod;this.backend=backend;}
 public String id(){return "mcpmcp."+mod;}
 public void register(PluginContext ctx){
  for(boolean packet:List.of(true,false)){String action=mod+(packet?".interact.packet":".interact.client");ctx.action(action,schema(".interact"),packet?"packet":"client","normal",a->{Schema.validate(a,schema(".interact"));return backend.interact(a,packet);});}
  for(String suffix:List.of("",".recipes",".blocks",".menu")){String kind=mod+suffix;ctx.observer(kind,100,a->{try{Schema.validate(a,schema(suffix));return suffix.isEmpty()?contract():backend.read(suffix,a);}catch(Exception|LinkageError ex){return J.fail(ex.getClass().getSimpleName()+": "+Objects.toString(ex.getMessage(),""));}});}
 }
 public JsonObject contract(){
  JsonObject schemas=new JsonObject();for(String s:List.of("",".recipes",".blocks",".menu"))schemas.add(mod+s,schema(s));
  JsonObject acts=new JsonObject();for(String t:List.of("packet","client"))acts.add(mod+".interact."+t,J.obj("input_schema",schema(".interact"),"transport",t,"permission","normal","effect","one native core right click; submission only"));
  return J.obj("ok",true,"provider",id(),"actions",acts,"schemas",schemas,"recipe_source","client RecipeManager, target recipe ID namespace, serializer namespace or audited implementation class","dynamic_rules","Only records present in client RecipeManager; consult optional jei observer for dynamic/viewer-only recipes. No recipe writes.","block_scope","Loaded client chunks only; radius at most 8, scan_budget at most 4096. Budget counts all positions, including air and nonmatches. Continue next_cursor with unchanged arguments.","unknown","Missing server payloads and unverified fields use status=unknown, never default zero. Cached server snapshots can lag; observed_at_ms is receipt time, not server tick.","safe_operations",List.of("block.interact after reading core describe_action, target block and held item; may open a menu intentionally","container.move or container.sequence on an already-open observed menu, empty cursor; core owns identity, transport, recovery, permission and initial menu preservation"),"machine_automation","not implemented; no claim of auto-fill, mana production, runic completion, item throwing, multiblock validation or 9x9 recipe-book placement","example",J.obj("kind",mod+".blocks","arguments",J.obj("x",0,"y",64,"z",0,"radius",1,"limit",8,"scan_budget",64)));
 }
 public static JsonObject schema(String s){
  JsonObject p=new JsonObject();JsonArray req=new JsonArray();
  if(s.equals(".interact")){for(String k:List.of("x","y","z")){integer(p,k,-30000000,30000000,"Target coordinate; core validates loaded chunk, build height, border and reach.");req.add(k);}for(String k:List.of("expected_block","expected_item")){field(p,k,"string","Required exact registry ID checked before one native interaction.");req.add(k);}field(p,"hand","string","Default main.");p.getAsJsonObject("hand").add("enum",J.json(List.of("main","off")));field(p,"face","string","Default up; core checks native visible shape face.");p.getAsJsonObject("face").add("enum",J.json(List.of("up","down","north","south","east","west")));}
  else if(s.equals(".recipes")){field(p,"query","string","Case-insensitive recipe ID substring, default empty.");field(p,"serializer","string","Exact serializer registry ID, optional.");field(p,"recipe_id","string","Exact recipe ID, optional.");integer(p,"limit",1,64,"Maximum returned rows, default 16.");integer(p,"scan_budget",1,4096,"Raw candidates per page, default 256; empty incomplete page must continue.");field(p,"cursor","string","Opaque cursor, bound to query and world/RecipeManager snapshot.");}
  else if(s.equals(".blocks")){for(String k:List.of("x","y","z")){integer(p,k,-30000000,30000000,"Required integer center; y additionally checked against world build height.");req.add(k);}integer(p,"radius",0,8,"Cube radius, default 0; within 64 blocks of player.");integer(p,"limit",1,64,"Maximum matched blocks, default 16.");integer(p,"scan_budget",1,4096,"Positions examined, default 512.");field(p,"block_id","string","Optional exact block registry ID filter.");field(p,"cursor","string","Opaque next_cursor bound to center/radius/filter/world.");}
  else if(!s.isEmpty()&&!s.equals(".menu"))throw new IllegalArgumentException("unknown_kind");
  return J.obj("type","object","properties",p,"required",req,"additionalProperties",false);
 }
 private static void integer(JsonObject p,String k,int min,int max,String d){p.add(k,J.obj("type","integer","minimum",min,"maximum",max,"description",d));}
 private static void field(JsonObject p,String k,String type,String d){p.add(k,J.obj("type",type,"maxLength",512,"description",d));}
}
