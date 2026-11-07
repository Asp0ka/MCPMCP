package dev.mcpmcp.core;
import com.google.gson.*;
public final class ContextView {
 public static JsonObject create(String mode,String transport,boolean connected,boolean integrated,Integer permission,JsonObject snapshot){
  JsonObject server=snapshot==null?Jsons.obj("cheats_enabled","unknown","source",connected?(integrated?"integrated_server_pending":"not_synchronized_by_remote_server"):"not_connected"):snapshot.deepCopy();
  server.addProperty("kind",!connected?"none":integrated?"integrated":"remote");
  JsonObject player=Jsons.obj("permission_level",permission==null?(Object)"unknown":permission,"can_use_level_2_commands",permission==null?(Object)"unknown":permission>=2,"source",permission==null?"not_connected":"client_synchronized_permission");
  return Jsons.obj("mode",mode,"transport",transport,"connected",connected,"native_chat_allowed",connected&&Policy.allowed(mode,transport,"input","normal"),"input_policy","native_client_submission_no_prefix_classification","server",server,"player",player);
 }
 private ContextView(){}
}
