# 精妙存储、精妙背包、更多熔炉 MCPMCP 适配器

适用 Minecraft 1.20.1 / Forge 47.4.28 / MCPMCP core 与 plugin-api 0.1.2。三个独立冷加载 Java 插件；每个分发目录内有自己的 manifest.json、adapter.jar、GUIDE.md，且每个 JAR 只声明一个 ServiceLoader provider。不要把 build/libs 下三个 JAR 合并，不把它们当 Forge mod 放入 mods。

## 精确绑定

| 根 observation kind / action 前缀 | 目标版本 | 完整 SHA-256 |
|---|---|---|
| sophisticatedstorage | 1.4.79.2069 | 4d555d1754fbd8cfe3cf0f00adc65c9d13189dd12e7f9a7b9ad3a355b1210e74 |
| sophisticatedbackpacks | 3.24.63.2057 | 4d4be2e6a9988ff08260de621c26d0b43f2e03e3aede00842c66dc136a5303bf |
| ironfurnaces | 4.1.8 | e457052522cef1f8644b6929a64eab96de04ce9e6e32a715f1d68d79da26d66b |

两个精妙插件还在运行时校验 sophisticatedcore 1.3.78.2249，SHA-256 为 f32519f8605509e20e5ee9569b4b35cc30e17b85cb6f81f4111350dbc427db75。manifest 的单目标绑定不会遗漏此附加依赖检查。版本/hash 不匹配时运行时读取与动作均失败关闭；本地强制加载 manifest 不会放宽适配器自身绑定。绑定成功按当前 JVM 缓存，更新模组 JAR 必须重启。

## 安装交接

将 distribution 下所需的完整目录复制到实例根 MCPMCP-Plugin/。需用户在 MCPMCP 本地设置审查并启用 Java 插件信任后重启；热 reload 不加载新 Java。此次开发没有安装、改客户端设置、启动游戏或提交 commit。

## 读取与分页

调用 observe(kind=前缀, arguments={})。根响应始终给出 action 合同、preconditions、observation_schema、limitations；不在世界或目标菜单未打开时不会伪造槽位。data 是实际菜单快照或明确错误。

参数仅允许 inventory_slot (0..35，候选物品所在的玩家原始库存索引)、offset (0..16383)、limit (1..128，默认64)。slots 和 upgrades 使用同一个 offset/limit 独立分页，分别查看 slot_total/slots_has_more、upgrade_container_total/upgrades_has_more；单个升级内槽位最多128项，超过有 slots_truncated，完整槽仍可从顶层 slots 分页读取。

通用 data：menu_id、menu_token、menu_class、state_id、carried、target_menu；目标菜单的每个槽有真实 menu_slot、container_slot、slot_class、role、active、may_pickup、max_stack 和 item。item 保留 item_id/count/has_nbt，并新增 stack_hash（64位小写 SHA-256），不导出任意长 NBT。stack_hash 来自完整 ItemStack.save（包括 tag 和 ForgeCaps）及完整整数 count；按 NBT 类型、列表顺序、排序后的 compound 键规范化编码，空 tag 与无 tag 分别绑定。menu_token 绑定当前菜单对象及世界，不能用重复 menu_id 代替它。

精妙菜单按原生 getTotalSlotsNumber/getSlot 读取，包含 menu.slots 以外的升级槽。upgrades 返回真实 upgrade_slot、升级类、物品、enabled/runnable、selected/client_tab_open、合成支持标记及实际槽。仅明确识别 CraftingUpgradeContainer 的输入/结果角色，不表示支持全部升级的设置或自动处理。存储包括该版本的 LimitedBarrelContainerMenu 子类；装饰台、设置页、Create 挂载等其他菜单没有宣称支持。

背包额外返回玩家0..35库存中真实 BackpackItem：inventory_slot、item、stack_token、open_allowed。每次 observe 会刷新这些快照；执行 open 必须使用最近一次同槽 stack_token，内部比对完整 ItemStack（含 NBT 与数量），提交后消费此 token。装备/副手/Curios 槽不在 open 合同内。

