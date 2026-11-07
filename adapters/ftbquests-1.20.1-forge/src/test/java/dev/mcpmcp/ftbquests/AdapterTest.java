package dev.mcpmcp.ftbquests;
import com.google.gson.*;
import dev.mcpmcp.api.*;
import dev.mcpmcp.core.*;
import java.nio.file.*;
import java.util.*;
import static dev.mcpmcp.ftbquests.Json.*;
public final class AdapterTest {
 static int checks;
 static void check(boolean condition,String label){checks++;if(!condition)throw new AssertionError(label);}
 static void rejects(Runnable fn,String expected){try{fn.run();throw new AssertionError("expected "+expected);}catch(IllegalArgumentException|IllegalStateException e){check(e.getMessage()!=null&&e.getMessage().contains(expected),"reject: "+expected+" got "+e);}}
 public static void main(String[] args)throws Exception {
  Map<String,PluginContext.Handler> reads=new HashMap<>(),actions=new HashMap<>();ServiceTest.Host host=new ServiceTest.Host();
  new FtbQuestsProvider(new FtbService(host)).register(new PluginContext(){
   public void observer(String n,int priority,Handler h){reads.put(n,h);}
   public void action(String n,JsonObject schema,String transport,String permission,Handler h){PluginValidation.schema(schema);check(transport.equals("packet")&&permission.equals("normal"),"normal packet contract");check(Policy.allowed("NORMAL","PACKET",transport,permission)&&!Policy.allowed("NORMAL","SIMULATE",transport,permission),"mode transport boundary");actions.put(n,h);}
   public JsonObject execute(String n,JsonObject a){throw new AssertionError("core forwarding not allowed");}
   public JsonObject observe(String n,JsonObject a){throw new AssertionError("core forwarding not allowed");}
  });
  check(reads.keySet().equals(Set.copyOf(Contract.READS)),"all observers registered");check(actions.keySet().equals(Set.copyOf(Contract.ACTIONS)),"all actions registered");
  JsonObject discovery=reads.get("ftbquests").call(new JsonObject()).getAsJsonObject("data");check(discovery.getAsJsonObject("contracts").size()==12,"full discoverable contracts");check(host.captures==0,"discovery no game access");
  var spi=ServiceLoader.load(McpmcpPlugin.class).stream().filter(p->p.type().getName().equals("dev.mcpmcp.ftbquests.FtbQuestsProvider")).toList();check(spi.size()==1&&spi.get(0).get().id().equals("mcpmcp.ftbquests"),"actual ServiceLoader finds one lazy provider");
  Files.writeString(Path.of(System.getProperty("adapter.projectDir"),"evidence","contracts.json"),new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(discovery));
  for(String n:Contract.READS)PluginValidation.schema(Contract.schema(n));
  PluginValidation.manifest(JsonParser.parseString(Files.readString(Path.of(System.getProperty("adapter.projectDir"),"plugin.json"))).getAsJsonObject());
  JsonObject a=ServiceTest.args(host.frame,false);check(str(actions.get("ftbquests.reward.claim").call(a).getAsJsonObject("data"),"status","").equals("dry_run")&&host.sends==0,"public dry_run defaults true");
  JsonObject invalid=a.deepCopy();invalid.addProperty("reward_id",9007199254740993L);rejects(()->{try{actions.get("ftbquests.reward.claim").call(invalid);}catch(RuntimeException e){throw e;}catch(Exception e){throw new RuntimeException(e);}},"schema_validation_failed");
  check(reads.get("ftbquests.quests").call(obj("limit",0)).getAsJsonObject("data").has("error"),"observer business error preserved");
  for(String s:List.of("0000000000000000","ffffffffffffffff","8000000000000000","0020000000000001"))check(id(parseId(s)).equals(s),"unsigned 64 bit exact");
  for(String s:List.of("1","FFFFFFFFFFFFFFFF","0x123456789abcde"," 000000000000000"))rejects(()->parseId(s),"canonical_hex");
  rejects(()->Contract.validate("ftbquests.rewards.claim_all",obj("chapter_id","0000000000000001")),"missing_session");
  JsonObject txt=text("a".repeat(10)+"😀",11);check(bool(txt,"truncated",false)&&str(txt,"text","").length()==10,"text clipping flags and surrogate integrity");

  ServiceTest.Host routed=new ServiceTest.Host();FtbService svc=new FtbService(routed);Jobs jobs=new Jobs();JsonObject dry=ServiceTest.args(routed.frame,false);String dryId=str(dry,"request_id","");
  jobs.submit(dryId,"ftbquests.reward.claim",dry,()->svc.act("ftbquests.reward.claim",dry),9,30000);jobs.pump(9);check(routed.sends==0,"actual core Jobs dry-run no packet");
  JsonObject badSubmit=dry.deepCopy();badSubmit.addProperty("dry_run",false);check(jobs.submit(dryId,"ftbquests.reward.claim",badSubmit,()->svc.act("ftbquests.reward.claim",badSubmit),9,30000).has("error"),"core same action_id different dry_run conflicts");
  JsonObject submit=badSubmit.deepCopy();String submitId=UUID.randomUUID().toString();submit.addProperty("request_id",submitId);jobs.submit(submitId,"ftbquests.reward.claim",submit,()->svc.act("ftbquests.reward.claim",submit),9,30000);jobs.pump(9);check(routed.sends==1&&str(jobs.result(submitId),"status","").equals("submitted"),"new real-submit operation executes once through core Jobs");
  jobs.submit(submitId,"ftbquests.reward.claim",submit,()->svc.act("ftbquests.reward.claim",submit),9,30000);jobs.pump(9);check(routed.sends==1,"same core action_id and adapter request_id dedupe");
  ServiceTest.run();SessionTest.run();NativeTest.run();
  System.out.println("AdapterTest: "+checks+" checks passed (contracts/service/session/pins/real NBT/target packet encoders/native visibility); no game or server run.");
 }
}
