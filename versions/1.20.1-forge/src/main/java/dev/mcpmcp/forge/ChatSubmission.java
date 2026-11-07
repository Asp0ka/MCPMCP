package dev.mcpmcp.forge;
import com.google.gson.JsonObject;
import dev.mcpmcp.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
/** Same public submit handler as Enter in the real ChatScreen. */
final class ChatSubmission {
 static JsonObject submit(JsonObject args){
  String text=ChatInput.validate(Jsons.str(args,"text",""));
  Minecraft mc=Minecraft.getInstance();
  if(mc.player==null||mc.level==null||mc.getConnection()==null)return Jsons.error("not_in_world");
  if(mc.screen!=null)return Jsons.error("close_existing_screen_first");
  ChatScreen chat=new ChatScreen(text);mc.setScreen(chat);
  if(mc.screen!=chat)return Jsons.error("chat_screen_replaced_before_submission");
  try{
   boolean handled=chat.handleChatInput(text,true);
   return Jsons.obj("delivery","submitted","pipeline","native_chat_screen","native_handled",handled,"input_length",text.length(),"client_normalization","vanilla_chat_rules","effect_confirmed",false,"verify","read chat and resulting state; client mods may intercept; long-running client commands are not this job");
  }finally{if(mc.screen==chat)mc.setScreen(null);}
 }
 private ChatSubmission(){}
}
