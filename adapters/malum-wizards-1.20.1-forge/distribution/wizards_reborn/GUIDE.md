# MCPMCP Wizards Reborn 适配器 0.1.0

## 安装与信任

本目录的 `plugin.json`、`adapter.jar`、`GUIDE.md` 是一套独立分发，目标 Minecraft 1.20.1 / Forge 47.4.28 / MCPMCP plugin-api、core 0.1.2 / Java 17。

目标模组精确版本：`1.20.1-0.2.9`。完整 SHA-256：`38f040319fe4dc16400b2507e3cb627a321cddba314de0d4acc4f2dcd8ffbfe7`。

将本目录内容复制到当前实例 `MCPMCP-Plugin/wizards_reborn-38f040319fe4dc16400b2507e3cb627a/`。只安装需要的模组插件；另一个插件不是依赖。保留目标模组本身及其现有依赖。manifest 的 `actions: []` 是正确的：Java provider 在启动时注册 `wizards_reborn.interact`，不要额外重复声明同名 packet action。

用户本地审查 Java 后启用 `trustJavaPlugins` 并重启客户端。Java 同 JVM，无文件安全沙箱；本适配器的生产代码没有文件写入、脚本或任意反射入口。此交付没有安装到客户端、没有改配置、没有启动游戏。目标 hash/版本不符应停用并重新适配，不能将强制加载当兼容性证明。

## 能力发现

先 `observe(kind="wizards_reborn", arguments={})`。响应中的 `contract.contracts` 提供完整封闭输入 schema，`data` 提供 Forge ModList 的实际 loaded/version、目标版本/hash、已知 recipe types 与具体限制。实际目标文件 hash 由核心 get_plugins 报告，插件不自行读客户端文件。

| kind | 内容 |
|---|---|
| `wizards_reborn` | 状态、合同、示例、限制；主菜单可查询 |
| `wizards_reborn.recipes` | 当前客户端 RecipeManager 内该模组的配方类型/类，支持分页与 query/type 过滤 |
| `wizards_reborn.recipe` | 指定 `recipe_id` 的真实输入、产物与该类型特殊资源成本 |
| `wizards_reborn.block` | 单个已加载坐标的实际 registry ID、方块类、BE 类和已核实的客户端同步字段 |
| `wizards_reborn.blocks` | 有界局部立方体扫描；不会加载区块 |
| `wizards_reborn.menu` | 当前真实菜单、所有者方块对象、动态槽位和游标；不支持的菜单返回 supported=false |

本插件不覆盖核心 `recipes` observer，也不依赖 JEI。查询到配方不代表可以自动合成。配方使用当前内存数据，既不读目标 JAR 的内置 JSON 充当当前整合包配方，也不借用单人服务端。支持其它 namespace 数据包新增的同类配方。

### 查询示例

```json
{"kind":"wizards_reborn.recipes","arguments":{"query":"","limit":16,"scan_budget":128}}
{"kind":"wizards_reborn.recipe","arguments":{"recipe_id":"<从上一查询取得的真实ID>"}}
{"kind":"wizards_reborn.blocks","arguments":{"x":0,"y":64,"z":0,"radius":2,"limit":8,"scan_budget":128}}
{"kind":"wizards_reborn.block","arguments":{"x":0,"y":64,"z":0}}
{"kind":"wizards_reborn.menu","arguments":{}}
```

坐标示例必须换成当前玩家附近的目标。`blocks` 中心距玩家方块坐标最多 64 格，radius 0..8，默认 2；最大 4913 候选位置。`limit` 1..32，默认 16；`scan_budget` 1..1024，默认 256。候选预算包含空气、未加载位置和过滤未命中。recipes 的预算针对本模组候选配方；详情只读取当前 manager 的目标 ID。

不完整响应必须用其 `next_cursor` 续查，保持 query/type 或中心/radius/block_id 不变；可以调整 limit/scan_budget。cursor 保存在有界内存中，120 秒过期，切世界或当前配方对象集合变更失效。分页不是一致性快照：方块状态可能在两页间变化。空页且 complete=false 不能解释为没有目标。`complete` 仅表示本次查询候选枚举完成，未加载方块及服务端隐藏配方仍未知。

`block.status` 区分 `outside_world`、`unloaded`、`not_target_mod`、`no_block_entity`、`supported`、`unsupported`。`supported` 表示识别此 BE 的观察/右键入口，不保证材料、仪式结构或正常右键会成功。

## 已核实的模组专用内容

- 十一类真实配方：alchemy_machine（物品/流体输入与产物、wissen、steam、药剂 ID）、arcane_iterator（wissen/health/experience、附魔/秘法附魔/水晶仪式、NBT）、arcane_workbench、arcanum_dust_transmutation、censer（效果与时长/等级）、crystal_infusion（light）、crystal_ritual（仪式 ID）、jeweler_table、mortar、wissen_altar（生成资源值）、wissen_crystallizer。实际类在 `mod.maxbogomol.wizards_reborn.common.recipe`。
- 六个已检查 BE 类型：WissenAltar（wissen/max/单容器/制造字段）；ArcaneWorkbench（wissen、input/output、制造字段）；WissenCrystallizer（wissen、容器、制造字段）；WissenCell（wissen、容器）；AlchemyMachine（三组只读 fluid capability 的真实罐容量/流体、input/output、wissen/steam 制造字段）；Crystal（light/max_light、容器、cooldown/max、startRitual/tickRitual）。
- AlchemyMachine 无独立暴露 wissen/steam 缓存，返回 unknown_no_exposed_buffer；不会把制造进度值误当全机资源。Wissen、light、steam 保留模组真实名称，不编造统一 mana。其余 BE 类状态未知并拒绝本插件交互。
- 动态菜单映射：ArcaneWorkbenchContainer、AlchemyMachineContainer、JewelerTableContainer、AlchemyFurnaceContainer；其它菜单 supported=false，不猜槽位或菜单归属。JewelerTable/AlchemyFurnace 可用现有核心正常右键显式打开，未扩展为已检查的资源 observer/专用 interact 类型。

