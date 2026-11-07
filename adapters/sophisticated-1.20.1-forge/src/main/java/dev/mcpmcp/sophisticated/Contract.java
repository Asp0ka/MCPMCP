package dev.mcpmcp.sophisticated;

import com.google.gson.*;
import dev.mcpmcp.core.Jsons;
import dev.mcpmcp.core.Schema;
import java.util.*;

/** Closed contracts shared by the three independently packaged providers. */
public final class Contract {
 public static final String CRAFTING = "net.p3pp3rf1y.sophisticatedcore.upgrades.crafting.CraftingUpgradeContainer";
 public static String namespace(String type) { return switch(type) {case "storage" -> "sophisticatedstorage";case "backpack" -> "sophisticatedbackpacks";case "furnace" -> "ironfurnaces";default -> throw new IllegalArgumentException("unknown_adapter");}; }
 public static List<String> operations(String type) {
  var names = new ArrayList<>(List.of("quick_move", "quick_move_packet"));
  if (!type.equals("furnace")) names.addAll(List.of("sort", "select_crafting"));
  if (type.equals("backpack")) names.add("open");
  return List.copyOf(names);
 }
 public static JsonObject integer(int min,int max) {return Jsons.obj("type","integer","minimum",min,"maximum",max);}
 public static JsonObject string(int max) {return Jsons.obj("type","string","minLength",1,"maxLength",max);}
 public static JsonObject schema(String operation) {
  JsonObject properties=Jsons.obj("menu_id",integer(0,Integer.MAX_VALUE),"menu_token",string(80));
  List<String> required=new ArrayList<>(List.of("menu_id","menu_token"));
  if (operation.equals("open")) {
   properties.add("stack_token",string(80));properties.add("inventory_slot",integer(0,35));properties.add("expected_item",string(256));properties.add("expected_count",integer(1,64));
   required.addAll(List.of("stack_token","inventory_slot","expected_item","expected_count"));
  } else if(operation.equals("select_crafting")) {
   properties.add("upgrade_slot",integer(0,4095));required.add("upgrade_slot");
  } else if(operation.startsWith("quick_move")) {
   properties.add("slot",integer(0,16383));properties.add("expected_item",string(256));properties.add("expected_count",integer(1,Integer.MAX_VALUE));properties.add("state_id",integer(0,32767));
   required.addAll(List.of("slot","expected_item","expected_count","state_id"));
  } else if(!operation.equals("sort")) throw new IllegalArgumentException("unsupported_operation");
  return Jsons.obj("type","object","properties",properties,"required",required,"additionalProperties",false);
 }
 public static JsonObject observationSchema() {return Jsons.obj("type","object","properties",Jsons.obj("inventory_slot",integer(0,35),"offset",integer(0,16383),"limit",integer(1,128)),"additionalProperties",false);}
 public static String transport(String op) {return op.equals("quick_move")?"client":"packet";}
 public static String permission(String op) {return op.startsWith("quick_move")?"normal":"hack";}
 public static JsonObject describe(String type) {
  String kind=namespace(type);JsonArray actions=new JsonArray();
  for(String op:operations(type))actions.add(Jsons.obj("name",kind+"."+op,"input_schema",schema(op),"transport",transport(op),"permission",permission(op)));
  return Jsons.obj("kind",kind,"source","exact_jar_client_menu","contract_version",1,"observation_schema",observationSchema(),"actions",actions,
   "preconditions",List.of("world and connection present","exact target JAR binding; SophisticatedCore also pinned for storage/backpack","fresh menu_id and identity menu_token from this observer","empty cursor; no other menu is closed","quick_move requires fresh state_id and exact source item/count; active supported native slot only","open requires player inventory menu and verified backpack in inventory slot 0..35"),
   "verification","actions return delivery=submitted; read observer after server synchronization; client prediction is not server confirmation",
   "pagination","offset/limit apply to slots and upgrade containers independently; consult totals and has_more flags",
   "limitations",List.of("only currently open menus; no remote storage scanning","no automatic crafting, recipe mutation, arbitrary upgrade settings, mode/transport changes or server-side writes","unknown upgrades and ghost/filter slots are read-only","single native quick-move follows the mod configuration; destination and quantity are not guaranteed","furnace fuel acceptance requires inventory_slot candidate; no claim to enumerate every fuel"));
 }
 public static void validate(JsonObject args,String op) {Schema.validate(args,schema(op));}
 private Contract() {}
}
