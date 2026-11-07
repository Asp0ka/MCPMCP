package dev.mcpmcp.ftbquests;

import com.google.gson.*;
import dev.mcpmcp.core.Schema;
import java.util.*;
import static dev.mcpmcp.ftbquests.Json.*;

public final class Contract {
 public static final List<String> READS=List.of("ftbquests","ftbquests.book","ftbquests.chapters","ftbquests.quests","ftbquests.quest","ftbquests.rewards","ftbquests.claimable","ftbquests.receipt");
 public static final List<String> ACTIONS=List.of("ftbquests.reward.claim","ftbquests.reward.choose","ftbquests.rewards.claim_all","ftbquests.task.check");
 static final String UUID_PATTERN="[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}";
 public static JsonObject schema(String name){
  JsonObject s=obj("type","object","properties",new JsonObject(),"additionalProperties",false,"required",new JsonArray());JsonObject p=s.getAsJsonObject("properties");JsonArray r=s.getAsJsonArray("required");
  if(name.equals("ftbquests")){s.addProperty("description","Exact target, closed schemas, client-only scope and submission workflow. No game reads.");return s;}
  need(READS.contains(name)||ACTIONS.contains(name),"ftbquests_unknown_contract");
  if(ACTIONS.contains(name)){
   required(r,"session","request_id");uuid(p,"session","Fresh observation token, 30 second TTL; bound to world/player/connection/file/selfTeamData and full content/progress snapshot.");uuid(p,"request_id","One UUID per operation; match core action_id. Dry-run is a separate operation UUID from the real submit because core action IDs bind the full payload. Retained receipts never auto-expire/re-send; ambiguous delivery blocks new mutations until claimed read-back, including after context change.");field(p,"dry_run","boolean","Default true. false explicitly submits exactly once.");
   if(name.equals("ftbquests.task.check")){
    required(r,"task_id","expected_task_hash","acknowledge_read");hex(p,"task_id","Exact visible, startable checkmark task. No resource tasks or progress setters.");digest(p,"expected_task_hash","Exact visible task definition hash.");field(p,"acknowledge_read","boolean","Must explicitly be true: caller has read this informational task. Does not authorize task editing or resource submission.");
   }else if(name.equals("ftbquests.rewards.claim_all")){
    required(r,"preview_token","scope","expected_claimable_hash","expected_claimable_count","max_rewards");uuid(p,"preview_token","Issued only by the final page of a complete unfiltered ftbquests.claimable preview; session/hash/count bound, 30s expiry.");field(p,"scope","string","Native global scope. No chapter/type filter exists.").add("enum",GSON.toJsonTree(List.of("all_not_excluded")));digest(p,"expected_claimable_hash","Exact claimable_hash from completed preview, including exclusions, definitions and effects.");number(p,"expected_claimable_count",1,512,"Exact eligible, unclaimed, unblocked native candidate count.");number(p,"max_rewards",1,512,"Explicit count budget; native packet has no server-side budget/filter. Races remain possible.");s.addProperty("description","One native ClaimAllRewardsMessage(), never a per-reward loop. Fails closed if native scope includes hidden, unknown or unsafe-choice candidates. Built-in ChoiceReward is excluded by native getExcludeFromClaimAll(). Preconfigured command/custom/table effects remain native.");
   }else{
    required(r,"reward_id","expected_reward_hash");hex(p,"reward_id","Reward ID from visible quest rewards, exactly 16 lower-case hexadecimal digits; JSON number IDs forbidden.");digest(p,"expected_reward_hash","Same snapshot reward_hash; binds definition, eligibility and choice/table content.");
    if(name.endsWith("choose")){required(r,"choice_index","expected_table_hash");number(p,"choice_index",0,127,"Explicit zero-based ordered table index from choice_candidates, never defaulted.");digest(p,"expected_table_hash","Whole recursive table identity, all candidates/weights/definitions; parent must be completed and unclaimed.");s.addProperty("description","One ClaimChoiceRewardMessage. Native server calls child.claim before marking parent, so adapter requires parent unclaimed, unlocked/unblocked self-team and a fully reviewed bounded table. No automatic first choice.");}
    else s.addProperty("description","One native ClaimRewardMessage(id,true) for audited ordinary reward routes. Choice requires reward.choose. No setters or direct Reward.claim calls.");
   }
  }else if(name.equals("ftbquests.receipt")){required(r,"request_id");uuid(p,"request_id","Exact operation UUID. Reads current synced claimed state when available and returns retained receipt even when disconnected; never resubmits.");}
  else if(name.equals("ftbquests.quest")){required(r,"quest_id");hex(p,"quest_id","Exact visible quest ID. Hidden content and hidden-until-startable details are not bypassed.");}
  else if(!name.equals("ftbquests.book")){
   number(p,"limit",1,32,"Default 8. Results bounded by limit and 1 MiB row JSON budget; cursor binds identical filters/limit/session/version.");uuid(p,"cursor","Opaque continuation token, expires in 30s. Keep query fields unchanged. Drift is stale_cursor, never an empty complete page.");
   if(!name.equals("ftbquests.claimable")){
    textField(p,"query",0,256,"Case-insensitive search in visible returned title/subtitle/description/task/reward preview text.");
    if(name.equals("ftbquests.chapters"))hex(p,"group_id","Optional exact visible group.");
    else {hex(p,"chapter_id","Optional exact visible chapter.");field(p,"claimable","boolean","Filter current client-synced effective eligibility; excludes locked/blocked/already claimed.");}
    if(name.equals("ftbquests.quests"))field(p,"status","string","Quest progression status.").add("enum",GSON.toJsonTree(List.of("completed","in_progress","available","locked")));
    if(name.equals("ftbquests.rewards")){hex(p,"quest_id","Optional visible quest.");field(p,"claimed","boolean","Filter claimed state.");field(p,"exclude_from_claim_all","boolean","Filter native getExcludeFromClaimAll, including choice exclusions.");}
   }
   s.addProperty("description",name.equals("ftbquests.claimable")?"Unfiltered native all_not_excluded preview with all completed unclaimed visible reward rows, exclusions and side-effect classification. Walk every page for preview_token.":"Client-synced visible data only, no GUI or server/other-team reads. Each task list includes content/progress/dependencies/reward preview; hidden fields are null with reason.");
  }else s.addProperty("description","Visible-only overall/group/chapter statistics, session, version and pending receipt. Never open GUI.");
  return s;
 }
 public static void validate(String name,JsonObject args){Schema.validate(args,schema(name));for(var e:args.entrySet())if(e.getKey().endsWith("_id")&&!e.getKey().equals("request_id"))parseId(e.getValue().getAsString());}
 public static JsonObject discovery(){JsonObject contracts=new JsonObject(),actions=new JsonObject();for(String n:READS)contracts.add(n,schema(n));for(String n:ACTIONS){contracts.add(n,schema(n));actions.add(n,obj("transport","packet","permission","normal"));}return obj("data",obj("source","ftbquests","adapter_version","0.1.0","contracts",contracts,"actions",actions,"observers",READS,"runtime_pins",RuntimePin.declarations(),"scope","ClientQuestFile.INSTANCE/selfTeamData/current player; native non-editor visibility","dry_run_default",true,"limits",obj("session_ttl_ms",30000,"page_max",32,"quests_max",16384,"reward_rows_max",65536,"receipts_max",512,"preview_budget_max",512,"choice_candidates_max",128),"limitations",List.of("Client sync can lag server; submitted and claimed read-back do not prove inventory delivery or command effects.","Native claim_all has global scope and no expected-hash/count fields on the wire. Client preflight cannot eliminate server races.","Configured command/custom/advancement/stage/table rewards may change server state. Native effects are neither edited nor elevated by this plugin.","Only explicit acknowledged checkmark confirmation; no resource task submission, editor, reset, give/creative, arbitrary commands, server files or other-team data.","No GUI operation or command-link click. Unknown/unsynced data is explicit and cannot authorize a mutation.","Complete every preview page. New UUID is not a retry strategy for unknown delivery. Receipts are in-memory for this provider lifetime.")));}
 private static void required(JsonArray r,String... keys){for(String k:keys)r.add(k);}
 private static JsonObject field(JsonObject p,String k,String type,String desc){JsonObject v=obj("type",type,"description",desc);p.add(k,v);return v;}
 private static JsonObject textField(JsonObject p,String k,int min,int max,String desc){JsonObject v=field(p,k,"string",desc);v.addProperty("minLength",min);v.addProperty("maxLength",max);return v;}
 private static void uuid(JsonObject p,String k,String d){textField(p,k,36,36,d).addProperty("pattern",UUID_PATTERN);}
 private static void hex(JsonObject p,String k,String d){textField(p,k,16,16,d).addProperty("pattern","[0-9a-f]{16}");}
 private static void digest(JsonObject p,String k,String d){textField(p,k,64,64,d).addProperty("pattern","[0-9a-f]{64}");}
 private static void number(JsonObject p,String k,int min,int max,String d){JsonObject v=field(p,k,"integer",d);v.addProperty("minimum",min);v.addProperty("maximum",max);}
 private Contract(){}
}
