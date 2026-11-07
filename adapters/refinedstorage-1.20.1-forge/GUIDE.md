# MCPMCP Refined Storage 适配器

版本0.1.0；MCPMCP plugin API/core 0.1.2；Minecraft 1.20.1 / Forge 47.4.28；Java17。仅适配当前已打开、激活且screen/menu/grid相互匹配的RS物品GridScreen。不会自动开/关菜单、切换界面、修改搜索框或直接修改服务端库存。

## 精确目标

- 实际文件：D:/Games/mc/Cheat/versions/神秘启旅/mods/refinedstorage-1.12.4.jar
- mods.toml：refinedstorage / 1.12.4
- SHA-256：d8ae3926c2121336efcd6956c43d91f21ce405b77ac7c7201c559d4c19f380d2
- distribution/plugin.json含完整目标版本/hash；构建同时检查精确hash。
- 实例另装refinedstorageaddons-0.10.0.jar，SHA-256为8371fe7932e88e3dd06401fd921a6ab2c52913287bd8994b096e47655c664491。本适配器没有addons直接依赖；只有实际复用RS GridScreen/GridContainerMenu和普通crafting slots时才走同一路径。未做addons游戏内验收，不声明全兼容。

## 构建与交付

在本工程目录使用Git Bash：

```bash
export JAVA_HOME='D:/Games/mc/mc-info-mcp/tooling/jdk17/jdk-17.0.21+9'
export GRADLE_USER_HOME='D:/Games/mc/mc-info-mcp/.gradle-user-home'
'D:/Games/mc/mc-info-mcp/tooling/gradle-8.8/bin/gradle' clean build --console=plain --max-workers=2
```

其他机器可用-PrsJar=实际路径/refinedstorage-1.12.4.jar，文件名和hash须一致。共享仓库plugin-api/build/libs/plugin-api-0.1.2.jar和core/build/libs/core-0.1.2.jar须已存在。使用本地flatDir + fg.deobf对实际JAR转换开发映射；目标hash进入依赖坐标以防共享缓存串用同名其他JAR。RS/core/API为compileOnly，不打入adapter。

build运行adapterContractTest、reobfJar和stageDistribution。build/libs/adapter.jar为生产映射；distribution/adapter.jar是同一文件。安装时将distribution/adapter.jar与plugin.json置于实例MCPMCP-Plugin/refinedstorage/，本地用户自行开启可信Java插件并完整重启客户端。Java插件不热替换。**本次仅产出工程和报告，没有修改实例、核心、客户端配置或启动游戏。**

## 可发现合同

先observe(kind=rs, arguments={})，data.contracts提供所有封闭schema，data.actions给出真实transport/normal权限。所有observer失败以data.error和data.available=false返回，避免核心0.1.2忽略observer顶层error。动作失败使用顶层error。未知字段拒绝。

| observer | 内容 |
|---|---|
| rs | 无需已开网格，可发现合同/目标/示例/限制 |
| rs.grid | session/menu、grid type/active/can_craft、游标、主背包36槽、合成九宫格与结果实际menu slot、矩阵摘要、搜索文本 |
| rs.storage | 客户端同步虚拟行：UUID/other UUID、quantity/craftable、物品ID/名称、NBT预览和摘要 |
| rs.operation | 按回执读回游标/主背包/合成变化；提取和自动合成含对应UUID当前行 |

虚拟行不是menu slot。stored/craftable行可有不同UUID，other_id仅供发现，动作不会猜测或转换UUID。quantity是网络同步数量；craftable通常quantity=0，其item.count是代表栈，不能当库存数量。

### 分页

首次observe rs.storage参数例如{query:minecraft:stone,limit:32}，不传cursor。后续使用next_cursor并保留相同query/limit；next_cursor为空时has_more=false、complete=true。返回精确total_matches、offset、returned。按UUID排序的冻结快照保证重复cursor的entries稳定，age_ms会变化，不在分页过程中合入后续变化；新查询不带cursor，动作前刷新。

每页1–64默认32；query最多128字符；原始行最多16384；原始值及匹配快照各最多8MiB；最多4份快照、30秒TTL，容量淘汰最旧。超限报错，不返回伪完整total。上下文变化失效；改筛选/limit或未发出的偏移拒绝。getAllStacks是客户端view map，不等同屏幕可见行，也不保证包含服务端所有物品，不扫描未同步网络/其他维度或流体。

## 受控动作

全部normal权限，必须已开/激活物品GridScreen且session/menu_id匹配，request_id为调用方生成的规范UUID；默认dry_run=true只预检，false才单次提交。提交前重复实时校验。world/player/connection/screen/menu/view对象改变更新session。

