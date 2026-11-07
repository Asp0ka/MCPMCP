package dev.mcpmcp.curios;
import com.google.gson.*;
import dev.mcpmcp.api.*;
import dev.mcpmcp.core.*;
import java.util.*;
public final class AdapterTest {
 static int checks;
 static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
 public static void main(String[] args)throws Exception {
  Map<String,PluginContext.Handler> reads=new HashMap<>(),actions=new HashMap<>();
  ServiceTest.Host host=new ServiceTest.Host();
  new CuriosProvider(new CuriosService(host)).register(new PluginContext(){
   public void observer(String n,int p,Handler h){reads.put(n,h);}
   public void action(String n,JsonObject s,String t,String p,Handler h){PluginValidation.schema(s);check(t.equals("packet")&&p.equals("normal"),"normal packet only");check(!Policy.allowed("NORMAL","SIMULATE",t,p),"simulate denied");actions.put(n,h);}
   public JsonObject execute(String n,JsonObject a){throw new AssertionError("core action not expected");}
   public JsonObject observe(String n,JsonObject a){throw new AssertionError("core read not expected");}
  });
  check(reads.keySet().equals(Set.of("curios","curios.slots","curios.menu","curios.candidates")),"public structured observer registration");
  check(actions.keySet().equals(Set.of("curios.open","curios.close","curios.swap","curios.page","curios.cosmetics.set","curios.render.set")),"controlled action registration");
  check(reads.get("curios").call(new JsonObject()).getAsJsonObject("data").has("contracts"),"discoverable closed schemas");
  for(String n:Contract.READS){PluginValidation.schema(Contract.schema(n));}
  PluginValidation.manifest(JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of(System.getProperty("adapter.projectDir"),"plugin.json"))).getAsJsonObject());
  JsonObject swapped=actions.get("curios.swap").call(ServiceTest.args());check(swapped.getAsJsonObject("data").get("status").getAsString().equals("dry_run")&&host.sends==0,"public handler performs dry-run preflight");
  JsonObject invalid=ServiceTest.args();invalid.addProperty("identifier","wrong");try{actions.get("curios.swap").call(invalid);throw new AssertionError("public slot guard");}catch(IllegalArgumentException ex){check(ex.getMessage().equals("curios_slot_identity_changed")&&host.sends==0,"public handler refuses incorrect slot identity");}
  check(reads.get("curios.slots").call(dev.mcpmcp.core.Jsons.obj("limit",0)).getAsJsonObject("data").has("error"),"public observer errors are preserved");
  ServiceTest.run();SessionTest.run();NativeTest.run();GuardTest.run();
  System.out.println("AdapterTest: "+checks+" public contract checks passed");
 }
}