熔炉依据完整槽类区分 input、fuel、output、generator_fuel、factory_input、factory_output、augment_red/green/blue；同时报告 inactive 槽，不能将存在理解为可用。furnace 给出等级、能量/容量、模式、燃烧状态、自动IO/拆分/红石/侧面配置、坐标和原生菜单使用的客户端 tile 同步数据：furnaceBurnTime、recipesUsed（本版燃烧缩放分母的原字段名）、cookTime/totalCookTime、factoryCookTime/factoryTotalCookTime、generatorBurn/generatorRecentRecipeRF、currentAugment。保留原始字段单位与语义，不把它们伪装成百分比或服务器权威快照。

若要检查某种燃料/输入，在打开熔炉后 observe(ironfurnaces,{"inventory_slot":候选索引})；每个槽的 accepts_candidate 调用真实 active 与 mayPlace。普通燃料、发电机燃料与工厂能量路径不同，不枚举或假定所有燃料都有效。当前增益物品从真实 augment 槽读取，currentAugment 是本版原始状态。

## 动作

所有动作只使用当前菜单，要求连接/世界存在、空游标、最近观测的 menu_id + menu_token；移动还要求当前 state_id 与源 expected_item/expected_count/expected_stack_hash 精确一致。合同版本现为2；两种 quick_move 动作都必须把目标 data.slots[] 中 item.stack_hash 原样传为 expected_stack_hash，即使 has_nbt=false 也不可省略；capability 变化也会拒绝。缺失/格式错误在合同阶段拒绝，源完整身份变化返回 slot_stack_changed_refresh_observation。未知参数拒绝，不能传入 mode、transport、任意 click 或目标坐标。不会关闭既有容器。

| 后缀 | 插件 | 额外参数 | transport / permission |
|---|---|---|---|
| open | 背包 | inventory_slot、expected_item、expected_count、stack_token | packet / hack |
| sort | 存储、背包 | 无 | packet / hack |
| select_crafting | 存储、背包 | upgrade_slot | packet / hack |
| quick_move | 三者 | slot、expected_item、expected_count、expected_stack_hash、state_id | client / normal |
| quick_move_packet | 三者 | 同上 | packet / normal |

例如先读取背包根 observer，从 data 和 inventory_backpacks 复制参数，再 execute_action：

```json
{"action":"sophisticatedbackpacks.open","arguments":{"menu_id":0,"menu_token":"替换为实际token","inventory_slot":9,"expected_item":"sophisticatedbackpacks:backpack","expected_count":1,"stack_token":"替换为该库存槽的实际token"}}
```

快捷搬运从同一次 observer 的 data 复制 menu_id/menu_token/state_id，从目标 data.slots[] 复制 menu_slot 为 slot、item.item_id 为 expected_item、item.count 为 expected_count、item.stack_hash 为 expected_stack_hash。NBT A 变为 B、空 NBT 新增、移除或 ForgeCaps 改变后，即使菜单/state_id/ID/数量不变也拒绝；重新 observe 后按新快照重新决策。此 hash 是内容指纹，不是授权 token，不能证明服务端未同步内容；仍以客户端已同步状态为边界。inventory_backpacks[].stack_token 与 open 的参数、单次消费规则保持不变，不要用它替代 expected_stack_hash。

打开后重新 observe，确认真实背包菜单再选择 upgrades 中 crafting_supported=true 的 upgrade_slot；调用 select_crafting 后再次读取 selected 与槽位。sort 沿用菜单当前 sort_by，不暗改排序方式。

quick_move 是一次原生 shift-click：可从玩家库存经模组路由填料，或从存储/已识别输入输出/合成槽取回；数量与目标由原生菜单、升级配置决定，没有指定数量或指定目标保证。未知升级设置、过滤/幽灵槽、增益安装槽保持只读；inactive 或 mayPickup=false 拒绝。会保留用户既有自动补充、快捷合成等模组行为，不能把一次点击等同于一次产物。

