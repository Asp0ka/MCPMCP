package dev.mcpmcp.ftbquests;
import com.google.gson.JsonObject;
import dev.ftb.mods.ftbquests.net.*;
import dev.architectury.networking.simple.BaseC2SMessage;
import java.util.function.Consumer;
import static dev.mcpmcp.ftbquests.Json.*;
final class NativePackets {
 static BaseC2SMessage build(String action,JsonObject args){return switch(action){
  case "ftbquests.task.check" -> new SubmitTaskMessage(parseId(str(args,"task_id","")));
  case "ftbquests.reward.claim" -> new ClaimRewardMessage(parseId(str(args,"reward_id","")),true);
  case "ftbquests.reward.choose" -> new ClaimChoiceRewardMessage(parseId(str(args,"reward_id","")),integer(args,"choice_index",-1));
  case "ftbquests.rewards.claim_all" -> new ClaimAllRewardsMessage();
  default -> throw new IllegalArgumentException("ftbquests_unknown_action");
 };}
 static void submit(String action,JsonObject args){submit(action,args,BaseC2SMessage::sendToServer);}
 static void submit(String action,JsonObject args,Consumer<BaseC2SMessage> sender){sender.accept(build(action,args));}
 private NativePackets(){}
}
