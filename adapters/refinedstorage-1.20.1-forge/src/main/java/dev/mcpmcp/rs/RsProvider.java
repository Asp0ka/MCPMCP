package dev.mcpmcp.rs;
import com.google.gson.*;
import dev.mcpmcp.api.*;
import dev.mcpmcp.core.Schema;
import java.util.*;
import static dev.mcpmcp.core.Jsons.*;
/** Closed discoverable contracts. Transport describes the actual native operation. */
public final class RsProvider implements McpmcpPlugin {
 public static final List<String> READS=List.of("rs","rs.grid","rs.storage","rs.operation");
 public static final List<String> ACTIONS=List.of("rs.extract","rs.insert_held","rs.craft.start","rs.crafting.take","rs.crafting.clear");
 private final RsService service;
 public RsProvider(){this(new RsService(new LiveGridAccess()));}
 public RsProvider(RsService service){this.service=Objects.requireNonNull(service);}
 public String id(){return "mcpmcp.refinedstorage";}
 public void register(PluginContext ctx){
  for(String k:READS)ctx.observer(k,100,a->service.read(k,a));
  for(String k:ACTIONS)ctx.action(k,schema(k),transport(k),"normal",a->{Schema.validate(a,schema(k));return service.act(k,a);});
 }
 public static String transport(String action){return action.equals("rs.crafting.take")?"input":"packet";}
 public static JsonObject schema(String kind){
  JsonObject s=obj("type","object","properties",new JsonObject(),"additionalProperties",false,"required",new JsonArray());
  JsonObject p=s.getAsJsonObject("properties");JsonArray r=s.getAsJsonArray("required");
  switch(kind){
   case "rs" -> s.addProperty("description","Pinned RS capabilities, schemas and limitations. Never opens or replaces a screen.");
   case "rs.grid" -> s.addProperty("description","Open active item GridScreen context, cursor, player inventory and crafting slots.");
   case "rs.storage" -> {
    text(p,"cursor",0,128,"Opaque next_cursor; keep query and limit unchanged. Expires in 30 seconds or on context change.");
    text(p,"query",0,128,"Case-insensitive ID/name substring. Does not change the search box or filters.");
    number(p,"limit",1,64,"Page size, default 32; snapshot bounded to 16384 raw rows / 8 MiB.");
   }
   case "rs.operation" -> {required(r,"operation_token");text(p,"operation_token",1,64,"Receipt from submission. Read after server sync; not a retry token.");}
   case "rs.extract","rs.insert_held","rs.craft.start","rs.crafting.take","rs.crafting.clear" -> {
    required(r,"session","menu_id","request_id");
    text(p,"session",1,64,"Exact context token from rs.grid/rs.storage; binds world, player, screen, menu and view.");
    number(p,"menu_id",0,Integer.MAX_VALUE,"Exact menu ID, never a virtual row index.");
    uuid(p,"request_id","Client UUID; identical retries return original receipt. Retained for session, max 1024 attempts.");
    field(p,"dry_run","boolean","Default true. false explicitly submits once after the same preflight.");
    if(!kind.equals("rs.crafting.clear")){
     required(r,"expected_item","expected_nbt_sha256");
     text(p,"expected_item",3,256,"Exact current item registry ID.").addProperty("pattern","[a-z0-9_.-]+:[a-z0-9_./-]+");
     digest(p,"expected_nbt_sha256","Exact canonical tag digest; truncated preview is not identity.");
    }
    switch(kind){
     case "rs.extract" -> {required(r,"stack_id","expected_quantity");uuid(p,"stack_id","Fresh stored-row UUID, not craftable or menu slot.");number(p,"expected_quantity",1,Integer.MAX_VALUE,"Exact snapshot network quantity. Changes require refresh.");field(p,"mode","string","Default stack. single/half/stack to empty cursor; shift to player inventory.").add("enum",GSON.toJsonTree(List.of("single","half","stack","shift")));}
     case "rs.insert_held" -> {required(r,"expected_count");number(p,"expected_count",1,Integer.MAX_VALUE,"Exact nonempty cursor count.");field(p,"single","boolean","Default false, try whole cursor; true inserts one.");}
     case "rs.craft.start" -> {required(r,"stack_id","quantity");uuid(p,"stack_id","Exact craftable-row UUID, not stored counterpart.");number(p,"quantity",1,4096,"Requested output item quantity. One request; no completion acknowledgement.");}
     case "rs.crafting.take" -> {required(r,"result_slot","expected_count","expected_matrix_sha256");number(p,"result_slot",0,511,"Actual result menu slot from rs.grid.");number(p,"expected_count",1,64,"Exact result count.");digest(p,"expected_matrix_sha256","Fresh inputs/output digest. Cursor must be empty.");}
     case "rs.crafting.clear" -> {required(r,"expected_matrix_sha256");digest(p,"expected_matrix_sha256","Exact inputs/output digest. Native RS clear; empty cursor required.");}
     default -> {}
    }
   }
   default -> throw new IllegalArgumentException("unknown_rs_contract");
  }
  return s;
 }
 public static JsonObject discovery(){
  JsonObject contracts=new JsonObject(),policies=new JsonObject();
  for(String k:READS)contracts.add(k,schema(k));
  for(String k:ACTIONS){contracts.add(k,schema(k));policies.add(k,obj("transport",transport(k),"permission","normal"));}
  return obj("data",obj("source","refinedstorage","adapter_version","0.1.0","target_version","1.12.4",
   "target_sha256","d8ae3926c2121336efcd6956c43d91f21ce405b77ac7c7201c559d4c19f380d2",
   "contracts",contracts,"observers",READS,"actions",policies,"dry_run_default",true,
   "scope","currently open active GridScreen; client-synced items only",
   "limits",obj("page_size_max",64,"raw_entries_max",16384,"snapshot_bytes_max",8388608,"snapshot_ttl_ms",30000,"operation_ttl_ms",120000,"attempts_per_session_max",1024),
   "limitations",List.of("No fluids, pattern writing, server scan, recipe filling, auto-open, task monitor/cancel or completion proof.",
    "Craft preview responses replace the screen; deliberately unsupported to preserve the current screen.",
    "Native packets contain no expected menu/item/NBT fields. Client preflight cannot remove server/network races.",
    "Operation readbacks are client observations; other actors may cause changes. Never automatically retry submitted requests."),
   "examples",List.of("observe rs {}","observe rs.grid {}","observe rs.storage {query:minecraft:stone,limit:32}",
    "execute rs.extract {session:<session>,menu_id:<id>,request_id:<uuid>,stack_id:<stored UUID>,expected_item:<id>,expected_nbt_sha256:<digest>,expected_quantity:<quantity>,mode:single,dry_run:true}",
    "execute rs.craft.start {session:<session>,menu_id:<id>,request_id:<uuid>,stack_id:<craftable UUID>,expected_item:<id>,expected_nbt_sha256:<digest>,quantity:1,dry_run:false}",
    "observe rs.operation {operation_token:<receipt>}")));
 }
 private static void required(JsonArray a,String...ks){for(String k:ks)a.add(k);}
 private static JsonObject field(JsonObject p,String k,String t,String d){JsonObject f=obj("type",t,"description",d);p.add(k,f);return f;}
 private static JsonObject text(JsonObject p,String k,int min,int max,String d){JsonObject f=field(p,k,"string",d);f.addProperty("minLength",min);f.addProperty("maxLength",max);return f;}
 private static void number(JsonObject p,String k,int min,int max,String d){JsonObject f=field(p,k,"integer",d);f.addProperty("minimum",min);f.addProperty("maximum",max);}
 private static void digest(JsonObject p,String k,String d){text(p,k,64,64,d).addProperty("pattern","[a-f0-9]{64}");}
 private static void uuid(JsonObject p,String k,String d){text(p,k,36,36,d).addProperty("pattern","[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}");}
}
