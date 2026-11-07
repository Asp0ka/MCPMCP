package dev.mcpmcp.api;
import com.google.gson.JsonObject;
public interface PluginContext {
 @FunctionalInterface interface Handler {JsonObject call(JsonObject args)throws Exception;}
 void action(String name,JsonObject schema,String transport,String permission,Handler handler);
 void observer(String kind,int priority,Handler handler);
 JsonObject execute(String action,JsonObject args);
 JsonObject observe(String kind,JsonObject args);
}
