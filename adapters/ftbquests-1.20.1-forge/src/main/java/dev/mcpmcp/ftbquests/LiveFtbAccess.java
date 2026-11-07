package dev.mcpmcp.ftbquests;

import com.google.gson.*;
import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import dev.ftb.mods.ftbquests.quest.*;
import dev.ftb.mods.ftbquests.quest.task.Task;
import dev.ftb.mods.ftbquests.quest.reward.*;
import dev.ftb.mods.ftbquests.quest.loot.*;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import java.util.*;
import static dev.mcpmcp.ftbquests.Json.*;

/** Never creates team data, accesses server quest files, opens GUI or changes client progress. */
final class LiveFtbAccess implements FtbAccess {
 private final RuntimePin pin=new RuntimePin();
 private final SessionIdentity identity=new SessionIdentity();
 private static final Set<String> ORDINARY=Set.of("ItemReward","XPReward","XPLevelsReward","RandomReward","LootReward","AllTableReward","CustomReward","CommandReward","AdvancementReward","StageReward","ToastReward");
 private ClientQuestFile file;private TeamData team;private UUID player;
 public JsonObject capture(){
  pin.verify();
  Minecraft mc=Minecraft.getInstance();
  if(mc.level==null||mc.player==null||mc.getConnection()==null){identity.clear();throw new IllegalStateException("ftbquests_not_in_world");}
  file=ClientQuestFile.INSTANCE;
  if(file==null||file.isLoading()||file.selfTeamData==null){identity.clear();throw new IllegalStateException("ftbquests_server_not_synced");}
  team=file.selfTeamData;player=mc.player.getUUID();
  if(team.getFile()!=file||!file.isPlayerOnTeam(mc.player,team)){identity.clear();throw new IllegalStateException("ftbquests_self_team_not_synced");}
  JsonObject frame=project(file,team,player);
  String version=hash(frame);String session=identity.update(new Object[]{mc.level,mc.player,mc.getConnection(),file,team},player+"/"+team.getTeamId(),version);
  frame.addProperty("version",version);frame.addProperty("session",session);frame.addProperty("context",identity.context());frame.addProperty("observed_at_ms",System.currentTimeMillis());return frame;
 }
 JsonObject project(ClientQuestFile file,TeamData team,UUID player){
  this.file=file;this.team=team;this.player=player;need(team.getFile()==file,"ftbquests_self_team_not_synced");
  JsonArray quests=new JsonArray(),chapters=new JsonArray(),groups=new JsonArray(),rewards=new JsonArray(),preview=new JsonArray();
  int questCount=0,rewardCount=0;boolean nativeSafe=true;LinkedHashSet<String> blockers=new LinkedHashSet<>();
  for(Chapter c:file.getAllChapters()){
   boolean chapterVisible=visible(c,team);
   if(chapterVisible)chapters.add(obj("id",id(c.id),"group_id",id(c.getGroup().id),"title",text(c.getTitle().getString(),1024),"subtitle",text(String.join("\n",c.getRawSubtitle()),8192),"visible",true));
   for(Quest q:c.getQuests()){
    need(++questCount<=16384,"ftbquests_quest_budget");
    boolean visible=chapterVisible&&visible(q,team);
    boolean completed=team.isCompleted(q);
    boolean details=visible&&detailsVisible(q,team);
    if(visible){if(details)for(Reward r:q.getRewards())validateGraph(r,0,Collections.newSetFromMap(new IdentityHashMap<>()),new int[]{512});quests.add(quest(q,details));}
    for(Reward r:q.getRewards()){
     need(++rewardCount<=65536,"ftbquests_reward_budget");
     boolean claimed=team.isRewardClaimed(player,r);
     boolean nativeEligible=completed&&!claimed&&!team.isLocked()&&!team.isRewardBlocked(r)&&team.getClaimType(player,r).canClaim();
     if(!details||!rewardVisible(r,team)){
      if(nativeEligible&&!r.getExcludeFromClaimAll()){nativeSafe=false;blockers.add("native_scope_contains_non_visible_reward");}
      continue;
     }
     JsonObject row=reward(r,true,0,Collections.newSetFromMap(new IdentityHashMap<>()));
     row.addProperty("chapter_id",id(c.id));row.addProperty("quest_id",id(q.id));row.add("quest_title",text(q.getTitle().getString(),1024));
     row.addProperty("reward_hash",hash(row));rewards.add(row);
     if(completed&&!claimed){
      preview.add(row.deepCopy());
      if(nativeEligible&&!r.getExcludeFromClaimAll()&&!bool(row,"ordinary_route_safe",false)){nativeSafe=false;blockers.add("native_scope_contains_unsafe_or_unknown_route");}
     }
    }
   }
  }
  need(chapters.size()<=4096,"ftbquests_chapter_budget");
  for(ChapterGroup g:file.getChapterGroups())if(g.isValid()&&g.isVisible(team))groups.add(obj("id",id(g.id),"title",text(g.getTitle().getString(),1024),"default",g.isDefaultGroup()));
  ChapterGroup def=file.getDefaultChapterGroup();
  boolean found=false;for(JsonElement e:groups)found|=str(e.getAsJsonObject(),"id","").equals(id(def.id));
  if(!found&&def.isVisible(team))groups.add(obj("id",id(def.id),"title",text(def.getTitle().getString(),1024),"default",true));
  need(groups.size()<=1024,"ftbquests_group_budget");
  JsonObject frame=obj("source","ftbquests","available",true,"scope","client_synced_self_team","player_uuid",player.toString(),"team_uuid",team.getTeamId().toString(),"title",text(file.getTitle().getString(),1024),"quests",quests,"chapters",chapters,"groups",groups,"rewards",rewards,"preview",preview,"native_scope_safe",nativeSafe,"native_scope_blockers",blockers,"team_locked",team.isLocked(),"server_state","client_synced_may_lag");
  return frame;
 }
 static boolean visible(Chapter c,TeamData team){return c.isValid()&&c.isVisible(team)&&c.getGroup().isVisible(team); }
 static boolean visible(Quest q,TeamData team){return q.isValid()&&visible(q.getChapter(),team)&&q.isVisible(team); }
 static boolean rewardVisible(Reward r,TeamData team){return !team.isRewardBlocked(r)&&r.getAutoClaimType()!=RewardAutoClaim.INVISIBLE;}
 static boolean detailsVisible(Quest q,TeamData team){return !q.hideDetailsUntilStartable()||team.canStartTasks(q); }
 static boolean textVisible(Quest q,TeamData team){return !q.getHideTextUntilComplete().get(false)||team.isCompleted(q); }
 private JsonObject quest(Quest q,boolean details){
  need(q.getTasks().size()<=128&&q.getRewards().size()<=128,"ftbquests_quest_children_budget");
  boolean complete=team.isCompleted(q),startable=team.canStartTasks(q);
  JsonObject row=obj("id",id(q.id),"chapter_id",id(q.getChapter().id),"title",text(q.getTitle().getString(),1024),"visible",true,"details_visible",details,"completed",complete,"started",team.isStarted(q),"can_start",startable,"status",complete?"completed":team.isStarted(q)?"in_progress":startable?"available":"locked","relative_progress",team.getRelativeProgress(q),"completion_count",team.getCompletionCount(q),"optional",q.isOptional(),"repeatable",q.canBeRepeated());
  row.addProperty("checkmark_route_safe",q.getRewards().stream().allMatch(r->r.getAutoClaimType()==RewardAutoClaim.DISABLED));
  if(!details){for(String k:List.of("subtitle","description","tasks","dependencies","rewards"))row.add(k,JsonNull.INSTANCE);row.addProperty("unknown_reason","native_hide_details_until_startable");row.add("claimable",JsonNull.INSTANCE);return row;}
  row.add("subtitle",text(q.getSubtitle().getString(),8192));
  boolean textVisible=textVisible(q,team);row.addProperty("description_visible",textVisible);
  if(textVisible){StringBuilder b=new StringBuilder();int total=0;boolean truncated=false;for(var line:q.getDescription()){String s=line.getString();total+=s.length()+1;if(b.length()<32768){if(b.length()>0)b.append('\n');int end=Math.min(s.length(),32768-b.length());b.append(s,0,end);if(end<s.length())truncated=true;}else truncated=true;}JsonObject text=text(b.toString(),32768);text.addProperty("truncated",truncated||bool(text,"truncated",false));text.addProperty("total_utf16_units",total==0?0:total-1);row.add("description",text);}else{row.add("description",JsonNull.INSTANCE);row.addProperty("description_unknown_reason","native_hide_text_until_complete");}
  JsonArray tasks=new JsonArray();for(Task t:q.getTasks()){boolean hide=t.hideProgressNumbers();CompoundTag data=new CompoundTag();t.writeData(data);tasks.add(obj("id",id(t.id),"type",t.getType().getTypeId().toString(),"title",text(t.getTitle().getString(),1024),"completed",team.isCompleted(t),"progress",hide?null:Long.toString(team.getProgress(t)),"max_progress",hide?null:Long.toString(t.getMaxProgress()),"progress_hidden",hide,"progress_unknown_reason",hide?"native_hide_progress_numbers":null,"consumes_resources",t.consumesResources(),"definition_hash",NbtIdentity.hash(data)));}row.add("tasks",tasks);
  JsonArray deps=new JsonArray();q.streamDependencies().forEach(d->{need(deps.size()<128,"ftbquests_dependencies_budget");boolean v=d.isVisible(team)&&(d.getQuestChapter()==null||d.getQuestChapter().isVisible(team));deps.add(obj("id",id(d.id),"visible",v,"completed",v?team.isCompleted(d):null,"started",v?team.isStarted(d):null,"unknown_reason",v?null:"dependency_not_visible"));});row.add("dependencies",deps);row.addProperty("dependencies_complete",team.areDependenciesComplete(q));row.addProperty("min_required_dependencies",q.getMinRequiredDependencies());
  JsonArray rs=new JsonArray();boolean canClaim=false;for(Reward r:q.getRewards()){if(!rewardVisible(r,team))continue;JsonObject rj=reward(r,true,0,Collections.newSetFromMap(new IdentityHashMap<>()));rj.addProperty("chapter_id",id(q.getChapter().id));rj.addProperty("quest_id",id(q.id));rj.add("quest_title",text(q.getTitle().getString(),1024));rj.addProperty("reward_hash",hash(rj));rs.add(rj);canClaim|=bool(rj,"can_claim",false);}row.add("rewards",rs);row.addProperty("claimable",canClaim);
  CompoundTag definition=new CompoundTag();q.writeData(definition);row.addProperty("definition_hash",NbtIdentity.hash(definition));return row;
 }
 static void validateGraph(Reward reward,int depth,Set<RewardTable> tables,int[] budget){
  need(depth<=8&&--budget[0]>=0,"ftbquests_table_graph_budget");
  if(reward instanceof RandomReward r&&r.getTable()!=null){RewardTable t=r.getTable();need(tables.add(t),"ftbquests_table_cycle");need(t.getWeightedRewards().size()<=128,"ftbquests_choice_candidates_budget");for(WeightedReward w:t.getWeightedRewards())validateGraph(w.getReward(),depth+1,tables,budget);tables.remove(t); }
 }
 private JsonObject reward(Reward r,boolean state,int depth,Set<RewardTable> visited){
  need(depth<=8,"ftbquests_table_depth_budget");
  String cls=r.getClass().getName();boolean builtIn=ORDINARY.stream().anyMatch(n->cls.equals("dev.ftb.mods.ftbquests.quest.reward."+n))||cls.equals(ChoiceReward.class.getName());
  boolean choice=r instanceof ChoiceReward;boolean ordinary=builtIn&&!choice;
  CompoundTag data=new CompoundTag();r.writeData(data);
  JsonObject o=obj("id",id(r.id),"type",r.getType().getTypeId().toString(),"title",text(r.getTitle().getString(),1024),"team_reward",r.isTeamReward(),"auto_claim",r.getAutoClaimType().name(),"exclude_from_claim_all",r.getExcludeFromClaimAll(),"choice",choice,"ordinary_route_safe",ordinary,"choice_route_safe",choice&&builtIn,"definition_hash",NbtIdentity.hash(data),"effects",List.of(effect(r)),"effects_complete",builtIn);
  if(r instanceof ItemReward ir){var stack=ir.getItem().copy();CompoundTag saved=new CompoundTag();stack.save(saved);o.add("item",obj("item_id",net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),"stack_count",stack.getCount(),"configured_count",ir.getCount(),"random_bonus",data.getInt("random_bonus"),"only_one",data.getBoolean("only_one"),"stack_hash",NbtIdentity.hash(saved),"has_nbt",stack.hasTag(),"quantity_scope","configured base; random_bonus/only_one can change actual result"));}
  if(state){boolean claimed=team.isRewardClaimed(player,r),nativeCan=team.getClaimType(player,r).canClaim(),blocked=team.isRewardBlocked(r);o.addProperty("claimed",claimed);o.addProperty("native_can_claim",nativeCan);o.addProperty("reward_blocked",blocked);o.addProperty("quest_completed",team.isCompleted(r.getQuest()));o.addProperty("can_claim",!claimed&&nativeCan&&team.isCompleted(r.getQuest())&&!team.isLocked()&&!blocked);o.addProperty("claim_type",team.getClaimType(player,r).name());}
  if(r instanceof RandomReward random){
   RewardTable table=random.getTable();
   if(table==null){o.addProperty("ordinary_route_safe",false);o.addProperty("choice_route_safe",false);o.addProperty("table_unknown_reason","server_table_not_synced_or_missing");}
   else {
    need(visited.add(table),"ftbquests_table_cycle");need(table.getWeightedRewards().size()<=128,"ftbquests_choice_candidates_budget");
    JsonArray candidates=new JsonArray(),identities=new JsonArray();LinkedHashSet<String> effects=new LinkedHashSet<>();effects.add(effect(r));boolean safe=true;int index=0;
    for(WeightedReward w:table.getWeightedRewards()){JsonObject child=reward(w.getReward(),false,depth+1,visited);child.addProperty("choice_index",index++);child.addProperty("weight",Float.toString(w.getWeight()));identities.add(child);if(choice||table.shouldShowTooltip())candidates.add(child.deepCopy());safe&=bool(child,"ordinary_route_safe",false)&&bool(child,"effects_complete",false);for(JsonElement e:array(child,"effects"))effects.add(e.getAsString());}
    visited.remove(table);CompoundTag td=new CompoundTag();table.writeData(td);o.addProperty("table_id",id(table.id));o.addProperty("table_hash",hash(obj("id",id(table.id),"definition_hash",NbtIdentity.hash(td),"ordered_candidates",identities)));o.addProperty("table_candidates_total",index);o.addProperty("table_candidates_visible",choice||table.shouldShowTooltip());o.add(choice?"choice_candidates":"table_candidates",candidates);o.add("effects",GSON.toJsonTree(effects));o.addProperty("effects_complete",builtIn&&safe);o.addProperty("ordinary_route_safe",ordinary&&safe);o.addProperty("choice_route_safe",choice&&builtIn&&safe&&index>0);
   }
  }
  return o;
 }
 private static String effect(Reward r){return switch(r.getClass().getSimpleName()){case "CommandReward"->"preconfigured_server_command";case "CustomReward"->"server_custom_handler";case "AdvancementReward"->"advancement_grant";case "StageReward"->"stage_change";case "ItemReward"->"item_grant";case "XPReward","XPLevelsReward"->"experience_grant";case "RandomReward","LootReward","AllTableReward"->"configured_table_grants";case "ChoiceReward"->"explicit_choice_only";case "ToastReward"->"notification";default->"unknown_custom_effect";};}
 public void submit(String action,JsonObject args){pin.verify();NativePackets.submit(action,args);}
}
