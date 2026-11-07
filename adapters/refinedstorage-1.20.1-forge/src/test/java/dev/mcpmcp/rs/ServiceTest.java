package dev.mcpmcp.rs;
import com.google.gson.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static dev.mcpmcp.rs.GridAccess.*;
import static dev.mcpmcp.core.Jsons.*;
import static dev.mcpmcp.rs.AdapterTest.check;
/** Exercises public read/act boundary with a detached client grid, never server mutation. */
final class ServiceTest {
 private static int tests;
 private static final String STORED="11111111-1111-1111-1111-111111111111",CRAFT="22222222-2222-2222-2222-222222222222";
 private static final String NBT="a".repeat(64),MATRIX="b".repeat(64);
 static void run(){
  test("closed schemas",ServiceTest::schemas);
  test("frozen pagination and replay",ServiceTest::pages);
  test("bounded and invalid cursors",ServiceTest::cursors);
  test("snapshot expires and context changes",ServiceTest::expiry);
  test("raw rows and bytes bounded",ServiceTest::budgets);
  test("default dry run",ServiceTest::dryRun);
  test("extract preflight and flags",ServiceTest::extraction);
  test("insert expected cursor identity",ServiceTest::insert);
  test("craftable UUID and amount",ServiceTest::autocraft);
  test("crafting matrix and output identity",ServiceTest::crafting);
  test("same UUID submits once; conflict refuses",ServiceTest::idempotency);
  test("failed transport is uncertain and not retried",ServiceTest::uncertain);
  test("post-send readback failure cannot resend",ServiceTest::readbackFailure);
  test("operation readback and TTL",ServiceTest::receipts);
  test("immediate precommit revalidation",ServiceTest::race);
  test("inactive grids and wrong type refuse",ServiceTest::inactive);
  test("canonical identity ignores object order",ServiceTest::identity);
  System.out.println("ServiceTest: "+tests+" behavioral scenarios passed");
 }
 private static void test(String n,Runnable r){try{r.run();tests++;}catch(Throwable e){throw new AssertionError(n,e);}}
 static final class Fake implements GridAccess {
  String session="session-one",type="CRAFTING";int menu=7,sends,captures;boolean active=true,canCraft=true,throwSend,throwAfter,changeOnSecond;
  JsonObject cursor=item("minecraft:air",0),inv=obj("items",new JsonArray()),crafting=matrix();
  List<Entry> rows=new ArrayList<>(List.of(entry(STORED,false,16),entry(CRAFT,true,0)));Command command;
  public Frame capture(){
   captures++;if(throwAfter&&sends>0)throw new IllegalArgumentException("fake_readback_unavailable");
   if(changeOnSecond&&captures>=2)cursor=item("minecraft:stone",1);
   return new Frame(session,menu,active,type,canCraft,cursor.deepCopy(),crafting.deepCopy(),inv.deepCopy(),"");
  }
  public List<Entry> entries(){return List.copyOf(rows);}
  public void submit(Command c){sends++;command=c;if(throwSend)throw new IllegalStateException("send uncertain");}
 }
 private static JsonObject item(String id,int count){return obj("item_id",id,"name",id,"count",count,"nbt","null","nbt_truncated",false,"nbt_sha256",NBT);}
 private static JsonObject matrix(){return obj("available",true,"matrix_sha256",MATRIX,"result_slot",13,"result",item("minecraft:stone",4),"inputs",new JsonArray());}
 private static Entry entry(String id,boolean craft,long qty){return new Entry(id,"",craft,qty,item("minecraft:stone",(int)qty));}
 private static JsonObject baseArgs(){return obj("session","session-one","menu_id",7,"request_id",UUID.randomUUID().toString(),"dry_run",false);}
 private static JsonObject extract(){JsonObject a=baseArgs();a.addProperty("stack_id",STORED);a.addProperty("expected_item","minecraft:stone");a.addProperty("expected_nbt_sha256",NBT);a.addProperty("expected_quantity",16);return a;}
 private static JsonObject insertArgs(){JsonObject a=baseArgs();a.addProperty("expected_item","minecraft:stone");a.addProperty("expected_nbt_sha256",NBT);a.addProperty("expected_count",8);return a;}
 private static JsonObject craftArgs(){JsonObject a=extract();a.remove("expected_quantity");a.addProperty("stack_id",CRAFT);a.addProperty("quantity",4);return a;}
 private static JsonObject take(){JsonObject a=insertArgs();a.addProperty("expected_count",4);a.addProperty("result_slot",13);a.addProperty("expected_matrix_sha256",MATRIX);return a;}
 private static JsonObject clear(){JsonObject a=baseArgs();a.addProperty("expected_matrix_sha256",MATRIX);return a;}
 private static boolean hasError(JsonObject result){return result.has("error")||(result.has("data")&&result.getAsJsonObject("data").has("error"));}
 private static void errorIs(JsonObject result,String code){JsonObject detail=result.has("data")?result.getAsJsonObject("data"):result;check(str(detail,"error","").equals(code),"expected "+code+" but "+result);}
 private static JsonObject data(JsonObject o){check(!o.has("error"),o.toString());return o.getAsJsonObject("data");}
 private static void schemas(){
  Fake f=new Fake();RsService s=new RsService(f);
  for(String k:RsProvider.READS){dev.mcpmcp.core.PluginValidation.schema(RsProvider.schema(k));check(hasError(s.read(k,obj("unknown",true))),"observer closed");}
  for(String k:RsProvider.ACTIONS){check(s.act(k,obj("unknown",true)).has("error"),"action closed");}
  JsonObject a=extract();a.addProperty("stack_id","1-1-1-1-1");check(s.act("rs.extract",a).has("error"),"short UUID rejected");
  a=extract();a.addProperty("expected_quantity",1.1);check(s.act("rs.extract",a).has("error"),"fraction rejected");
  a=extract();a.addProperty("mode","all");check(s.act("rs.extract",a).has("error"),"unknown mode rejected");check(f.sends==0,"no invalid sends");
 }
 private static void pages(){
  Fake f=new Fake();RsService s=new RsService(f);JsonObject first=data(s.read("rs.storage",obj("limit",1)));
  check(first.get("total_matches").getAsInt()==2,"exact total");check(first.getAsJsonArray("entries").get(0).getAsJsonObject().get("stack_id").getAsString().equals(STORED),"UUID sorted");
  String cursor=str(first,"next_cursor","");f.rows.clear();
  JsonObject next=data(s.read("rs.storage",obj("limit",1,"cursor",cursor)));check(next.getAsJsonArray("entries").size()==1&&!bool(next,"has_more",true),"frozen second page");
  check(next.equals(data(s.read("rs.storage",obj("limit",1,"cursor",cursor))))||next.getAsJsonArray("entries").equals(data(s.read("rs.storage",obj("limit",1,"cursor",cursor))).getAsJsonArray("entries")),"repeat cursor stable");
  check(data(s.read("rs.storage",obj("query","absent"))).get("total_matches").getAsInt()==0,"zero results complete");
 }
 private static void cursors(){
  RsService s=new RsService(new Fake());
  check(hasError(s.read("rs.storage",obj("limit",0))),"limit zero");check(hasError(s.read("rs.storage",obj("limit",65))),"limit 65");
  errorIs(s.read("rs.storage",obj("cursor","garbage")),"rs_invalid_cursor");
  String c=str(data(s.read("rs.storage",obj("limit",1))),"next_cursor","");
  errorIs(s.read("rs.storage",obj("limit",2,"cursor",c)),"rs_cursor_parameters_changed");
  errorIs(s.read("rs.storage",obj("limit",1,"query","stone","cursor",c)),"rs_cursor_parameters_changed");
  errorIs(s.read("rs.storage",obj("limit",1,"cursor",c.substring(0,c.lastIndexOf(':'))+":99")),"rs_invalid_cursor");
 }
 private static void expiry(){
  Fake f=new Fake();AtomicLong t=new AtomicLong();RsService s=new RsService(f,t::get);
  String c=str(data(s.read("rs.storage",obj("limit",1))),"next_cursor","");t.set(30001);errorIs(s.read("rs.storage",obj("cursor",c,"limit",1)),"rs_snapshot_expired_refresh");
  c=str(data(s.read("rs.storage",obj("limit",1))),"next_cursor","");f.session="new-screen";errorIs(s.read("rs.storage",obj("cursor",c,"limit",1)),"rs_snapshot_expired_refresh");errorIs(s.act("rs.extract",extract()),"rs_context_changed_refresh_grid");
 }
 private static void budgets(){
  Fake f=new Fake();RsService s=new RsService(f);f.rows=Collections.nCopies(16385,entry(STORED,false,1));errorIs(s.read("rs.storage",obj()),"rs_snapshot_entry_limit");
  f.rows=List.of(entry(STORED,false,1),entry(STORED,false,1));errorIs(s.read("rs.storage",obj()),"rs_duplicate_stack_uuid");
  f.rows=new ArrayList<>();for(int i=0;i<140;i++){JsonObject v=item("minecraft:stone",1);v.addProperty("nbt","x".repeat(65536));f.rows.add(new Entry(new UUID(0,i+1).toString(),"",false,1,v));}errorIs(s.read("rs.storage",obj()),"rs_snapshot_byte_limit");
 }
 private static void dryRun(){Fake f=new Fake();RsService s=new RsService(f);JsonObject a=extract();a.remove("dry_run");JsonObject r=s.act("rs.extract",a);check(bool(r,"preflight_ok",false)&&str(r,"delivery","").equals("not_submitted")&&f.sends==0,"dry default");a.addProperty("dry_run",false);check(!s.act("rs.extract",a).has("error")&&f.sends==1,"same id can submit after preflight");}
 private static void extraction(){
  for(String mode:List.of("single","half","stack","shift")){Fake f=new Fake();RsService s=new RsService(f);JsonObject a=extract();a.addProperty("mode",mode);JsonObject r=s.act("rs.extract",a);check(f.sends==1&&f.command.mode().equals(mode)&&!bool(r,"server_success_confirmed",true)&&r.has("verification_hint"),"one native extract with hint");}
  Fake f=new Fake();RsService s=new RsService(f);JsonObject a=extract();a.addProperty("menu_id",9);errorIs(s.act("rs.extract",a),"rs_menu_changed");
  a=extract();a.addProperty("expected_item","minecraft:dirt");errorIs(s.act("rs.extract",a),"rs_expected_item_mismatch");
  a=extract();a.addProperty("expected_nbt_sha256","c".repeat(64));errorIs(s.act("rs.extract",a),"rs_expected_nbt_mismatch");
  a=extract();a.addProperty("expected_quantity",15);errorIs(s.act("rs.extract",a),"rs_quantity_changed_refresh_storage");
  a=extract();a.addProperty("stack_id",CRAFT);errorIs(s.act("rs.extract",a),"rs_stored_stack_uuid_required");
  f.rows.clear();errorIs(s.act("rs.extract",extract()),"rs_stack_changed_refresh_storage");
  f.cursor=item("minecraft:stone",1);errorIs(s.act("rs.extract",extract()),"rs_cursor_must_be_empty");check(f.sends==0,"no failed extraction sends");
 }
 private static void insert(){Fake f=new Fake();RsService s=new RsService(f);errorIs(s.act("rs.insert_held",insertArgs()),"rs_cursor_empty");f.cursor=item("minecraft:stone",7);errorIs(s.act("rs.insert_held",insertArgs()),"rs_cursor_count_changed");f.cursor=item("minecraft:stone",8);JsonObject a=insertArgs();a.addProperty("expected_nbt_sha256","c".repeat(64));errorIs(s.act("rs.insert_held",a),"rs_expected_nbt_mismatch");a=insertArgs();a.addProperty("single",true);check(!s.act("rs.insert_held",a).has("error")&&f.sends==1&&f.command.single(),"single insert");}
 private static void autocraft(){Fake f=new Fake();RsService s=new RsService(f);JsonObject a=craftArgs();a.addProperty("stack_id",STORED);errorIs(s.act("rs.craft.start",a),"rs_craftable_stack_uuid_required");f.canCraft=false;errorIs(s.act("rs.craft.start",craftArgs()),"rs_autocrafting_unavailable");f.canCraft=true;a=craftArgs();a.addProperty("quantity",4097);check(s.act("rs.craft.start",a).has("error"),"bounded craft");JsonObject r=s.act("rs.craft.start",craftArgs());check(f.sends==1&&f.command.quantity()==4&&str(r,"delivery","").equals("submitted"),"one craft request");}
 private static void crafting(){Fake f=new Fake();RsService s=new RsService(f);JsonObject a=take();a.addProperty("result_slot",12);errorIs(s.act("rs.crafting.take",a),"rs_result_slot_changed");a=take();a.addProperty("expected_matrix_sha256","c".repeat(64));errorIs(s.act("rs.crafting.take",a),"rs_crafting_matrix_changed");check(!s.act("rs.crafting.take",take()).has("error")&&f.command.slot()==13,"result slot native click");check(!s.act("rs.crafting.clear",clear()).has("error")&&f.sends==2,"clear native request");f.type="PATTERN";errorIs(s.act("rs.crafting.clear",clear()),"rs_crafting_grid_required");}
 private static void idempotency(){Fake f=new Fake();RsService s=new RsService(f);JsonObject a=extract();JsonObject r=s.act("rs.extract",a);f.rows.clear();f.cursor=item("minecraft:stone",1);JsonObject replay=s.act("rs.extract",a);check(f.sends==1&&bool(replay,"replayed",false)&&r.get("operation_token").equals(replay.get("operation_token")),"replay before live mutation checks");a.addProperty("expected_quantity",8);errorIs(s.act("rs.extract",a),"rs_request_id_conflict");}
 private static void uncertain(){Fake f=new Fake();f.throwSend=true;RsService s=new RsService(f);JsonObject a=extract(),r=s.act("rs.extract",a);check(str(r,"submission_state","").equals("transport_exception_outcome_unknown")&&!r.has("error"),"unknown after failed send");s.act("rs.extract",a);check(f.sends==1,"uncertain call never retry");}
 private static void readbackFailure(){Fake f=new Fake();f.throwAfter=true;RsService s=new RsService(f);JsonObject a=extract(),r=s.act("rs.extract",a);check(r.has("readback_error")&&str(r,"delivery","").equals("submitted"),"readback not submission failure");f.throwAfter=false;s.act("rs.extract",a);check(f.sends==1,"failed readback no second native call");}
 private static void receipts(){Fake f=new Fake();AtomicLong t=new AtomicLong();RsService s=new RsService(f,t::get);JsonObject r=s.act("rs.extract",extract());String token=str(r,"operation_token","");f.cursor=item("minecraft:stone",1);f.rows.set(0,entry(STORED,false,15));JsonObject current=data(s.read("rs.operation",obj("operation_token",token)));check(bool(current,"cursor_changed",false)&&!bool(current,"server_success_confirmed",true)&&current.getAsJsonObject("target_current").get("quantity").getAsInt()==15,"observable delta no causal success claim");t.set(120001);errorIs(s.read("rs.operation",obj("operation_token",token)),"rs_operation_expired_or_unknown");}
 private static void race(){Fake f=new Fake();f.changeOnSecond=true;RsService s=new RsService(f);errorIs(s.act("rs.extract",extract()),"rs_cursor_must_be_empty");check(f.sends==0,"recheck blocks send");}
 private static void inactive(){Fake f=new Fake();RsService s=new RsService(f);f.active=false;errorIs(s.act("rs.extract",extract()),"rs_grid_inactive");f.active=true;f.type="FLUID";errorIs(s.act("rs.extract",extract()),"rs_item_grid_required");check(f.sends==0,"wrong context no sends");}
 private static void identity(){check(JsonIdentity.digest(obj("b",2,"a",1)).equals(JsonIdentity.digest(obj("a",1,"b",2))),"key order invariant");check(!JsonIdentity.digest(obj("a",1)).equals(JsonIdentity.digest(obj("a",2))),"values affect identity");check(JsonIdentity.sha("null").equals("74234e98afe7498fb5daf1f36ac2d78acc339464f950703b8c019892f982b90b"),"known SHA-256");}
}
