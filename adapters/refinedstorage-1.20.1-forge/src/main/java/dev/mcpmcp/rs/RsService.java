package dev.mcpmcp.rs;

import com.google.gson.*;
import dev.mcpmcp.core.Schema;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.LongSupplier;
import static dev.mcpmcp.core.Jsons.*;
import static dev.mcpmcp.rs.GridAccess.*;

/** Policy-neutral state machine; the host enforces permission and real transport. */
public final class RsService {
 private static final long SNAPSHOT_TTL=30_000,OPERATION_TTL=120_000;
 private final GridAccess grid;
 private final LongSupplier clock;
 private final LinkedHashMap<String,Snapshot> snapshots=new LinkedHashMap<>();
 private final LinkedHashMap<String,Receipt> receipts=new LinkedHashMap<>();
 private final Map<String,Attempt> attempts=new HashMap<>();
 private String session="";
 private record Snapshot(String session,int menu,String query,int limit,long created,List<JsonObject> rows,Set<Integer> issued){}
 private record Receipt(String action,JsonObject args,Frame before,JsonObject target,long created,JsonObject response){}
 private record Attempt(String fingerprint,JsonObject response){}
 private record Prepared(Frame frame,JsonObject target,Command command){}
 public RsService(GridAccess grid){this(grid,()->System.nanoTime()/1_000_000L);}
 public RsService(GridAccess grid,LongSupplier clock){this.grid=Objects.requireNonNull(grid);this.clock=Objects.requireNonNull(clock);}

