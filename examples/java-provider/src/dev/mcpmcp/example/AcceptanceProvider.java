package dev.mcpmcp.example;
import com.google.gson.*;import dev.mcpmcp.api.*;
public final class AcceptanceProvider implements McpmcpPlugin {
 public String id(){return "example.provider";}
 public void register(PluginContext context){context.action("example.provider_probe",JsonParser.parseString("{\"type\":\"object\",\"additionalProperties\":false}").getAsJsonObject(),"client","normal",a->{JsonObject r=new JsonObject();r.addProperty("observed_success",true);r.addProperty("provider","cold_java_fixture");return r;});context.observer("example.provider",10,a->{JsonObject r=new JsonObject();r.addProperty("source","cold_java_fixture");r.addProperty("registered",true);return r;});}
}