| action | transport | 行为/额外条件 |
|---|---|---|
| rs.extract | packet | stored UUID、expected item/NBT/quantity、空游标；single/half/stack到游标，shift到玩家背包；原生flags为2/1/0/4 |
| rs.insert_held | packet | 非空游标，expected item/NBT/count；single=true存1个，否则尝试整叠，可能部分存入 |
| rs.craft.start | packet | craftable UUID、expected item/NBT、can_craft、空游标；quantity 1–4096；单发StartRequest，不弹预览 |
| rs.crafting.take | input | CRAFTING grid、空游标、实际result_slot、expected item/NBT/count 1–64、矩阵摘要；一次左键PICKUP |
| rs.crafting.clear | packet | CRAFTING grid、空游标、矩阵摘要；原生GridClearMessage |

PACKET拒绝input动作take，SIMULATE拒绝packet动作，MIXED允许两种；不自动切换或伪标transport。

### 提取示例

以下尖括号值必须用最新观察替换：

```json
{
  "action": "rs.extract",
  "arguments": {
    "session": "<rs.storage.data.session>",
    "menu_id": 7,
    "request_id": "<调用方新UUID>",
    "stack_id": "<stored行UUID>",
    "expected_item": "minecraft:stone",
    "expected_nbt_sha256": "<该行64位摘要>",
    "expected_quantity": 16,
    "mode": "single",
    "dry_run": true
  }
}
```

预检确认后以相同request_id/语义参数、dry_run:false提交。核心外层action_id须用新的值，否则外层预检作业会被去重；内部request_id保持不变。查询核心job的result.operation_token后observe rs.operation {operation_token:回执}。

同session相同request_id/参数（dry_run除外）返回原operation token，不再提交；同ID不同参数冲突。去重最多1024次/会话，回执过期仍保留精简去重记录；用满须重开网格。回执最多128条/120秒，最旧可能被淘汰。换会话或重启不能宣称跨进程exactly-once。

### 读回与未知结果

提交返回delivery=submitted、server_success_confirmed=false、操作特定verification_hint、before及即时客户端readback。原生调用抛异常也保留transport_exception_outcome_unknown去重回执，不自动重试；提交后读回失败仍是已尝试提交。

- 提取：服务端同步后查operation和全新storage快照，核对同item/NBT网络减少、游标或背包增加。
- 插入：核对游标减少与全新storage同item/NBT增加，可能部分存入。
- 自动合成：无可靠task ID/确认/完成证据，人工查RS合成监视器和后续storage，不能据submitted推断完成。
- 取产物：核对游标exact item/NBT/count及输入变化，结果可因继续补料重新出现。
- 清空：核对输入清空及目标库存；不提供补偿、退款或自动回滚。

其他玩家/机器也能改变状态，operation不提供因果成功证明。RS原生封包不包含expected menu/item/NBT，客户端校验不能消除发包后的网络和服务端竞争。

NBT预览最多2048字符；完整摘要使用排序compound keys、保留类型/列表顺序的canonical SNBT tag SHA-256。无tag编码为null，与空compound不同。**仅覆盖ItemStack.getTag()，不覆盖模组外置capability状态**；count独立验证，damage另输出且通常在tag内。最多4096节点/深度32、单原子估算128KiB/canonical文本65536字符，超限rs_nbt_identity_budget，拒绝继续。

## 未实现/未验收

没有流体、pattern写入、任务监视/取消、服务端全网扫描、自动开关菜单、任意计数提取、多次循环合成或配方填料；JEI属于独立插件。未实现预览：真实ClientProxy接到预览响应会setScreen(CraftingPreviewScreen)，与保持当前界面约束冲突。自动合成使用原生预览界面Start按钮同一StartRequest；服务端仍自行计算和检查AUTOCRAFTING权限，不保证启动成功。

本次未启动游戏，未验收实际服务器权限/容量/延迟、addons无线合成网格或其他RS版本。旧MCP验收仅作语义参考，不证明本插件游戏内通过。

## 测试证据

adapterContractTest是JavaExec入口，挂到check/build，不是仅空JUnit任务成功。

- 公开注册、封闭schema/manifest、observer错误可见、核心Policy真实transport。
- ServiceTest：17组公开read/act行为场景，冻结分页/游标/TTL/预算、预检、动作护栏、去重、传输异常、读回失败、提交前实时重验。使用明确标记的detached grid fixture，不当实服。
- NbtIdentityTest：8项真实Minecraft tag身份/预算检查。
- PacketTest：9项实际目标JAR编码器/构造器检查，经ForgeGradle映射，无游戏或网络。
- evidence/clean-build.log：最终clean build真实构建含reobf和全部测试。
- evidence/target-api.log、rs-semantics.log：目标JAR实际javap、版本/hash。
- evidence/final-artifact-check.json、final-reobf-javap.log：最终产物hash/服务入口/映射检查。

后续人工验收：在测试世界正常放置RS网络并开Grid，先dry-run再单次提交，等同步后核对storage/游标/背包守恒；覆盖四种提取、两种插入、NBT同ID不同tag拒绝、换菜单拒绝；准备pattern/crafter后测自动合成及有输入的crafting take/clear。没有live结果前不把编译测试写作游戏内通过。
