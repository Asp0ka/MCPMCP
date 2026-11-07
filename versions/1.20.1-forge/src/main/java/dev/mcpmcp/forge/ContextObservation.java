package dev.mcpmcp.forge;
import com.google.gson.*;
import dev.mcpmcp.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
/** Asynchronous server read; never infer remote settings from OP. */
final class ContextObservation {
 private static IntegratedServer owner;private static Object world;private static UUID playerId;
 private static CompletableFuture<JsonObject> pending;private static JsonObject cached;private static long requestedAt;
 static JsonObject read(){
  Minecraft mc=Minecraft.getInstance();boolean connected=mc.getConnection()!=null&&mc.player!=null&&mc.level!=null;
  IntegratedServer server=connected?mc.getSingleplayerServer():null;UUID id=connected?mc.player.getUUID():null;long now=System.currentTimeMillis();
  if(owner!=server||world!=mc.level||!java.util.Objects.equals(playerId,id)){owner=server;world=mc.level;playerId=id;pending=null;cached=null;requestedAt=0;}
  if(pending!=null&&pending.isDone()){try{cached=pending.join();}catch(RuntimeException failure){cached=Jsons.obj("cheats_enabled","unknown","source","integrated_server_query_failed");}pending=null;}
  if(server!=null&&pending==null&&now-requestedAt>=1000){
   requestedAt=now;pending=server.submit(()->{
    boolean worldCheats=server.getWorldData().getAllowCommands();boolean lanCheats=server.isPublished()&&server.getPlayerList().isAllowCheatsForAllPlayers();
    return Jsons.obj("cheats_enabled",worldCheats||lanCheats,"world_commands_allowed",worldCheats,"lan_published",server.isPublished(),"lan_commands_allowed",lanCheats,"source","integrated_server_thread","observed_at_ms",System.currentTimeMillis());
   });
  }
  JsonObject data=ContextView.create(Runtime.config().mode,Runtime.config().transport,connected,server!=null,connected?mc.player.getPermissionLevel():null,cached);
  data.getAsJsonObject("server").addProperty("refresh_pending",pending!=null);
  if(server!=null&&cached==null)data.getAsJsonObject("server").addProperty("next","repeat get_context to read the pending server-thread snapshot");
  return Jsons.obj("data",data,"source","local_policy_and_available_server_context","complete",server==null||cached!=null,"truncated",false,"observed_at_ms",now);
 }
 private ContextObservation(){}
}
