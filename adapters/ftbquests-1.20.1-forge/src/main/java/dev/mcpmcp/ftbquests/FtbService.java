package dev.mcpmcp.ftbquests;

import com.google.gson.*;
import java.util.*;
import java.util.function.LongSupplier;
import static dev.mcpmcp.ftbquests.Json.*;

/** Bounded snapshot paging, explicit reviewed batch and one-send receipts. No tick resend. */
public final class FtbService {
 private final FtbAccess access;private final LongSupplier clock;
 private record Cursor(String query,String version,String session,int offset,long expires){}
 private record Preview(String session,String hash,int count,long expires){}
 private record Receipt(String payload,JsonObject result){}
 private final Map<String,Cursor> cursors=new LinkedHashMap<>();
 private final Map<String,Preview> previews=new LinkedHashMap<>();
 private final Map<String,Receipt> receipts=new LinkedHashMap<>();
 private JsonObject pending;
 public FtbService(FtbAccess access){this(access,System::currentTimeMillis);}
 FtbService(FtbAccess access,LongSupplier clock){this.access=Objects.requireNonNull(access);this.clock=clock;}
 public synchronized JsonObject read(String name,JsonObject args){
  try{
   Contract.validate(name,args);if(name.equals("ftbquests"))return Contract.discovery();
   if(name.equals("ftbquests.receipt")){String unavailable=null;try{updatePending(access.capture());}catch(IllegalArgumentException|IllegalStateException e){unavailable=e.getMessage();}Receipt r=receipts.get(str(args,"request_id",""));need(r!=null,"ftbquests_receipt_not_found");JsonObject d=r.result().getAsJsonObject("data").deepCopy();d.add("readback_unavailable",GSON.toJsonTree(unavailable));return obj("data",d);}
   JsonObject f=access.capture();updatePending(f);purge();
   if(name.equals("ftbquests.book"))return obj("data",book(f));
   if(name.equals("ftbquests.quest")){JsonObject q=find(array(f,"quests"),str(args,"quest_id",""));need(q!=null,"ftbquests_quest_missing_or_not_visible");JsonObject d=base(f);d.add("quest",q.deepCopy());return obj("data",d);}
   JsonArray source=switch(name){case "ftbquests.chapters"->chapterStats(f);case "ftbquests.quests"->array(f,"quests");case "ftbquests.rewards"->array(f,"rewards");case "ftbquests.claimable"->array(f,"preview");default->throw new IllegalArgumentException("ftbquests_unknown_read");};
   JsonArray rows=new JsonArray();for(JsonElement e:source)if(matches(e.getAsJsonObject(),args))rows.add(e);
   int limit=integer(args,"limit",8),offset=0;long expires=clock.getAsLong()+30000;String query=query(name,args);
   if(args.has("cursor")){Cursor c=cursors.get(str(args,"cursor",""));need(c!=null&&clock.getAsLong()<c.expires(),"ftbquests_stale_cursor");need(c.query().equals(query)&&c.version().equals(str(f,"version",""))&&c.session().equals(str(f,"session","")),"ftbquests_stale_cursor_or_query_changed");offset=c.offset();expires=c.expires();need(offset<rows.size(),"ftbquests_cursor_out_of_range");}
   int end=offset,bytes=0;JsonArray page=new JsonArray();while(end<rows.size()&&end-offset<limit){int n=rows.get(end).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;need(n<=1048576,"ftbquests_row_response_budget");if(bytes+n>1048576)break;bytes+=n;page.add(rows.get(end++).deepCopy());}
   String next=null;if(end<rows.size()){need(cursors.size()<256,"ftbquests_cursor_budget");next=UUID.randomUUID().toString();cursors.put(next,new Cursor(query,str(f,"version",""),str(f,"session",""),end,expires));}
   JsonObject d=base(f);d.add("rows",page);d.addProperty("offset",offset);d.addProperty("limit",limit);d.addProperty("total_rows",rows.size());d.addProperty("complete",next==null);d.add("next_cursor",GSON.toJsonTree(next));
   if(name.equals("ftbquests.claimable")){
    JsonObject summary=claimSummary(f);d.add("native_batch",summary);d.addProperty("preview_complete",next==null);
    String token=null;int count=integer(summary,"claimable_count",0);
    if(next==null&&bool(summary,"safe_to_submit",false)&&count>0&&count<=512){need(previews.size()<128,"ftbquests_preview_budget");token=UUID.randomUUID().toString();previews.put(token,new Preview(str(f,"session",""),str(summary,"claimable_hash",""),count,Math.min(expires,clock.getAsLong()+30000)));}
    d.add("preview_token",GSON.toJsonTree(token));
   }
   return obj("data",d);
  }catch(IllegalArgumentException|IllegalStateException e){return obj("data",obj("source","ftbquests","available",false,"error",e.getMessage(),"complete",false,"state","unknown","pending_operation",pending==null?JsonNull.INSTANCE:pending.deepCopy()));}
 }
 public synchronized JsonObject act(String name,JsonObject args){
  Contract.validate(name,args);purge();String request=str(args,"request_id",""),payload=name+"/"+canonical(args);
  Receipt prior=receipts.get(request);if(prior!=null){need(prior.payload().equals(payload),"ftbquests_request_id_reused");return prior.result().deepCopy();}
  JsonObject f=access.capture();updatePending(f);need(pending==null,"ftbquests_operation_pending_read_claimed_before_new_uuid");
  List<String> targets=preflight(name,args,f);
  JsonObject after=access.capture();updatePending(after);need(pending==null,"ftbquests_operation_pending");need(str(f,"version","").equals(str(after,"version","!")),"ftbquests_snapshot_changed");preflight(name,args,after);
  boolean dry=bool(args,"dry_run",true);JsonObject result=obj("data",obj("source","ftbquests","status",dry?"dry_run":"submitted","request_id",request,"action",name,"session",str(f,"session",""),"reward_ids",targets,"packet_count_max",dry?0:1,"server_confirmed",false,"effects_confirmed",false,"native_scope",name.endsWith("claim_all")?"all_not_excluded":name.endsWith("task.check")?"single_checkmark":"single_reward"));
  if(dry)return result;
  need(receipts.size()<512,"ftbquests_receipt_budget_restart_requires_manual_reconciliation");
  receipts.put(request,new Receipt(payload,result.deepCopy()));
  pending=obj("request_id",request,"action",name,"player_uuid",str(f,"player_uuid",""),"team_uuid",str(f,"team_uuid",""),"context",str(f,"context",""),"reward_ids",targets,"submitted_at_ms",clock.getAsLong(),"status","submitted");
  try{access.submit(name,args);}catch(RuntimeException|LinkageError e){result.getAsJsonObject("data").addProperty("status","delivery_unknown");result.getAsJsonObject("data").addProperty("error",e.getMessage());pending.addProperty("status","delivery_unknown");receipts.put(request,new Receipt(payload,result.deepCopy()));}
  return result;
 }
 private List<String> preflight(String name,JsonObject a,JsonObject f){
  need(bool(f,"available",false),"ftbquests_unavailable");need(str(a,"session","").equals(str(f,"session","!")),"ftbquests_stale_session");need(!bool(f,"team_locked",true),"ftbquests_team_locked");
  if(name.endsWith("claim_all")){
   JsonObject s=claimSummary(f);int count=integer(s,"claimable_count",0);
   need(str(a,"scope","").equals("all_not_excluded"),"ftbquests_native_scope_required");need(bool(s,"safe_to_submit",false),"ftbquests_native_scope_unreviewable");
   need(count>0&&count==integer(a,"expected_claimable_count",-1)&&count<=integer(a,"max_rewards",0),"ftbquests_claimable_count_or_budget_changed");need(str(s,"claimable_hash","").equals(str(a,"expected_claimable_hash","!")),"ftbquests_claimable_hash_changed");
   Preview p=previews.get(str(a,"preview_token",""));need(p!=null&&clock.getAsLong()<p.expires()&&p.session().equals(str(f,"session",""))&&p.hash().equals(str(s,"claimable_hash",""))&&p.count()==count,"ftbquests_complete_fresh_preview_required");
   List<String> ids=new ArrayList<>();for(JsonElement e:array(f,"preview")){JsonObject r=e.getAsJsonObject();if(bool(r,"can_claim",false)&&!bool(r,"exclude_from_claim_all",true))ids.add(str(r,"id",""));}return ids;
  }
  if(name.equals("ftbquests.task.check")){
   need(bool(a,"acknowledge_read",false),"ftbquests_acknowledge_read_required");
   for(JsonElement e:array(f,"quests")){JsonObject q=e.getAsJsonObject();if(!bool(q,"details_visible",false)||!q.has("tasks")||q.get("tasks").isJsonNull())continue;JsonObject task=find(array(q,"tasks"),str(a,"task_id",""));if(task==null)continue;
    need(str(task,"type","").equals("ftbquests:checkmark")&&!bool(task,"consumes_resources",true),"ftbquests_checkmark_task_required");need(!bool(task,"completed",true),"ftbquests_task_already_completed");need(bool(q,"can_start",false),"ftbquests_task_not_startable");need(bool(q,"checkmark_route_safe",false),"ftbquests_checkmark_auto_effects_require_native_ui");need(str(task,"definition_hash","").equals(str(a,"expected_task_hash","!")),"ftbquests_task_definition_changed");return List.of(str(task,"id",""));}
   throw new IllegalArgumentException("ftbquests_task_missing_or_not_visible");
  }
  JsonObject r=find(array(f,"rewards"),str(a,"reward_id",""));need(r!=null,"ftbquests_reward_missing_or_not_visible");need(!bool(r,"claimed",true),"ftbquests_reward_already_claimed");need(bool(r,"quest_completed",false)&&bool(r,"native_can_claim",false)&&bool(r,"can_claim",false)&&!bool(r,"reward_blocked",true),"ftbquests_reward_not_claimable");need(str(a,"expected_reward_hash","").equals(str(r,"reward_hash","!")),"ftbquests_reward_changed");
  if(name.endsWith("choose")){
   need(bool(r,"choice",false)&&bool(r,"choice_route_safe",false),"ftbquests_choice_route_unsafe");need(str(a,"expected_table_hash","").equals(str(r,"table_hash","!")),"ftbquests_choice_table_changed");JsonArray choices=array(r,"choice_candidates");int i=integer(a,"choice_index",-1);need(i>=0&&i<choices.size()&&choices.size()==integer(r,"table_candidates_total",-1)&&integer(choices.get(i).getAsJsonObject(),"choice_index",-1)==i,"ftbquests_choice_index_invalid");
  }else need(!bool(r,"choice",true)&&bool(r,"ordinary_route_safe",false),"ftbquests_ordinary_route_required");
  return List.of(str(r,"id",""));
 }
 private void updatePending(JsonObject f){
  if(pending==null)return;String id=str(pending,"request_id","");Receipt r=receipts.get(id);
  if(!str(pending,"context","").equals(str(f,"context","!"))){JsonObject result=r.result().deepCopy();result.getAsJsonObject("data").addProperty("status","context_lost_delivery_unresolved");receipts.put(id,new Receipt(r.payload(),result));pending.addProperty("status","context_lost_delivery_unresolved");
   if(!str(pending,"player_uuid","").equals(str(f,"player_uuid","!"))||!str(pending,"team_uuid","").equals(str(f,"team_uuid","!")))return;
  }
  boolean checkmark=str(pending,"action","").equals("ftbquests.task.check");boolean claimed=true;for(JsonElement e:array(pending,"reward_ids")){
   if(checkmark){JsonObject task=null;for(JsonElement q:array(f,"quests")){JsonObject qr=q.getAsJsonObject();if(qr.has("tasks")&&!qr.get("tasks").isJsonNull()){task=find(array(qr,"tasks"),e.getAsString());if(task!=null)break;}}claimed&=task!=null&&bool(task,"completed",false);}
   else{JsonObject reward=find(array(f,"rewards"),e.getAsString());claimed&=reward!=null&&bool(reward,"claimed",false);}
  }
  if(claimed){JsonObject result=r.result().deepCopy();result.getAsJsonObject("data").addProperty("status",checkmark?"task_completed_read_back":"claimed_read_back");result.getAsJsonObject("data").addProperty(checkmark?"task_completed_observed":"claimed_observed",true);result.getAsJsonObject("data").addProperty("effects_confirmed",false);receipts.put(id,new Receipt(r.payload(),result));pending=null;}
 }
 private JsonObject base(JsonObject f){return obj("source","ftbquests","available",true,"scope","client_synced_self_team","session",str(f,"session",""),"version",str(f,"version",""),"player_uuid",str(f,"player_uuid",""),"team_uuid",str(f,"team_uuid",""),"observed_at_ms",f.get("observed_at_ms"),"server_state","client_synced_may_lag","pending_operation",pending==null?JsonNull.INSTANCE:pending.deepCopy());}
 private JsonObject book(JsonObject f){JsonObject b=base(f);b.add("title",f.get("title"));b.add("totals",counts(array(f,"quests")));JsonArray chapters=chapterStats(f),groups=new JsonArray();for(JsonElement e:array(f,"groups")){JsonObject g=e.getAsJsonObject().deepCopy();JsonArray qs=new JsonArray();Set<String> ids=new HashSet<>();for(JsonElement c:chapters)if(str(c.getAsJsonObject(),"group_id","").equals(str(g,"id","")))ids.add(str(c.getAsJsonObject(),"id",""));for(JsonElement q:array(f,"quests"))if(ids.contains(str(q.getAsJsonObject(),"chapter_id","")))qs.add(q);g.add("counts",counts(qs));groups.add(g);}b.add("groups",slice(groups,128));b.addProperty("groups_total",groups.size());b.addProperty("groups_truncated",groups.size()>128);b.add("chapters",slice(chapters,128));b.addProperty("chapters_total",chapters.size());b.addProperty("chapters_truncated",chapters.size()>128);b.addProperty("statistics_scope","visible_quests_only; hidden details and claims remain unknown");b.add("native_batch",claimSummary(f));return b;}
 private static JsonArray slice(JsonArray a,int n){JsonArray b=new JsonArray();for(int i=0;i<Math.min(n,a.size());i++)b.add(a.get(i).deepCopy());return b;}
 private static JsonArray chapterStats(JsonObject f){JsonArray rows=new JsonArray();for(JsonElement e:array(f,"chapters")){JsonObject c=e.getAsJsonObject().deepCopy();JsonArray qs=new JsonArray();for(JsonElement q:array(f,"quests"))if(str(q.getAsJsonObject(),"chapter_id","").equals(str(c,"id","")))qs.add(q);c.add("counts",counts(qs));rows.add(c);}return rows;}
 private static JsonObject counts(JsonArray qs){int completed=0,started=0,available=0,locked=0,claimable=0,unknown=0;for(JsonElement e:qs){JsonObject q=e.getAsJsonObject();if(bool(q,"completed",false))completed++;switch(str(q,"status","")){case "in_progress"->started++;case "available"->available++;case "locked"->locked++;default->{}}if(!q.has("claimable")||q.get("claimable").isJsonNull())unknown++;else if(bool(q,"claimable",false))claimable++;}return obj("visible_quests",qs.size(),"completed",completed,"in_progress",started,"available",available,"locked",locked,"claimable_quests",claimable,"claimability_unknown_quests",unknown);}
 static JsonObject claimSummary(JsonObject f){
  int count=0,excluded=0,choice=0,blocked=0;LinkedHashSet<String> effects=new LinkedHashSet<>();boolean safe=bool(f,"native_scope_safe",false);
  for(JsonElement e:array(f,"preview")){JsonObject r=e.getAsJsonObject();if(bool(r,"exclude_from_claim_all",true))excluded++;if(bool(r,"choice",false))choice++;if(!bool(r,"can_claim",false))blocked++;if(bool(r,"can_claim",false)&&!bool(r,"exclude_from_claim_all",true)){count++;safe&=bool(r,"ordinary_route_safe",false)&&!bool(r,"choice",true)&&!bool(r,"claimed",true);for(JsonElement x:array(r,"effects"))effects.add(x.getAsString());}}
  String hash=hash(obj("scope","all_not_excluded","session",str(f,"session",""),"version",str(f,"version",""),"rows",array(f,"preview"),"native_scope_safe",safe));
  return obj("scope","all_not_excluded","claimable_hash",hash,"claimable_count",count,"preview_rows",array(f,"preview").size(),"excluded_count",excluded,"choice_count",choice,"blocked_or_locked_count",blocked,"native_scope_blockers",f.get("native_scope_blockers"),"effects",effects,"safe_to_submit",safe&&count<=512,"server_races_possible",true);
 }
 private static String query(String name,JsonObject args){JsonObject q=args.deepCopy();q.remove("cursor");return name+"/"+canonical(q);}
 private static boolean matches(JsonObject row,JsonObject args){
  for(String k:List.of("chapter_id","group_id","quest_id","status","claimed","exclude_from_claim_all"))if(args.has(k)&&(!row.has(k)||!args.get(k).equals(row.get(k))))return false;
  if(args.has("claimable")){String k=row.has("can_claim")?"can_claim":"claimable";if(!row.has(k)||row.get(k).isJsonNull()||!row.get(k).equals(args.get("claimable")))return false;}
  String q=str(args,"query","").toLowerCase(Locale.ROOT);return q.isEmpty()||searchText(row).toLowerCase(Locale.ROOT).contains(q);
 }
 private static String searchText(JsonElement e){StringBuilder b=new StringBuilder();if(e.isJsonObject()){JsonObject o=e.getAsJsonObject();for(String k:List.of("title","subtitle","description","tasks","rewards","quest_title"))if(o.has(k))b.append(searchText(o.get(k))).append(' ');if(o.has("text"))b.append(str(o,"text",""));}else if(e.isJsonArray())for(JsonElement x:e.getAsJsonArray())b.append(searchText(x)).append(' ');return b.toString();}
 static JsonObject find(JsonArray rows,String id){for(JsonElement e:rows){JsonObject r=e.getAsJsonObject();if(str(r,"id","").equals(id))return r;}return null;}
 private void purge(){long now=clock.getAsLong();cursors.entrySet().removeIf(e->now>=e.getValue().expires());previews.entrySet().removeIf(e->now>=e.getValue().expires());}
}
