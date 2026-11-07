package dev.mcpmcp.curios;
import com.google.gson.*;
import java.util.*;
import static dev.mcpmcp.core.Jsons.*;
import static dev.mcpmcp.curios.AdapterTest.check;
/** Detached boundary: real provider/service, explicitly no Minecraft server. */
final class ServiceTest {
 static final String SESSION="11111111-1111-1111-1111-111111111111",HASH="a".repeat(64),OTHER="b".repeat(64);
 static final class Host implements CuriosAccess {
  int sends,preflights;String last="";boolean failSend;
  JsonObject frame=obj("session",SESSION,"menu_instance",SESSION,"menu_id",7,"state_id",3,"kind","curios_v2","cursor_empty",true,"current_page",0,"total_pages",2,"viewing_cosmetics",false,"has_cosmetics",true,"view_sync_pending",false,"slots",new JsonArray(),"inventory",new JsonArray());
  Host(){frame.getAsJsonArray("slots").add(obj("menu_slot",46,"role","curio","identifier","ring","index",0,"cosmetic",false,"audited",true,"locked",false,"active",true,"may_pickup",true,"can_toggle_render",true,"render",true,"max_stack_size",64,"item",item("test:curio",1,HASH)));frame.getAsJsonArray("inventory").add(obj("inventory_slot",0,"item",item("test:other",1,OTHER)));}
  public JsonObject capture(){return frame.deepCopy();}
  public JsonArray slots(){return frame.getAsJsonArray("slots").deepCopy();}
  public JsonArray candidates(JsonObject args){return slots();}
  public void preflight(String action,JsonObject args){preflights++;}
  public void submit(String action,JsonObject args){sends++;last=action;if(failSend)throw new IllegalStateException("test_network_unknown");}
 }
 static JsonObject item(String id,int count,String hash){return obj("item_id",id,"count",count,"stack_hash",hash);}
 static JsonObject args(){return obj("session",SESSION,"menu_id",7,"state_id",3,"request_id",UUID.randomUUID().toString(),"menu_slot",46,"identifier","ring","index",0,"cosmetic",false,"hotbar_slot",0,"intent","exchange","expected_curio_item","test:curio","expected_curio_count",1,"expected_curio_stack_hash",HASH,"expected_hotbar_item","test:other","expected_hotbar_count",1,"expected_hotbar_stack_hash",OTHER);}
 static void rejects(Runnable call,String code){checksReject(call,code);}
 private static void checksReject(Runnable call,String code){boolean rejected=false;try{call.run();}catch(IllegalArgumentException e){check(e.getMessage().equals(code),"expected "+code+" got "+e.getMessage());rejected=true;}check(rejected,"must reject "+code);}
 static void lifecycle(){
  Host h=new Host();h.failSend=true;CuriosService s=new CuriosService(h);JsonObject a=args();a.addProperty("dry_run",false);JsonObject r=s.act("curios.swap",a);check(r.getAsJsonObject("data").get("status").getAsString().equals("delivery_unknown"),"uncertain send is explicit");s.act("curios.swap",a);check(h.sends==1,"ambiguous send never retried");
  Host sync=new Host();s=new CuriosService(sync);a=args();a.addProperty("dry_run",false);s.act("curios.swap",a);sync.frame.getAsJsonArray("slots").get(0).getAsJsonObject().add("item",item("test:other",1,OTHER));sync.frame.getAsJsonArray("inventory").get(0).getAsJsonObject().add("item",item("test:curio",1,HASH));check(s.read("curios.menu",new JsonObject()).getAsJsonObject("data").get("pending_operation").isJsonNull(),"pending clears only on exact two-sided read-back");
  Host page=new Host();s=new CuriosService(page);JsonObject p=base();p.addProperty("expected_page",0);p.addProperty("page",1);p.addProperty("dry_run",false);s.act("curios.page",p);page.frame.addProperty("current_page",1);check(s.read("curios.menu",new JsonObject()).getAsJsonObject("data").get("pending_operation").isJsonNull(),"page response clears pending");
  Host cosmetics=new Host();s=new CuriosService(cosmetics);p=base();p.addProperty("expected_boolean",false);p.addProperty("value",true);p.addProperty("dry_run",false);s.act("curios.cosmetics.set",p);cosmetics.frame.addProperty("viewing_cosmetics",true);cosmetics.frame.addProperty("view_sync_pending",true);check(!s.read("curios.menu",new JsonObject()).getAsJsonObject("data").get("pending_operation").isJsonNull(),"local cosmetic prediction not server response");cosmetics.frame.addProperty("view_sync_pending",false);check(s.read("curios.menu",new JsonObject()).getAsJsonObject("data").get("pending_operation").isJsonNull(),"cosmetics waits for response-driven rebuild");
  Host fresh=new Host();s=new CuriosService(fresh);a=args();a.addProperty("dry_run",false);s.act("curios.swap",a);fresh.frame.addProperty("menu_instance",UUID.randomUUID().toString());check(s.read("curios.menu",new JsonObject()).getAsJsonObject("data").get("pending_operation").isJsonNull(),"pending never attaches replacement menu");
 }
 static void safety(){lifecycle();
  for(String mutation:List.of("session","menu","state","cursor","slot","identifier","index","cosmetic","locked","active","audited","curio_nbt","hotbar_caps","curio_count","intent")){
   Host h=new Host();CuriosService service=new CuriosService(h);JsonObject a=args(),row=h.frame.getAsJsonArray("slots").get(0).getAsJsonObject();String error;
   switch(mutation){
    case "session"->{h.frame.addProperty("session",UUID.randomUUID().toString());error="curios_stale_session";}
    case "menu"->{h.frame.addProperty("menu_id",8);error="curios_menu_changed";}
    case "state"->{h.frame.addProperty("state_id",4);error="curios_state_changed";}
    case "cursor"->{h.frame.addProperty("cursor_empty",false);error="curios_cursor_not_empty";}
    case "slot"->{a.addProperty("menu_slot",45);error="curios_real_curio_slot_required";}
    case "identifier"->{a.addProperty("identifier","charm");error="curios_slot_identity_changed";}
    case "index"->{a.addProperty("index",1);error="curios_slot_identity_changed";}
    case "cosmetic"->{a.addProperty("cosmetic",true);error="curios_slot_namespace_changed";}
    case "locked","active","audited"->{row.addProperty(mutation,mutation.equals("locked"));error="curios_slot_read_only";}
    case "curio_nbt"->{row.getAsJsonObject("item").addProperty("stack_hash",OTHER);error="curios_stack_changed";}
    case "hotbar_caps"->{h.frame.getAsJsonArray("inventory").get(0).getAsJsonObject().getAsJsonObject("item").addProperty("stack_hash",HASH);error="curios_stack_changed";}
    case "curio_count"->{row.getAsJsonObject("item").addProperty("count",2);error="curios_item_or_count_changed";}
    default->{a.addProperty("intent","unequip");error="curios_intent_stack_mismatch";}
   }
   rejects(()->service.act("curios.swap",a),error);check(h.sends==0&&h.preflights==0,"reject before native predicates: "+mutation);
  }
  Host h=new Host();CuriosService service=new CuriosService(h);JsonObject a=args();a.addProperty("hotbar_slot",9);rejects(()->service.act("curios.swap",a),"schema_validation_failed");a.addProperty("hotbar_slot",0);a.addProperty("unexpected",true);rejects(()->service.act("curios.swap",a),"unknown_unexpected");
  JsonObject read=service.read("curios.slots",obj("limit",1));check(read.getAsJsonObject("data").getAsJsonArray("rows").size()==1,"bounded page");
  read=service.read("curios.slots",obj("limit",0));check(read.getAsJsonObject("data").get("error").getAsString().equals("schema_validation_failed"),"observer error retained inside data");
  JsonObject submitted=args();submitted.addProperty("dry_run",false);service.act("curios.swap",submitted);JsonObject next=args();next.addProperty("dry_run",false);rejects(()->service.act("curios.swap",next),"curios_operation_pending_read_or_close");
  JsonObject altered=submitted.deepCopy();altered.addProperty("intent","equip");rejects(()->service.act("curios.swap",altered),"curios_request_id_reused");check(h.sends==1,"pending/reused UUID no extra send");
  for(String action:List.of("curios.open","curios.close","curios.page","curios.cosmetics.set","curios.render.set")){
   Host host=new Host();CuriosService s=new CuriosService(host);JsonObject p=base();
   if(action.equals("curios.open"))host.frame.addProperty("kind","player_closed");
   if(action.equals("curios.page")){p.addProperty("expected_page",0);p.addProperty("page",1);}
   if(action.equals("curios.cosmetics.set")){p.addProperty("expected_boolean",false);p.addProperty("value",true);}
   if(action.equals("curios.render.set")){p.addProperty("menu_slot",46);p.addProperty("identifier","ring");p.addProperty("index",0);p.addProperty("expected_boolean",true);p.addProperty("value",false);p.addProperty("expected_stack_hash",HASH);}
   s.act(action,p);check(host.sends==0,"all actions default dry-run: "+action);p.addProperty("dry_run",false);s.act(action,p);check(host.sends==1&&host.last.equals(action),"one native action: "+action);s.act(action,p);check(host.sends==1,"replay receipt: "+action);
  }
  for(String action:List.of("curios.open","curios.close")){
   Host host=new Host();host.frame.addProperty("kind","other");CuriosService s=new CuriosService(host);rejects(()->s.act(action,base()),action.equals("curios.open")?"curios_close_existing_screen_first":"curios_v2_menu_required");check(host.sends==0,"never take over other screen");
  }
  Host view=new Host();CuriosService s=new CuriosService(view);JsonObject p=base();p.addProperty("expected_boolean",true);p.addProperty("value",false);check(s.act("curios.cosmetics.set",p).getAsJsonObject("data").get("status").getAsString().equals("unchanged"),"desired boolean is idempotent no-op");check(view.preflights==0&&view.sends==0,"no-op no native toggle");
  p=base();p.addProperty("expected_page",0);p.addProperty("page",2);JsonObject badPage=p;rejects(()->s.act("curios.page",badPage),"curios_adjacent_page_required");
 }
 static JsonObject base(){return obj("session",SESSION,"menu_id",7,"state_id",3,"request_id",UUID.randomUUID().toString());}
 static void run(){safety();Host h=new Host();CuriosService s=new CuriosService(h);JsonObject a=args();JsonObject result=s.act("curios.swap",a);check(result.getAsJsonObject("data").get("status").getAsString().equals("dry_run"),"dry-run default receipt");check(h.sends==0&&h.preflights==1,"dry-run native checks but zero sends");a.addProperty("dry_run",false);result=s.act("curios.swap",a);check(h.sends==1,"one submission");check(result.getAsJsonObject("data").get("status").getAsString().equals("submitted")&&!result.getAsJsonObject("data").get("server_confirmed").getAsBoolean(),"submission not confirmation");s.act("curios.swap",a.deepCopy());check(h.sends==1,"same UUID never re-sends");}
}