 public JsonObject read(String kind,JsonObject a){
  try{
   Schema.validate(a,RsProvider.schema(kind));
   if(kind.equals("rs"))return RsProvider.discovery();
   Frame f=current();
   return switch(kind){
    case "rs.grid" -> obj("data",frameJson(f));
    case "rs.storage" -> storage(f,a);
    case "rs.operation" -> operation(f,a);
    default -> error("unknown_rs_observer");
   };
  }catch(Exception|LinkageError e){JsonObject f=failure(e);f.addProperty("available",false);f.addProperty("source","refinedstorage");return obj("data",f);}
 }
 public JsonObject act(String action,JsonObject a){
  try{
   Schema.validate(a,RsProvider.schema(action));
   if(!RsProvider.ACTIONS.contains(action))return error("unknown_rs_action");
   Frame initial=current();checkContext(initial,a);
   String request=str(a,"request_id","").toLowerCase(Locale.ROOT);
   JsonObject payload=a.deepCopy();payload.remove("request_id");payload.remove("dry_run");
   String fingerprint=action+":"+JsonIdentity.digest(payload);
   Attempt prior=attempts.get(request);
   if(prior!=null){
    need(prior.fingerprint.equals(fingerprint),"rs_request_id_conflict");
    JsonObject replay=prior.response.deepCopy();replay.addProperty("replayed",true);return replay;
   }
   Prepared p=prepare(action,a,initial);
   if(bool(a,"dry_run",true))return obj("delivery","not_submitted","dry_run",true,"preflight_ok",true,"transport",RsProvider.transport(action),
    "before",frameJson(p.frame),"target",p.target,"verification_hint",hint(action),"server_success_confirmed",false);
   need(attempts.size()<1024,"rs_session_attempt_limit_reopen_grid");
   // Repeat every safety check immediately before one native operation.
   p=prepare(action,a,current());
   String token=UUID.randomUUID().toString();
   JsonObject result=obj("delivery","submitted","submission_state","attempting","operation_token",token,"request_id",request,"transport",RsProvider.transport(action),
    "before",frameJson(p.frame),"target",p.target,"server_success_confirmed",false,"verification_hint",hint(action));
   // Tombstones retain only a compact receipt, not 1024 full inventory snapshots.
   JsonObject marker=obj("delivery","submitted","submission_state","attempting","operation_token",token,"request_id",request,
    "transport",RsProvider.transport(action),"server_success_confirmed",false,"verification_hint",hint(action));
   attempts.put(request,new Attempt(fingerprint,marker));
   receipts.put(token,new Receipt(action,a.deepCopy(),p.frame,p.target.deepCopy(),clock.getAsLong(),marker));
   while(receipts.size()>128)receipts.remove(receipts.keySet().iterator().next());
   try{grid.submit(p.command);result.addProperty("submission_state","native_call_returned");}
   catch(Exception|LinkageError e){result.addProperty("submission_state","transport_exception_outcome_unknown");result.addProperty("warning",safeMessage(e));}
   marker.addProperty("submission_state",str(result,"submission_state","unknown"));
   if(result.has("warning"))marker.add("warning",result.get("warning"));
   // Send and read-back are separate. A read-back error must never invite a resend.
   try{Frame after=grid.capture();result.add("immediate_readback",frameJson(after));result.addProperty("context_unchanged",after.session().equals(p.frame.session()));}
   catch(Exception|LinkageError e){result.addProperty("readback_error",safeMessage(e));}
   return result.deepCopy();
  }catch(Exception|LinkageError e){return failure(e);}
 }
 private Frame current(){
  Frame f=grid.capture();
  need(f!=null,"rs_grid_unavailable");
  if(!session.equals(f.session())){snapshots.clear();receipts.clear();attempts.clear();session=f.session();}
  need(f.active(),"rs_grid_inactive");
  need(!f.gridType().equals("FLUID"),"rs_item_grid_required");
  long now=clock.getAsLong();snapshots.values().removeIf(s->now-s.created>SNAPSHOT_TTL);receipts.values().removeIf(s->now-s.created>OPERATION_TTL);
  return f;
 }
 private static void checkContext(Frame f,JsonObject a){need(f.session().equals(str(a,"session","")),"rs_context_changed_refresh_grid");need(f.menuId()==integer(a,"menu_id",-1),"rs_menu_changed");}
 private JsonObject storage(Frame f,JsonObject a){
  String query=str(a,"query","").toLowerCase(Locale.ROOT),cursor=str(a,"cursor","");int limit=integer(a,"limit",32);
  Snapshot snapshot;String id;int offset=0;
  if(cursor.isEmpty()){
   List<Entry> raw=grid.entries();need(raw.size()<=16384,"rs_snapshot_entry_limit");
   Set<String> unique=new HashSet<>();List<JsonObject> rows=new ArrayList<>();long bytes=0;
   for(Entry e:raw){
    need(unique.add(e.id()),"rs_duplicate_stack_uuid");
    JsonObject row=row(e);
    String item=str(row,"item_id",""),name=str(row,"name","");
    if(!item.toLowerCase(Locale.ROOT).contains(query)&&!name.toLowerCase(Locale.ROOT).contains(query))continue;
    bytes+=row.toString().getBytes(StandardCharsets.UTF_8).length;need(bytes<=8_388_608,"rs_snapshot_byte_limit");rows.add(row);
   }
   rows.sort(Comparator.comparing(e->str(e,"stack_id","")));
   id=UUID.randomUUID().toString();snapshot=new Snapshot(f.session(),f.menuId(),query,limit,clock.getAsLong(),List.copyOf(rows),new HashSet<>(Set.of(0)));
   snapshots.put(id,snapshot);while(snapshots.size()>4)snapshots.remove(snapshots.keySet().iterator().next());
  }else{
   int colon=cursor.lastIndexOf(':');need(colon>0,"rs_invalid_cursor");id=cursor.substring(0,colon);
   try{offset=Integer.parseInt(cursor.substring(colon+1));}catch(NumberFormatException e){throw new IllegalArgumentException("rs_invalid_cursor");}
   snapshot=snapshots.get(id);need(snapshot!=null,"rs_snapshot_expired_refresh");
   need(snapshot.session.equals(f.session())&&snapshot.menu==f.menuId(),"rs_context_changed_refresh_grid");
   need(snapshot.query.equals(query)&&snapshot.limit==limit,"rs_cursor_parameters_changed");
   need(snapshot.issued.contains(offset),"rs_invalid_cursor");
  }
  int end=Math.min(offset+limit,snapshot.rows.size());JsonArray entries=new JsonArray();
  for(int i=offset;i<end;i++)entries.add(snapshot.rows.get(i).deepCopy());
  String next="";if(end<snapshot.rows.size()){snapshot.issued.add(end);next=id+":"+end;}
  return obj("data",obj("session",f.session(),"menu_id",f.menuId(),"active",true,"scope","open_grid_client_synced_items_only",
   "snapshot_id",id,"consistency","frozen_client_snapshot; refresh before actions","age_ms",clock.getAsLong()-snapshot.created,
   "offset",offset,"entries",entries,"total_matches",snapshot.rows.size(),"returned",entries.size(),"next_cursor",next,"has_more",!next.isEmpty(),"complete",next.isEmpty()));
 }
 private Prepared prepare(String action,JsonObject a,Frame f){
  checkContext(f,a);JsonObject target=new JsonObject();String id="",mode="";int quantity=0,slot=-1;boolean single=false;
  if(!action.equals("rs.insert_held"))need(integer(f.cursor(),"count",0)==0,"rs_cursor_must_be_empty");
  switch(action){
   case "rs.extract","rs.craft.start" -> {
    id=str(a,"stack_id","");Entry found=null;
    for(Entry entry:grid.entries())if(entry.id().equalsIgnoreCase(id)){need(found==null,"rs_duplicate_stack_uuid");found=entry;}
    need(found!=null,"rs_stack_changed_refresh_storage");target=row(found);identity(found.item(),a);
    if(action.equals("rs.extract")){
     need(!found.craftable(),"rs_stored_stack_uuid_required");need(found.quantity()>0&&found.quantity()==integer(a,"expected_quantity",-1),"rs_quantity_changed_refresh_storage");mode=str(a,"mode","stack");
    }else{need(f.canCraft(),"rs_autocrafting_unavailable");need(found.craftable(),"rs_craftable_stack_uuid_required");quantity=integer(a,"quantity",0);}
   }
   case "rs.insert_held" -> {
    need(integer(f.cursor(),"count",0)>0,"rs_cursor_empty");identity(f.cursor(),a);need(integer(f.cursor(),"count",0)==integer(a,"expected_count",-1),"rs_cursor_count_changed");target=f.cursor().deepCopy();single=bool(a,"single",false);
   }
   case "rs.crafting.take","rs.crafting.clear" -> {
    need(f.gridType().equals("CRAFTING")&&bool(f.crafting(),"available",false),"rs_crafting_grid_required");
    need(str(f.crafting(),"matrix_sha256","").equals(str(a,"expected_matrix_sha256","")),"rs_crafting_matrix_changed");target=f.crafting().deepCopy();
    if(action.equals("rs.crafting.take")){
     slot=integer(a,"result_slot",-1);need(slot==integer(f.crafting(),"result_slot",-2),"rs_result_slot_changed");
     JsonObject result=object(f.crafting(),"result");need(integer(result,"count",0)>0,"rs_crafting_result_empty");identity(result,a);need(integer(result,"count",0)==integer(a,"expected_count",-1),"rs_crafting_result_changed");
    }
   }
   default -> throw new IllegalArgumentException("unknown_rs_action");
  }
  return new Prepared(f,target,new Command(action,id,mode,quantity,single,slot));
 }
 private static void identity(JsonObject item,JsonObject a){
  need(str(item,"item_id","").equals(str(a,"expected_item","")),"rs_expected_item_mismatch");
  need(str(item,"nbt_sha256","").equals(str(a,"expected_nbt_sha256","")),"rs_expected_nbt_mismatch");
 }
 private JsonObject operation(Frame f,JsonObject a){
  Receipt r=receipts.get(str(a,"operation_token",""));need(r!=null,"rs_operation_expired_or_unknown");
  need(f.session().equals(r.before.session()),"rs_context_changed_refresh_grid");
  JsonObject now=frameJson(f),result=obj("delivery","submitted","action",r.action,"submission_state",str(r.response,"submission_state","unknown"),"age_ms",clock.getAsLong()-r.created,
   "server_success_confirmed",false,"before",frameJson(r.before),"current",now,"verification_hint",hint(r.action),
   "cursor_changed",!r.before.cursor().equals(f.cursor()),"player_inventory_changed",!r.before.inventory().equals(f.inventory()),"crafting_changed",!r.before.crafting().equals(f.crafting()),
   "attribution","client state only; change is not proof that this request caused it");
  if(r.action.equals("rs.extract")||r.action.equals("rs.craft.start")){
   Entry current=null;for(Entry e:grid.entries())if(e.id().equalsIgnoreCase(str(r.args,"stack_id",""))){current=e;break;}
   result.add("target_current",current==null?JsonNull.INSTANCE:row(current));result.add("target_before",r.target.deepCopy());
  }
  return obj("data",result);
 }
 static JsonObject row(Entry e){JsonObject r=e.item().deepCopy();r.addProperty("stack_id",e.id());r.addProperty("other_id",e.otherId());r.addProperty("quantity",e.quantity());r.addProperty("craftable",e.craftable());return r;}
 static JsonObject frameJson(Frame f){return obj("session",f.session(),"menu_id",f.menuId(),"active",f.active(),"grid_type",f.gridType(),"can_craft",f.canCraft(),"cursor",f.cursor(),"crafting",f.crafting(),"inventory",f.inventory(),"search",f.search());}
 private static String hint(String action){return switch(action){
  case "rs.extract" -> "After server sync read rs.operation and a NEW rs.storage snapshot; compare target quantity and cursor (single/half/stack) or inventory (shift). Do not retry automatically.";
  case "rs.insert_held" -> "After server sync read rs.operation, cursor count/NBT and a NEW rs.storage snapshot for the same item/NBT. Partial insertion is possible.";
  case "rs.craft.start" -> "Request sent once; no task ID, acknowledgement or completion proof. Check the RS crafting monitor manually and NEW storage snapshots. Never infer completion from submitted.";
  case "rs.crafting.take" -> "After server sync read rs.operation: exact cursor output and changed inputs; result may refill. No shift/multi-craft loop.";
  default -> "After server sync read rs.operation: crafting inputs cleared and destination inventory/storage. No refund or rollback guarantee.";
 };}
 private static void need(boolean ok,String message){if(!ok)throw new IllegalArgumentException(message);}
 private static String safeMessage(Throwable e){String s=e.getClass().getSimpleName()+": "+Objects.toString(e.getMessage(),"");return s.substring(0,Math.min(512,s.length()));}
 private static JsonObject failure(Throwable e){return obj("error",e instanceof IllegalArgumentException?Objects.toString(e.getMessage(),"rs_invalid_argument"):"rs_api_unavailable_or_incompatible","reason",safeMessage(e));}
}
