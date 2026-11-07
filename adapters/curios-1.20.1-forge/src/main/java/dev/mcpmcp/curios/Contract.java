package dev.mcpmcp.curios;

import com.google.gson.*;
import dev.mcpmcp.core.Schema;
import java.util.*;
import static dev.mcpmcp.curios.CuriosJson.*;

/** Public closed schemas; all indices name their source namespace. */
public final class Contract {
 public static final String VERSION="5.14.1+1.20.1";
 public static final String SHA256="1e817919a35b37cf30524aaec73f0ca5130452f23f168f844854df282eb8e51f";
 public static final List<String> READS=List.of("curios","curios.slots","curios.menu","curios.candidates");
 public static final List<String> ACTIONS=List.of("curios.open","curios.close","curios.swap","curios.page","curios.cosmetics.set","curios.render.set");
 public static void validate(String name,JsonObject args){Schema.validate(args,schema(name));}
 public static JsonObject schema(String name){
  JsonObject s=obj("type","object","properties",new JsonObject(),"additionalProperties",false,"required",new JsonArray());
  JsonObject p=s.getAsJsonObject("properties");JsonArray r=s.getAsJsonArray("required");
  switch(name){
   case "curios" -> s.addProperty("description","Pinned target, full schemas and safe workflow. Does not touch the game.");
   case "curios.menu" -> s.addProperty("description","Current menu/session/state/cursor, real menu slots, main inventory and pending operation. Does not open a screen.");
   case "curios.slots","curios.candidates" -> {
    number(p,"offset",0,4096,"Default 0. Fresh live offset page; not an atomic snapshot. Keep filters unchanged.");
    number(p,"limit",1,64,"Default 32, maximum 64 rows. Total capability rows bounded to 4096.");
    text(p,"identifier",1,128,"Optional exact Curios identifier from slots, e.g. ring. Not a resource ID or menu index.");
    number(p,"index",0,4095,"Optional exact index within that identifier's stack handler.");
    field(p,"cosmetic","boolean","Optional filter for slots; candidates default false. Cosmetic handler is a separate namespace.");
    if(name.equals("curios.candidates")){required(r,"inventory_slot");number(p,"inventory_slot",0,35,"Actual player main inventory index; 0..8 are hotbar. Does not scan registry or server storage. Native predicates are client-only hints.");}
   }
   case "curios.open","curios.close","curios.swap","curios.page","curios.cosmetics.set","curios.render.set" -> {
    required(r,"session","menu_id","state_id","request_id");
    text(p,"session",36,36,"Fresh token from curios.menu; binds real world/player/connection/menu/screen/capability, page/grid and slot objects; expires after 30 seconds.").addProperty("pattern",UUID_PATTERN);
    number(p,"menu_id",0,Integer.MAX_VALUE,"Exact current menu ID, including player menu ID for open.");
    number(p,"state_id",0,32767,"Exact current menu state from same observation; not a server success signal.");
    text(p,"request_id",36,36,"UUID for one operation. Same UUID + same payload returns retained receipt for 120 seconds, max 512. Never retry submitted under a new UUID.").addProperty("pattern",UUID_PATTERN);
    field(p,"dry_run","boolean","Default true. false explicitly submits once after identical checks. No stack/UI writes or packets during dry-run.");
    switch(name){
     case "curios.open" -> s.addProperty("description","Only no screen + current player inventory menu + empty cursor. One native CPacketOpenCurios(EMPTY). Observe until Curios v2 is actually open.");
     case "curios.close" -> s.addProperty("description","Only token-bound Curios v2 screen/menu + empty cursor. Native closeContainer sends one close packet and closes local screen to gameplay.");
     case "curios.swap" -> {
      required(r,"menu_slot","identifier","index","cosmetic","hotbar_slot","intent","expected_curio_item","expected_curio_count","expected_curio_stack_hash","expected_hotbar_item","expected_hotbar_count","expected_hotbar_stack_hash");
      target(p);field(p,"cosmetic","boolean","Exact CurioSlot.isCosmetic from menu; never inferred from menu slot arithmetic.");
      number(p,"hotbar_slot",0,8,"Player inventory hotbar index, also native SWAP button; never a menu slot.");
      field(p,"intent","string","equip = empty Curio/nonempty hotbar; unequip = nonempty Curio/empty hotbar; exchange = both nonempty. Entire stacks only.").add("enum",GSON.toJsonTree(List.of("equip","unequip","exchange")));
      for(String side:List.of("curio","hotbar")){
       text(p,"expected_"+side+"_item",3,256,"Exact item_id from same menu observation; empty uses minecraft:air.").addProperty("pattern","[a-z0-9_.-]+:[a-z0-9_./-]+");
       number(p,"expected_"+side+"_count",0,Integer.MAX_VALUE,"Exact full count, including 0 for empty.");
       digest(p,"expected_"+side+"_stack_hash","Full ItemStack.save SHA-256 including ForgeCaps, ordinary NBT, type/order and full int count, including empty stacks.");
      }
      s.addProperty("description","One vanilla SWAP packet, empty carried prediction and empty changed-slots map; no local inventory prediction, pickup sequence, quick-move, partial stack or destination fallback.");
     }
     case "curios.page" -> {required(r,"expected_page","page");number(p,"expected_page",0,4095,"Exact zero-based current page from menu.");number(p,"page",0,4095,"Desired current or adjacent page only. One CPacketPage(menu_id,next); observe fresh session before next operation.");}
     case "curios.cosmetics.set" -> {required(r,"expected_boolean","value");booleans(p);s.addProperty("description","Set cosmetic VIEW, not item cosmetic status. Native client toggle + one packet, as native button. Read-back after server page response; local view alone is not confirmation.");}
     case "curios.render.set" -> {required(r,"menu_slot","identifier","index","expected_boolean","value","expected_stack_hash");target(p);booleans(p);digest(p,"expected_stack_hash","Exact functional Curio item stack hash from menu. Cosmetic slots are deliberately rejected.");}
     default -> {}
    }
   }
   default -> throw new IllegalArgumentException("curios_unknown_contract");
  }
  return s;
 }
 public static JsonObject discovery(){
  JsonObject contracts=new JsonObject(),actions=new JsonObject();
  for(String name:READS)contracts.add(name,schema(name));
  for(String name:ACTIONS){contracts.add(name,schema(name));actions.add(name,obj("permission","normal","transport","packet"));}
  return obj("data",obj("source","curios","adapter_version","0.1.0","target_version",VERSION,"target_sha256",SHA256,
   "contracts",contracts,"actions",actions,"observers",READS,"dry_run_default",true,
   "limits",obj("limit_max",64,"capability_rows_max",4096,"menu_slots_max",512,"session_ttl_ms",30000,"receipt_ttl_ms",120000,"receipts_max",512),
   "workflow",List.of("Observe curios.slots without opening a screen.","Observe curios.menu; if originally closed, explicitly open then wait/read back.","Use actual menu_slot + identifier/index/cosmetic and both stack hashes. Dry-run then submit a hotbar swap.","Observe exact two-stack result. Keep an initially open menu open. Explicit close only if caller intends to restore gameplay."),
   "limitations",List.of("Client-synced state only; unknown handlers remain read-only and unknown predicates are null.","No arbitrary stack setters, slot resizing, off-page guessed IDs, automatic open/swap/close wrapper, multi-pickup, arbitrary inventory swap or other mods' global skill keys.","Non-hotbar source/destination: caller first uses core container.move to stage it in hotbar.","Native packet has no expected item/hash fields; client checks cannot remove server/network races.","submitted is never server success. Re-read; uncertain sends are never automatically retried.","Page, cosmetic view and render use underlying toggle packets. One pending menu operation blocks additional mutations until relevant observation/context change; close remains available.")));
 }
 private static final String UUID_PATTERN="[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}";
 private static void target(JsonObject p){number(p,"menu_slot",0,511,"Actual current menu list index from curios.menu.slots; off-page proxy slots are never exposed as clickable.");text(p,"identifier",1,128,"Exact real CurioSlot.getIdentifier.");number(p,"index",0,4095,"Exact real CurioSlot.getSlotIndex within selected handler.");}
 private static void booleans(JsonObject p){field(p,"expected_boolean","boolean","Exact observed current boolean; compared before toggling. Already desired value is a no-op.");field(p,"value","boolean","Desired boolean; no-op when already equal. Never blindly toggle repeatedly.");}
 private static void required(JsonArray a,String...ks){for(String k:ks)a.add(k);}
 private static JsonObject field(JsonObject p,String k,String type,String d){JsonObject v=obj("type",type,"description",d);p.add(k,v);return v;}
 private static JsonObject text(JsonObject p,String k,int min,int max,String d){JsonObject v=field(p,k,"string",d);v.addProperty("minLength",min);v.addProperty("maxLength",max);return v;}
 private static void number(JsonObject p,String k,int min,int max,String d){JsonObject v=field(p,k,"integer",d);v.addProperty("minimum",min);v.addProperty("maximum",max);}
 private static void digest(JsonObject p,String k,String d){text(p,k,64,64,d).addProperty("pattern","[a-f0-9]{64}");}
 private Contract(){}
}