## 安全交互与菜单

唯一 Java action：`wizards_reborn.interact`，transport=`client`、permission=`normal`。MCPMCP 核心策略允许 SIMULATE/MIXED，PACKET 拒绝，不自动切模式。即使 HACK 模式也保持正常距离/视线检查。

从同一时刻的 block、inventory 和 context 读出参数，再调用（下列是参数说明，尖括号需替换为读取结果）：

```json
{
  "action": "wizards_reborn.interact",
  "action_id": "<调用者唯一ID>",
  "arguments": {
    "dimension": "minecraft:overworld",
    "x": 0, "y": 64, "z": 0,
    "expected_block": "<已观测的真实方块ID>",
    "expected_item": "minecraft:air",
    "expected_count": 0,
    "expected_nbt": "",
    "hand": "main",
    "face": "up"
  }
}
```

- 原生单次 `gameMode.useItemOn`；不调用模组 craft/ritual/resource setter，不直接改 BE、背包、槽位，不发送自定义裸封包。持什么物品就执行那件物品的正常右键，空手也可能取出/改变方块物品；调用者应明确当前预期效果。
- 必须处于世界、客户端线程、无打开 screen/容器、空游标、玩家存活非睡眠非旁观、mayBuild 且本地 mayInteract。维度、方块 ID、手持 item/count/SNBT 必须精确一致。
- 只接受已检查类型的 BE；目标须已加载、在建筑高度和世界边界内。按 outline 的每个 AABB 选择指定面的可见命中，原生 ray trace 核对坐标与面，距离不大于 min(6, Forge BLOCK_REACH)。没有可见面则拒绝，不能据此推断方块不存在。
- 玩家正在使用物品或按住 use/attack 时拒绝，避免覆盖本地输入。服务器领地/角色/材料/结构检查继续由原生游戏及模组执行；本地检查不能证明服务端授权。
- 返回 `delivery=submitted` / `server_acceptance_confirmed=false`，不是产物或仪式成功。native_result=PASS/FAIL 也不会被包装成合成成功。读回 block/menu/inventory，核对预期物品数量和 NBT；需要持久性时关闭重开同一菜单验证。

打开后先读 `wizards_reborn.menu`，核对 class、position、dimension、owner_loaded_identity_match、still_valid、within_normal_reach、cursor 和 slots。`sync_ready` 明确 unknown；state_id 或进度变化不能单独证明服务端接受。所有者坐标直接取自当前 mod menu 的真实 blockEntity，未用最近右键坐标猜测归属。

槽位 action 采用输出 `slot`，不能采用 `container_slot`。槽位同时含真实 slot_class、inventory_class、player_inventory、item/count/NBT、may_pickup、slot_limit。`slot_limit` 和 may_pickup 不是任意物品的可放入证明；交由核心正常 mayPlace/容量校验。物品搬运沿用已有核心 `container.move` 或经过校验的 `container.sequence`，先显式打开并复用当前菜单（省略 target）；核心不会自动识别这些模组方块的 target 菜单类型。

- 调用核心 action 前读取 `describe_action`，核对实时菜单和所有步骤。不要假设 recipe-book 填料、模组按钮或仪式启动已兼容；本插件没有注册这些动作。
- 核心容器会话负责 menu 身份/epoch、容量/物品、同步变化、取消/回填与恢复；本适配器没有私有后台任务，也不拥有游标或持续按键。
- 用唯一 action_id 查 job 终态；submitted 仅表示发送/调用。网络交送未知时先查原 ID 和实时状态，不换 ID 重试非幂等右键。右键完成后无法撤回已提交给服务器的输入。

## unknown 与验证边界

所有资源/进度都来自**当前客户端 BE 副本**。字节码的 save/load 和 Lodestone/Fluffy Fur update-tag 路径证明字段可同步，不证明本次读取刚收到服务端数据；sync_age、server_state、服务端库存/权限保留 unknown。客户端可以局部动画/预测，不据此补造服务端状态，也不推断仪式已经完成。未知类/动态输出保留 unknown 或 complete=false。

已运行：Java 17、ForgeGradle 6.0.54、Forge 47.4.28 official 1.20.1 编译 + reobfJar；独立 contract test（每个插件 55 checks）；分发包 Java 17/ServiceLoader/目标 hash 与实际模组成员链接检查。日志及 `javap` 证据在适配器根 `evidence/`。

未运行：游戏加载、远程/单人真实方块同步、正向右键/容器操作、角色/距离/遮挡拒绝、取消和服务端效果读回。应由获授权安装验收阶段验证，不能用本次编译测试替代。
