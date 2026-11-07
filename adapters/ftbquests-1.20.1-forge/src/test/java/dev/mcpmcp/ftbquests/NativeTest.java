package dev.mcpmcp.ftbquests;
import com.google.gson.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.nbt.*;
import dev.ftb.mods.ftbquests.net.*;
import dev.ftb.mods.ftbquests.quest.*;
import dev.ftb.mods.ftbquests.quest.reward.*;
import dev.ftb.mods.ftblibrary.config.Tristate;
import dev.architectury.utils.Env;
import java.lang.reflect.*;
import java.util.*;
import static dev.mcpmcp.ftbquests.Json.*;
import static dev.mcpmcp.ftbquests.AdapterTest.*;
/** Actual target packet encoders and native visibility. Isolated fixtures; never sends. */
final class NativeTest {
 static void set(Object o,Class<?> type,String name,Object v)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(o,v);}
 static Object get(Object o,Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(o);}
 static final class FileFixture extends BaseQuestFile {
  public Env getSide(){return Env.CLIENT;}
  public void deleteObject(long id){throw new AssertionError();}
  public boolean isPlayerOnTeam(net.minecraft.world.entity.player.Player p,TeamData t){throw new AssertionError();}
 }
 static void run()throws Exception{
  SharedConstants.tryDetectVersion();Field b=Bootstrap.class.getDeclaredField("isBootstrapped");b.setAccessible(true);b.setBoolean(null,true);net.minecraft.core.registries.BuiltInRegistries.bootStrap();
  CompoundTag a=new CompoundTag(),c=new CompoundTag();a.putLong("id",Long.MIN_VALUE);a.putInt("n",1);c.putInt("n",1);c.putLong("id",Long.MIN_VALUE);check(NbtIdentity.hash(a).equals(NbtIdentity.hash(c)),"NBT sorted compound equivalent");c.putLong("n",1);check(!NbtIdentity.hash(a).equals(NbtIdentity.hash(c)),"NBT numeric types distinguished");c=a.copy();c.putLong("id",Long.MIN_VALUE+1);check(!NbtIdentity.hash(a).equals(NbtIdentity.hash(c)),"NBT full unsigned 64-bit tail distinguished");
  ListTag l=new ListTag();l.add(StringTag.valueOf("A"));l.add(StringTag.valueOf("B"));ListTag k=new ListTag();k.add(StringTag.valueOf("B"));k.add(StringTag.valueOf("A"));check(!NbtIdentity.hash(l).equals(NbtIdentity.hash(k)),"choice list order identity");
  a.putString("command","x".repeat(20000)+"A");c=a.copy();c.putString("command","x".repeat(20000)+"B");check(!NbtIdentity.hash(a).equals(NbtIdentity.hash(c)),"definition full untruncated command tail hash");
  JsonObject args=obj("reward_id","fedcba9876543210","choice_index",17);
  for(String n:Contract.ACTIONS){JsonObject packetArgs=args.deepCopy();packetArgs.addProperty("task_id","fedcba9876543210");var packet=NativePackets.build(n,packetArgs);FriendlyByteBuf buf=new FriendlyByteBuf(Unpooled.buffer());try{packet.write(buf);if(n.endsWith("task.check"))check(packet instanceof SubmitTaskMessage&&buf.readLong()==parseId("fedcba9876543210")&&!buf.isReadable(),"native checkmark task exact ID");else if(n.endsWith("claim_all"))check(packet instanceof ClaimAllRewardsMessage&&!buf.isReadable(),"native claimall zero-field global scope");else if(n.endsWith("choose"))check(packet instanceof ClaimChoiceRewardMessage&&buf.readLong()==parseId("fedcba9876543210")&&buf.readVarInt()==17&&!buf.isReadable(),"native choice exact unsigned ID and explicit index");else check(packet instanceof ClaimRewardMessage&&buf.readLong()==parseId("fedcba9876543210")&&buf.readBoolean()&&!buf.isReadable(),"native ordinary exact unsigned ID and notify true");}finally{buf.release();}int[] sends={0};NativePackets.submit(n,packetArgs,p->sends[0]++);check(sends[0]==1,"single native message callback per action");}
  visibility();projection();
 }
 static void projection()throws Exception{
  var f=new dev.ftb.mods.ftbquests.client.ClientQuestFile();UUID player=UUID.randomUUID();TeamData team=new TeamData(UUID.randomUUID(),f);f.selfTeamData=team;
  Chapter ch=new Chapter(100,f,f.getDefaultChapterGroup());ch.setRawTitle("Chapter");f.getDefaultChapterGroup().addChapter(ch);Quest q=new Quest(101,ch);q.setRawTitle("Quest content");q.setRawSubtitle("Subtitle instructions");set(q,Quest.class,"rawDescription",List.of("Do not click links","Mine copper"));ch.addQuest(q);
  var task=new dev.ftb.mods.ftbquests.quest.task.CheckmarkTask(102,q);task.setRawTitle("Checkmark requirement");q.addTask(task);
  var numericTask=new dev.ftb.mods.ftbquests.quest.task.ItemTask(109,q);numericTask.setRawTitle("Copper items");set(numericTask,dev.ftb.mods.ftbquests.quest.task.ItemTask.class,"count",Long.MAX_VALUE);q.addTask(numericTask);var progress=(it.unimi.dsi.fastutil.longs.Long2LongMap)get(team,TeamData.class,"taskProgress");progress.put(numericTask.id,9007199254740993L);
  var reward=new ItemReward(103,q,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COPPER_INGOT,3));reward.setRawTitle("Copper reward");q.addReward(reward);
  var choice=new ChoiceReward(104,q);choice.setRawTitle("Choose an effect");var table=new dev.ftb.mods.ftbquests.quest.loot.RewardTable(105,f);table.setRawTitle("Choices");choice.setTable(table);q.addReward(choice);
  var command=new CommandReward(106,q);command.setRawTitle("Configured command");table.addReward(new dev.ftb.mods.ftbquests.quest.loot.WeightedReward(command,1));var child=new ItemReward(107,q,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COAL));child.setRawTitle("Coal");table.addReward(new dev.ftb.mods.ftbquests.quest.loot.WeightedReward(child,1));
  LiveFtbAccess live=new LiveFtbAccess();JsonObject frame=live.project(f,team,player),row=array(frame,"quests").get(0).getAsJsonObject();
  check(str(row.getAsJsonObject("subtitle"),"text","").equals("Subtitle instructions")&&str(row.getAsJsonObject("description"),"text","").contains("Mine copper"),"actual quest subtitle and description projected");
  JsonObject item=FtbService.find(array(frame,"rewards"),id(reward.id)).getAsJsonObject("item");check(str(item,"item_id","").equals("minecraft:copper_ingot")&&integer(item,"configured_count",0)==1&&integer(item,"stack_count",0)==3,"actual reward item ID/count projection independent of custom title");
  JsonObject tr=array(row,"tasks").get(0).getAsJsonObject();check(str(tr,"type","").equals("ftbquests:checkmark")&&tr.get("max_progress").isJsonNull()&&tr.get("progress").isJsonNull()&&bool(tr,"progress_hidden",false),"actual native boolean task hides progress numbers explicitly");
  JsonObject nt=array(row,"tasks").get(1).getAsJsonObject();check(str(nt,"progress","").equals("9007199254740993")&&nt.get("max_progress").isJsonPrimitive(),"real numeric task progress exact decimal string");
  var completed=(it.unimi.dsi.fastutil.longs.Long2LongMap)get(team,TeamData.class,"completed");completed.put(q.id,1);frame=live.project(f,team,player);JsonObject r=FtbService.find(array(frame,"rewards"),id(choice.id));check(bool(r,"can_claim",false)&&bool(r,"exclude_from_claim_all",false)&&array(r,"choice_candidates").size()==2,"actual choice candidates and eligibility");String hash=str(r,"table_hash","");Collections.reverse((List<?>)get(table,dev.ftb.mods.ftbquests.quest.loot.RewardTable.class,"weightedRewards"));JsonObject reversed=FtbService.find(array(live.project(f,team,player),"rewards"),id(choice.id));check(!str(reversed,"table_hash","").equals(hash),"actual table candidate order invalidates hash");Collections.reverse((List<?>)get(table,dev.ftb.mods.ftbquests.quest.loot.RewardTable.class,"weightedRewards"));
  set(command,CommandReward.class,"command","/say "+"x".repeat(20000)+"changed");frame=live.project(f,team,player);r=FtbService.find(array(frame,"rewards"),id(choice.id));check(!str(r,"table_hash","").equals(hash),"whole table hash changes with nonselected child command tail");check(array(r,"effects").toString().contains("preconfigured_server_command"),"nested command side effect exposed without executing");
  set(reward,Reward.class,"autoclaim",RewardAutoClaim.INVISIBLE);frame=live.project(f,team,player);check(FtbService.find(array(frame,"rewards"),id(reward.id))==null&&!bool(frame,"native_scope_safe",true),"native invisible reward hidden and blocks broad claim");set(reward,Reward.class,"autoclaim",RewardAutoClaim.DISABLED);
  set(q,Quest.class,"invisibleUntilCompleted",true);completed.clear();frame=live.project(f,team,player);check(array(frame,"quests").isEmpty()&&array(frame,"rewards").isEmpty(),"hidden quest never emitted by real projector");
  set(q,Quest.class,"invisibleUntilCompleted",false);set(q,Quest.class,"hideDetailsUntilStartable",Tristate.TRUE);Quest dependency=new Quest(108,ch);dependency.setRawTitle("Dependency");ch.addQuest(dependency);q.addDependency(dependency);frame=live.project(f,team,player);row=FtbService.find(array(frame,"quests"),id(q.id));check(row.get("tasks").isJsonNull()&&row.get("rewards").isJsonNull()&&row.get("subtitle").isJsonNull(),"native locked detail fields explicitly null");
  set(q,Quest.class,"hideDetailsUntilStartable",Tristate.FALSE);set(q,Quest.class,"hideTextUntilComplete",Tristate.TRUE);frame=live.project(f,team,player);row=FtbService.find(array(frame,"quests"),id(q.id));check(row.get("description").isJsonNull()&&row.has("description_unknown_reason"),"native hidden description stays null");
  completed.put(q.id,1);set(ch,Chapter.class,"alwaysInvisible",true);frame=live.project(f,team,player);check(!bool(frame,"native_scope_safe",true)&&array(frame,"rewards").isEmpty(),"native hidden completed candidate blocks whole global batch without content leak");
  set(ch,Chapter.class,"alwaysInvisible",false);table.addReward(new dev.ftb.mods.ftbquests.quest.loot.WeightedReward(choice,1));rejects(()->live.project(f,team,player),"table_cycle");
 }
 static void visibility()throws Exception{
  FileFixture f=new FileFixture();TeamData team=new TeamData(UUID.randomUUID(),f);ChapterGroup group=f.getDefaultChapterGroup();Chapter ch=new Chapter(10,f,group);group.addChapter(ch);Quest q=new Quest(11,ch);ch.addQuest(q);
  check(LiveFtbAccess.visible(ch,team)&&LiveFtbAccess.visible(q,team),"native visible chapter and quest");
  set(q,Quest.class,"invisibleUntilCompleted",true);check(!LiveFtbAccess.visible(q,team),"native hidden quest rejected even by direct ID");set(q,Quest.class,"invisibleUntilCompleted",false);
  set(ch,Chapter.class,"alwaysInvisible",true);check(!LiveFtbAccess.visible(q,team),"invisible chapter rejects quest");set(ch,Chapter.class,"alwaysInvisible",false);
  set(q,Quest.class,"hideTextUntilComplete",Tristate.TRUE);check(!LiveFtbAccess.textVisible(q,team),"native text-until-completed honored");
  set(q,Quest.class,"hideDetailsUntilStartable",Tristate.TRUE);Quest dep=new Quest(12,ch);ch.addQuest(dep);q.addDependency(dep);check(!LiveFtbAccess.detailsVisible(q,team),"native locked details hidden");
  UUID player=UUID.randomUUID();ChoiceReward choice=new ChoiceReward(13,q);check(choice.getExcludeFromClaimAll(),"actual built-in choice excluded from native claimall");
  ItemReward reward=new ItemReward(14,q);check(!team.getClaimType(player,reward).canClaim(),"actual incomplete native reward cannot claim");
  var completed=(it.unimi.dsi.fastutil.longs.Long2LongMap)get(team,TeamData.class,"completed");completed.put(q.id,1);check(team.getClaimType(player,reward).canClaim(),"actual completed unclaimed native reward can claim");
  @SuppressWarnings("unchecked") var claimed=(it.unimi.dsi.fastutil.objects.Object2LongMap<Object>)get(team,TeamData.class,"claimedRewards");claimed.put(dev.ftb.mods.ftbquests.util.QuestKey.forReward(player,reward),1);check(team.isRewardClaimed(player,reward)&&!team.getClaimType(player,reward).canClaim(),"actual already claimed native reward cannot claim");
 }
}
