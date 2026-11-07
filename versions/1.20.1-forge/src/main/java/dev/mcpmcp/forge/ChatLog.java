package dev.mcpmcp.forge;

import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import dev.mcpmcp.core.DisplayedMessages;
import java.lang.reflect.Field;
import java.util.*;
import static dev.mcpmcp.core.Jsons.*;

/** Displayed incoming/client-mod messages only; never inspects outgoing input history. */
@Mod.EventBusSubscriber(modid="mcpmcp",value=Dist.CLIENT)
public final class ChatLog {
 private static final ChatHistory HISTORY=new ChatHistory(512,4096);
 private static final DisplayedMessages DISPLAYED=new DisplayedMessages(100);
 private static Field messages;private static boolean initialized;private static String captureError;
 public static void init(){if(initialized)return;initialized=true;try{messages=ObfuscationReflectionHelper.findField(ChatComponent.class,"f_93760_");}catch(RuntimeException e){captureError="displayed_chat_unavailable";}}
 @SubscribeEvent public static void received(ClientChatReceivedEvent event){
  init();boolean overlay=event instanceof ClientChatReceivedEvent.System s&&s.isOverlay();
  if(overlay||messages==null)record(event.getMessage(),overlay?"overlay":event.isSystem()?"system":"chat");
 }
 public static void captureDisplayed(){
  init();Minecraft mc=Minecraft.getInstance();if(messages==null||mc.gui==null)return;
  try{
   Object value=messages.get(mc.gui.getChat());if(!(value instanceof List<?> list))throw new IllegalStateException();
   for(Object entry:DISPLAYED.take(list))if(entry instanceof GuiMessage message)record(message.content(),"displayed");
  }catch(IllegalAccessException|RuntimeException e){messages=null;captureError="displayed_chat_unavailable";}
 }
 public static void record(Component message,String kind){
  if(message!=null)HISTORY.add(message.getString(),ObservJson.clipped(kind==null?"chat":kind,32),System.currentTimeMillis());
 }
 public static JsonObject read(JsonObject args){
  captureDisplayed();long after=Observations.offset(args,"after",0);
  JsonObject page=HISTORY.read(after,ObservBounds.pageSize(integer(args,"limit",100),Runtime.config().scanBudget));
  page.addProperty("capture",messages==null?"forge_events_only":"native_displayed_chat_and_overlays");page.addProperty("persistent",false);
  if(captureError!=null)page.addProperty("capture_error",captureError);
  page.addProperty("native_display_window",100);
  boolean more=page.get("more").getAsBoolean(),gap=after<page.get("dropped_before").getAsLong();page.addProperty("history_gap",gap);
  return ObservJson.envelope(page,"client_displayed_messages","bounded_displayed_history",!more&&!gap&&captureError==null,more||gap);
 }
 private ChatLog(){}
}
