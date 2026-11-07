package dev.mcpmcp.magic;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import java.lang.reflect.*;

/** One synchronous core interaction; no child job, held input, menu pickup or custom packet. */
final class SafeInteraction {
 static JsonObject run(String mod,JsonObject a,boolean packet)throws Exception {
  Minecraft mc=Minecraft.getInstance();
  if(mc.level==null||mc.player==null||mc.gameMode==null)return J.obj("error","not_in_world");
  if(mc.screen!=null||mc.player.containerMenu!=mc.player.inventoryMenu)return J.obj("error","close_existing_screen_first");
  if(!mc.player.containerMenu.getCarried().isEmpty())return J.obj("error","cursor_not_empty");
  BlockPos p=new BlockPos(J.num(a,"x",0),J.num(a,"y",0),J.num(a,"z",0));
  if(mc.level.isOutsideBuildHeight(p)||!mc.level.hasChunkAt(p))return J.obj("error","target_unloaded_or_outside_world");
  var block=BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(p).getBlock());
  if(!block.getNamespace().equals(mod)||!block.toString().equals(J.str(a,"expected_block","")))return J.obj("error","target_block_changed_or_wrong_mod");
  var hand=J.str(a,"hand","main").equals("off")?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND;
  if(!BuiltInRegistries.ITEM.getKey(mc.player.getItemInHand(hand).getItem()).toString().equals(J.str(a,"expected_item","")))return J.obj("error","held_item_changed");
  JsonObject core=a.deepCopy();core.remove("expected_item");
  // Public 0.1.2 method audited in evidence/core-javap.txt. PluginManager checks
  // the action's fixed packet/client transport and normal permission before entry.
  // Core reuses its reach, shape/raycast, current mode, border and identity checks.
  try {
   Method one=Class.forName("dev.mcpmcp.forge.Actions").getMethod("one",String.class,JsonObject.class,boolean.class);
   JsonObject result=(JsonObject)one.invoke(null,"block.interact",core,packet);
   result.addProperty("effect_confirmation","submitted_only; read target, held stack and menu independently");
   result.addProperty("menu_effect","normal right click may intentionally open a menu; no pre-existing screen is replaced");return result;
  }catch(InvocationTargetException ex){Throwable cause=ex.getCause();return J.obj("error","core_interaction_rejected","reason",cause==null?ex.toString():cause.toString());}
 }
}
