package dev.mcpmcp.forge;
import com.google.gson.*;import dev.mcpmcp.core.*;import java.util.*;
public final class Capabilities {
 private static final Map<String,JsonObject> specs=new LinkedHashMap<>();
 static {
  add("stop","停止任务、释放输入和自己发起的移动","","");
  add("move.to","Baritone移动，需读坐标确认到达","x y z","x y z");add("move.stop","停止本工具发起的Baritone移动","","");
  add("player.look","设置yaw/pitch或朝向世界坐标","yaw pitch x y z","");add("player.sneak","设置潜行，stop会恢复旧状态","value","");add("player.select","选择快捷栏0..8","slot","slot");add("player.use","使用主手或副手","hand expected_item","");add("player.hold","长按左右键；纯发包左键指定实体","button hand entity_id ticks expected_item","");add("player.dig","正常挖掘指定方块","x y z face ticks expected_block","x y z");
  add("block.interact","按真实形状可见面交互","x y z face hand expected_block","x y z");add("block.place","对支撑面使用手持方块","x y z face hand expected_block","x y z");
  add("container.open","打开容器，调用后读取菜单确认","x y z face hand expected_block","x y z");add("container.close","游标为空时关闭当前菜单","menu_id","");
  String common="target steps timeout_ms require_change";
  add("container.click","当前或临时目标容器单次点击，保持初始开关状态",common+" slot button click expected_item expected_carried","slot");
  add("container.move","在两个菜单槽位之间转移指定数量，保持开关状态",common+" from_slot to_slot count","from_slot to_slot");
  add("container.craft","原版配方封包填料并取产物，支持玩家2x2与工作台",common+" recipe_id all take_result","recipe_id");
  add("container.sequence","工作方块通用语义步骤：click/button/rename/recipe/wait/wait_result",common,"steps");
  add("container.smelt","熔炉/高炉/烟熏炉：按已读取槽位步骤填料、燃料、等待产物、取出",common,"steps");add("container.brew","酿造台：按已读取槽位步骤操作药水、材料、燃料并等完成",common,"steps");add("container.workstation","铁砧/砂轮/制图/织布/切石/锻造/附魔等菜单步骤",common,"steps");add("inventory.bundle","收纳袋通过玩家菜单原生右键槽位逻辑取放，按步骤执行",common,"steps");
  add("container.button","切石、织布、附魔等菜单选项",common+" button","button");add("container.rename","铁砧物品重命名",common+" text","text");
  add("inventory.equip","玩家菜单shift点击装备","slot timeout_ms","slot");add("inventory.unequip","玩家菜单shift点击卸下装备","slot timeout_ms","slot");
  add("entity.interact","正常实体右键交互","entity_id uuid hand","entity_id");add("entity.attack","正常实体攻击","entity_id uuid","entity_id");add("ride.mount","与坐骑正常交互上马","entity_id uuid hand","entity_id");add("ride.dismount","下马","","");add("ride.move","限定时长坐骑前后/侧向/跳跃输入","forward strafe jump ticks","");
  add("chat.send","发送非指令聊天消息","text","text");add("command","显式游戏指令，仅作弊模式","command","command");add("wait","等待位置、菜单、库存或方块条件","condition ticks","condition");add("batch","最多32步操作，逐步校验与执行","steps timeout_ms","steps");
  add("packet.capture_start","开始有界封包元信息观察","query","");add("packet.capture_stop","停止观察","","");add("packet.send","已核对的PLAY C2S或模组构造器封包；外挂模式","packet_class mode signature values fields channel","packet_class");
  add("ui.click","点击已读取的屏幕控件","id x y button","");add("ui.key","指定GLFW按键编号输入","key action modifiers","");add("ui.mouse","指定鼠标点击","x y button action","");add("ui.text","向当前或指定文本框输入文字","id text append","text");add("ui.focus","聚焦控件","id","id");add("ui.scroll","屏幕滚轮","x y delta","delta");
  add("world.create","打开并操作原生新建世界界面","name seed gamemode difficulty generate_structures cheats","");add("world.join","加入本地存档或指定服务器","world address name","");add("world.leave","退出世界回主菜单","","");add("world.open_lan","通过原生接口开启局域网","port gamemode cheats","");add("screenshot","全屏或指定像素区域截图","x y width height","");
 }
 private static void add(String n,String d,String fields,String required){JsonObject props=new JsonObject();Set<String> booleans=Set.of("value","all","take_result","append","jump","cheats","generate_structures","require_change"),numbers=Set.of("yaw","pitch","forward","strafe","delta"),ints=Set.of("x","y","z","slot","menu_id","button","ticks","entity_id","from_slot","to_slot","count","timeout_ms","port","key","modifiers","width","height");for(String k:fields.split(" "))if(!k.isBlank()){JsonObject p;if(booleans.contains(k))p=Jsons.obj("type","boolean");else if(numbers.contains(k))p=Jsons.obj("type","number","minimum",-30000000,"maximum",30000000);else if(ints.contains(k))p=Jsons.obj("type","integer","minimum",-30000000,"maximum",30000000);else if(Set.of("target","condition").contains(k))p=Jsons.obj("type","object","additionalProperties",true);else if(Set.of("steps","signature","values","fields").contains(k))p=Jsons.obj("type","array","maxItems",128,"items",Jsons.obj("type","any"));else p=Jsons.obj("type","string","maxLength",4096);props.add(k,p);}JsonArray req=new JsonArray();for(String r:required.split(" "))if(!r.isBlank())req.add(r);specs.put(n,Jsons.obj("name",n,"description",d,"input_schema",Jsons.obj("type","object","properties",props,"required",req,"additionalProperties",false),"effect_confirmation","read_live_state_after_submission"));}
 public static boolean builtin(String n){return specs.containsKey(n);}
 public static JsonObject describe(String n){JsonObject s=specs.get(n);if(s!=null){JsonObject v=s.deepCopy();v.addProperty("transport",Actions.transport(n));v.addProperty("permission",Actions.permission(n));return v;}return Runtime.plugins().describe(n);}
 public static JsonObject list(){JsonArray list=new JsonArray();for(String n:specs.keySet()){JsonObject s=describe(n);s.addProperty("allowed",Policy.allowed(Runtime.config().mode,Runtime.config().transport,s.get("transport").getAsString(),s.get("permission").getAsString()));list.add(s);}return Jsons.obj("actions",list,"plugin_actions",Runtime.plugins().actions(),"observations",List.of("player","inventory","item","equipment","state","container","blocks","entities","chunks","structures","mods","paths","recipes","chat","screen","screenshot","packets","packet","packet_capture"),"client_file_mutation","denied_except_own_plugin_text","recipe_viewer_dependency",false);}
 private Capabilities(){}
}
