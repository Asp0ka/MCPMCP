package dev.mcpmcp.magic;
import com.google.gson.*;
import net.minecraft.nbt.*;
final class DataTest {
 static void run(){
  ContractTest.check(Resources.gridSize(82)==9,"extreme live menu has 81 inputs plus output");
  ContractTest.check(Resources.gridSize(50)==7&&Resources.gridSize(26)==5&&Resources.gridSize(10)==3,"other real tiers distinguished");
  ContractTest.check(Resources.gridSize(81)==0&&Resources.gridSize(1)==0,"unexpected slot layout not fabricated");
  JsonObject o=new JsonObject();Resources.number(o,null,"mana","mana");ContractTest.check(o.getAsJsonObject("mana").get("status").getAsString().equals("unknown"),"missing payload not zero");
  CompoundTag n=new CompoundTag();Resources.number(o,n,"mana","mana");ContractTest.check(!o.getAsJsonObject("mana").has("value"),"absent tag not zero");
  n.putInt("mana",0);Resources.number(o,n,"mana","mana");ContractTest.check(o.getAsJsonObject("mana").get("value").getAsInt()==0,"explicit server zero known");
  n.putString("mana","oops");Resources.number(o,n,"mana","mana");ContractTest.check(!o.getAsJsonObject("mana").has("value"),"wrong tag type unknown");
  ContractTest.check(Resources.inventory(n).get("status").getAsString().equals("unknown"),"absent inventory unknown");
  n.put("Items",new ListTag());ContractTest.check(Resources.inventory(n).getAsJsonArray("nonempty_slots").isEmpty(),"explicit empty inventory known");
  n.putInt("mana",25);n.putInt("target",100);ContractTest.check(Resources.ratio(n,"mana","target").get("value").getAsDouble()==0.25,"ratio from explicit data");
  n.putInt("target",0);ContractTest.check(!Resources.ratio(n,"mana","target").has("value"),"no target does not fabricate progress");
 }
}
