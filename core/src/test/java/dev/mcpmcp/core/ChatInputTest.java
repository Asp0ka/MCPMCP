package dev.mcpmcp.core;
import com.google.gson.*;
public final class ChatInputTest {
 private static int checks;
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
 private static void rejects(String text){try{ChatInput.validate(text);throw new AssertionError("accepted invalid input");}catch(IllegalArgumentException expected){checks++;}}
 public static void main(String[] args){
  for(String text:new String[]{"hello","/time query daytime","#mine 16 minecraft:iron_ore",".help","!custom foo","  #help  ","你好 世界"})check(text.equals(ChatInput.validate(text)),"text preserved");
  check(ChatInput.validate("x".repeat(256)).length()==256,"vanilla length");
  rejects("");rejects("   ");rejects(null);rejects("x".repeat(257));
  rejects("a"+((char)10)+"b");rejects("a"+((char)13)+"b");rejects("a"+((char)9)+"b");rejects("a"+((char)127)+"b");
  JsonObject offline=ContextView.create("NORMAL","MIXED",false,false,null,null);
  check(offline.getAsJsonObject("server").get("cheats_enabled").getAsString().equals("unknown"),"offline unknown");
  JsonObject remote=ContextView.create("HACK","MIXED",true,false,4,null);
  check(remote.getAsJsonObject("server").get("cheats_enabled").getAsString().equals("unknown"),"remote OP not cheat setting");
  check(remote.getAsJsonObject("player").get("permission_level").getAsInt()==4,"permission separate");
  check(remote.get("native_chat_allowed").getAsBoolean(),"native chat normal policy");
  JsonObject local=ContextView.create("NORMAL","MIXED",true,true,0,Jsons.obj("cheats_enabled",false,"source","integrated_server_thread"));
  check(!local.getAsJsonObject("server").get("cheats_enabled").getAsBoolean(),"local false known");
  check(local.get("mode").getAsString().equals("NORMAL"),"mode");check(local.get("transport").getAsString().equals("MIXED"),"transport");
  JsonObject packet=ContextView.create("CHEAT","PACKET",true,true,2,Jsons.obj("cheats_enabled",true));
  check(!packet.get("native_chat_allowed").getAsBoolean(),"PACKET does not fake native input");
  check(packet.getAsJsonObject("server").get("cheats_enabled").getAsBoolean(),"cheats not inferred from mode");
  System.out.println("ChatInputTest: "+checks+" checks passed");
 }
}
