package dev.mcpmcp.core;
import com.google.gson.*;import java.util.function.BooleanSupplier;
/** Job owns deadline/cancellation; this step sends at most once, then polls. */
public final class PluginStep implements Jobs.Step {
 private final Jobs.Step send;private final BooleanSupplier verify;private boolean started;private JsonObject result,terminal;
 public PluginStep(Jobs.Step send,BooleanSupplier verify){this.send=send;this.verify=verify;}
 public JsonObject tick()throws Exception{if(terminal!=null)return terminal.deepCopy();try{if(!started){started=true;result=send.tick();if(result==null)result=new JsonObject();if(result.has("error")||verify==null){terminal=result;return terminal.deepCopy();}}if(verify.getAsBoolean()){terminal=Jsons.obj("observed_success",true,"delivery","submitted","verification","conditions_met","submission",result);return terminal.deepCopy();}return Jsons.obj("pending",true,"phase","verify","submission",result);}catch(Exception|LinkageError e){terminal=Jsons.obj("error","plugin_action_failed","reason",e.toString());return terminal.deepCopy();}}
}