玩家归属校验支持直接引用当前玩家 Inventory 的槽，以及经过真实 Forge 源码/字节码核实的精确 SlotItemHandler + InvWrapper：必须 getItemHandler().getInv() 与当前玩家 Inventory 为同一对象，且原始 container_slot 在0..35。不以 menu_slot、坐标、类名字符串或物品相同判断归属；外来库存、任意 IItemHandler、范围/组合包装、包装或 handler 槽的自定义子类不会因此取得玩家角色。精妙菜单优先按 getUpgradeContainers()/getSlots() 的真实升级登记识别角色，未知升级即便引用玩家包装仍只读；原生被锁住的背包所在槽继续受 mayPickup 与 isInaccessibleSlot 限制。更多熔炉4.1.8原生构造器已确认直接使用 InvWrapper，无需 KubeJS 猜测或末尾36槽启发式。

quick_move 使用客户端 gameMode 原生方法（适合 SIMULATE/MIXED），quick_move_packet 直接提交固定 QUICK_MOVE 原版包（适合 PACKET/MIXED）。sort/select_crafting/open 已用真实字节码确认是原生 C2S 路径，但按保守 hack 权限声明。插件不会静默更改当前 mode 或 transport，所需策略不符由核心拒绝。

所有写动作返回 delivery=submitted、server_confirmed=false，不返回 observed_success。随后读回菜单/库存/产物并进行预期比较，客户端预测或页签打开不等于服务器接受。无异步自动成功宣称，无服务器 tile 直接修改、任意反射方法调用、配方更改或文件操作。

## 恢复与缺口

菜单改变、游标非空、源物品变化、绑定不符或升级缺失会拒绝。先由玩家安全放回游标物品并重新 observe；插件不代关菜单、不自动回滚玩家自己的交互。数据取自客户端已同步内容，未打开菜单/区块的数据不提供。未实现指定数量跨槽转移、自动配方填充与合成、增益安装与配置、任意升级开关/过滤器/泵/流体控制、无线加热器菜单或远程仓储扫描。

本次验证包括本机真实 JAR metadata/hash/javap、已安装 Forge 与编译缓存 universal JAR hash 对齐、公开 getItemHandler/getInv 的源代码与实际字节码、真实 Forge 编译重混淆、纯合同拒绝/权限测试、实际 Inventory/SlotItemHandler/InvWrapper 离线归属回归、三适配器×原生/packet×直接/包装槽的完整 NBT/ForgeCaps 拒绝与接受回归、三个 build/libs 与 distribution 产物隔离 ServiceLoader 加载和逐字节一致校验。槽测试仅初始化其独立 JVM 的本地注册表，不启动客户端/世界/网络。没有启动或操作游戏；服务器接受、各等级/增益组合、实际排序与燃料处理尚需父任务在游戏内验收。

## 重建

在本目录使用指定 JDK17、Gradle8.8 与旧工程 GRADLE_USER_HOME：

```bash
export JAVA_HOME='D:/Games/mc/mc-info-mcp/tooling/jdk17/jdk-17.0.21+9'
export GRADLE_USER_HOME='D:/Games/mc/mc-info-mcp/.gradle-user-home'
python scripts/inspect_targets.py
python scripts/inspect_player_inventory.py
python scripts/verify_jar_contracts.py
'D:/Games/mc/mc-info-mcp/tooling/gradle-8.8/bin/gradle' --no-daemon --console=plain clean check assemble
python scripts/package_distribution.py
'D:/Games/mc/mc-info-mcp/tooling/gradle-8.8/bin/gradle' --no-daemon --console=plain distributionTest
python scripts/inspect_stack_identity.py
```

证据在 evidence/，实际构建与产物验证日志在 evidence/build.log、distribution-test.log、jar-contract.log。三个 reobfStorageJar/reobfBackpackJar/reobfFurnaceJar 分别重混淆 Minecraft 调用；不分发被禁用的默认空 jar。
