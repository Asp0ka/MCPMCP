package dev.mcpmcp.curios;

import com.google.gson.*;
import java.util.*;
import java.util.function.LongSupplier;
import static dev.mcpmcp.curios.CuriosJson.*;

/** Synchronous handler: a mutation has exactly one native submission, never a pickup sequence. */
public final class CuriosService {
 private final CuriosAccess access;
 private final LongSupplier clock;
 private record Receipt(String payload,long time,JsonObject result){}
 private final Map<String,Receipt> receipts=new LinkedHashMap<>();
 private JsonObject pending;
 public CuriosService(CuriosAccess access){this(access,System::currentTimeMillis);}
 CuriosService(CuriosAccess access,LongSupplier clock){this.access=Objects.requireNonNull(access);this.clock=clock;}
 public synchronized JsonObject read(String name,JsonObject args){
  try{
   Contract.validate(name,args);
   if(name.equals("curios"))return Contract.discovery();
   JsonObject f=access.capture();updatePending(f);
   if(name.equals("curios.menu")){f.add("pending_operation",pending==null?JsonNull.INSTANCE:pending.deepCopy());return obj("data",f);}
   JsonArray source=name.equals("curios.slots")?access.slots():access.candidates(args);
   require(source.size()<=4096,"curios_row_budget");JsonArray matching=new JsonArray();
   for(JsonElement e:source){JsonObject row=e.getAsJsonObject();if(args.has("identifier")&&!str(row,"identifier","").equals(str(args,"identifier","")))continue;if(args.has("index")&&integer(row,"index",-1)!=integer(args,"index",-2))continue;if(args.has("cosmetic")&&bool(row,"cosmetic",false)!=bool(args,"cosmetic",false))continue;matching.add(row);}
   int offset=integer(args,"offset",0),limit=integer(args,"limit",32),end=Math.min(matching.size(),offset+limit);JsonArray page=new JsonArray();
   for(int i=offset;i<end;i++)page.add(matching.get(i));
   return obj("data",obj("source","curios","scope","client_synced_player","slot_types",access.types(),"rows",page,"offset",offset,"limit",limit,"total_rows",matching.size(),"complete",end>=matching.size(),"next_offset",end<matching.size()?end:JsonNull.INSTANCE,"session",str(f,"session",""),"observed_at_ms",clock.getAsLong()));
  }catch(IllegalArgumentException|IllegalStateException e){return obj("data",obj("source","curios","available",false,"error",e.getMessage()));}
 }
 public synchronized JsonObject act(String name,JsonObject args){
  Contract.validate(name,args);long now=clock.getAsLong();
  receipts.entrySet().removeIf(e->now-e.getValue().time()>120000);
  String id=str(args,"request_id","");String payload=name+args.toString();
  Receipt prior=receipts.get(id);
  // Structural JSON equality tolerates property order on retries.
  if(prior!=null){require(JsonParser.parseString(prior.payload().substring(prior.payload().indexOf('{'))).equals(args)&&prior.payload().startsWith(name+"{"),"curios_request_id_reused");return prior.result().deepCopy();}
  JsonObject frame=access.capture();updatePending(frame);validateFrame(name,args,frame);
  require(pending==null||name.equals("curios.close"),"curios_operation_pending_read_or_close");
  boolean noop=noop(name,args,frame);
  if(!noop){access.preflight(name,args);JsonObject after=access.capture();validateFrame(name,args,after);}
  boolean dry=bool(args,"dry_run",true);
  JsonObject result=obj("data",obj("status",noop?"unchanged":dry?"dry_run":"submitted","action",name,"request_id",id,"server_confirmed",false,"packet_count_max",noop||dry?0:1,"observed_at_ms",now));
  if(dry||noop)return result;
  require(receipts.size()<512,"curios_receipt_budget");
  // Retain before sending; ambiguous exceptions cannot cause a second send on the same UUID.
  receipts.put(id,new Receipt(payload,now,result.deepCopy()));
  pending=obj("action",name,"request_id",id,"menu_instance",str(frame,"menu_instance",""),"arguments",args.deepCopy(),"submitted_at_ms",now,"server_confirmed",false);
  try{access.submit(name,args);}catch(RuntimeException e){result.getAsJsonObject("data").addProperty("status","delivery_unknown");result.getAsJsonObject("data").addProperty("error",e.getMessage());receipts.put(id,new Receipt(payload,now,result.deepCopy()));}
  return result;
 }
 private void updatePending(JsonObject frame){
  if(pending==null)return;
  if(!str(pending,"menu_instance","").equals(str(frame,"menu_instance",""))){pending=null;return;}
  JsonObject a=pending.getAsJsonObject("arguments");String action=str(pending,"action","");boolean changed=false;
  switch(action){
   case "curios.swap" -> {JsonObject slot=find(frame,"slots","menu_slot",integer(a,"menu_slot",-1)),hotbar=find(frame,"inventory","inventory_slot",integer(a,"hotbar_slot",-1));changed=slot!=null&&hotbar!=null&&str(slot.getAsJsonObject("item"),"stack_hash","").equals(str(a,"expected_hotbar_stack_hash",""))&&str(hotbar.getAsJsonObject("item"),"stack_hash","").equals(str(a,"expected_curio_stack_hash",""));}
   case "curios.page" -> changed=integer(frame,"current_page",-1)==integer(a,"page",-2);
   case "curios.cosmetics.set" -> changed=bool(frame,"viewing_cosmetics",false)==bool(a,"value",false)&&!bool(frame,"view_sync_pending",false);
   case "curios.render.set" -> {JsonObject slot=find(frame,"slots","menu_slot",integer(a,"menu_slot",-1));changed=slot!=null&&bool(slot,"render",false)==bool(a,"value",false);}
   default -> {}
  }
  if(changed)pending=null;
 }
 static void validateFrame(String action,JsonObject a,JsonObject f){
  require(str(a,"session","").equals(str(f,"session","!")),"curios_stale_session");
  require(integer(a,"menu_id",-1)==integer(f,"menu_id",-2),"curios_menu_changed");
  require(integer(a,"state_id",-1)==integer(f,"state_id",-2),"curios_state_changed");
  require(bool(f,"cursor_empty",false),"curios_cursor_not_empty");
  if(action.equals("curios.open")){require(str(f,"kind","").equals("player_closed"),"curios_close_existing_screen_first");return;}
  require(str(f,"kind","").equals("curios_v2"),"curios_v2_menu_required");
  require(action.equals("curios.close")||!bool(f,"view_sync_pending",false),"curios_cosmetic_view_sync_pending");
  switch(action){
   case "curios.swap" -> {
    JsonObject row=target(a,f);require(row.has("cosmetic")&&bool(row,"cosmetic",false)==bool(a,"cosmetic",false),"curios_slot_namespace_changed");
    require(bool(row,"audited",false)&&!bool(row,"locked",true)&&bool(row,"active",false),"curios_slot_read_only");
    JsonObject hotbar=find(f,"inventory","inventory_slot",integer(a,"hotbar_slot",-1));require(hotbar!=null,"curios_hotbar_missing");
    JsonObject ci=row.getAsJsonObject("item"),hi=hotbar.getAsJsonObject("item");identity(a,"curio",ci);identity(a,"hotbar",hi);
    boolean c=integer(ci,"count",0)>0,h=integer(hi,"count",0)>0;String intent=str(a,"intent","");
    require(switch(intent){case "equip"->!c&&h;case "unequip"->c&&!h;case "exchange"->c&&h;default->false;},"curios_intent_stack_mismatch");
   }
   case "curios.page" -> {int current=integer(f,"current_page",-1),page=integer(a,"page",-2);require(integer(a,"expected_page",-2)==current,"curios_page_changed");require(page>=0&&page<integer(f,"total_pages",0)&&Math.abs(page-current)<=1,"curios_adjacent_page_required");}
   case "curios.cosmetics.set" -> {require(bool(f,"has_cosmetics",false),"curios_cosmetics_unavailable");expectedBoolean(a,bool(f,"viewing_cosmetics",false));}
   case "curios.render.set" -> {JsonObject row=target(a,f);require(bool(row,"audited",false)&&!bool(row,"locked",true)&&bool(row,"active",false)&&!bool(row,"cosmetic",true)&&bool(row,"can_toggle_render",false),"curios_render_read_only");require(str(a,"expected_stack_hash","").equals(str(row.getAsJsonObject("item"),"stack_hash","!")),"curios_stack_changed");expectedBoolean(a,bool(row,"render",false));}
   case "curios.close" -> {}
   default -> throw new IllegalArgumentException("curios_unknown_action");
  }
 }
 private static boolean noop(String action,JsonObject a,JsonObject f){return switch(action){case "curios.page"->integer(a,"page",-1)==integer(f,"current_page",-2);case "curios.cosmetics.set"->bool(a,"value",false)==bool(f,"viewing_cosmetics",false);case "curios.render.set"->bool(a,"value",false)==bool(target(a,f),"render",false);default->false;};}
 private static void expectedBoolean(JsonObject a,boolean value){require(value==bool(a,"value",false)||value==bool(a,"expected_boolean",false),"curios_boolean_changed");}
 private static void identity(JsonObject a,String prefix,JsonObject item){require(item!=null&&str(a,"expected_"+prefix+"_item","").equals(str(item,"item_id","!"))&&integer(a,"expected_"+prefix+"_count",-1)==integer(item,"count",-2),"curios_item_or_count_changed");require(str(a,"expected_"+prefix+"_stack_hash","").equals(str(item,"stack_hash","!")),"curios_stack_changed");}
 private static JsonObject target(JsonObject a,JsonObject f){JsonObject row=find(f,"slots","menu_slot",integer(a,"menu_slot",-1));require(row!=null&&str(row,"role","").equals("curio"),"curios_real_curio_slot_required");require(str(row,"identifier","").equals(str(a,"identifier","!"))&&integer(row,"index",-1)==integer(a,"index",-2),"curios_slot_identity_changed");return row;}
 private static JsonObject find(JsonObject f,String array,String key,int value){JsonArray rows=f.getAsJsonArray(array);if(rows!=null)for(JsonElement e:rows){JsonObject row=e.getAsJsonObject();if(integer(row,key,-3)==value)return row;}return null;}
 static void require(boolean ok,String error){if(!ok)throw new IllegalArgumentException(error);}
}

