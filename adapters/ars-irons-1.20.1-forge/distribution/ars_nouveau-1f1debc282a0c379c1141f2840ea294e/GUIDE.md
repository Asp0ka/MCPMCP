# MCPMCP 魔法适配器使用指南

目标 Minecraft 1.20.1 / Forge 47.4.28 / MCPMCP API 1（core/plugin-api 0.1.2）。本插件在客户端运行，不读取集成服务器，不改 capability、物品数据或服务端资源。

## 安装与信任

将此目录作为独立插件放入实例根 `MCPMCP-Plugin/`，保留 `plugin.json`、`GUIDE.md`、`adapter.jar`。Java 插件需用户在本地审查、信任并重启；文本热重载不会加载新 Java。构建交付没有写入游戏实例，也没有启动游戏。目标版本与完整 SHA256 由 manifest 固定；不匹配时应拒绝，不能将强制兼容视为已验证。

## 发现与读取

先 `observe(kind="ars_nouveau", arguments={})`，返回 schema、actions、examples、unknown 和 unsupported。不会覆盖核心 `recipes` observer。

```json
{"kind":"ars_nouveau","arguments":{"view":"player"}}
{"kind":"ars_nouveau","arguments":{"view":"spells","limit":16}}
{"kind":"ars_nouveau","arguments":{"view":"recipes","limit":16,"scan_budget":256}}
{"kind":"ars_nouveau","arguments":{"view":"machine","x":0,"y":64,"z":0}}
```

- `player`：本地玩家客户端法力、背包中法术容器、装备、当前法术选择。铁咒和 Traveloptics 同用铁咒法力/选择系统；该选择列表包含其 API 已收集的手持/Curios 来源。`inventory_index` 是玩家背包索引，不是当前菜单槽号。Curios 中未进入选择列表的普通装备不在该装备视图中，完整 Curios 专有状态未实现。
- Ars 的 `spellbooks` 暴露 slot、glyph_ids、selected_signature、基础法力消耗；只对物品副本调用读取 API，避免 Ars 构造器 getOrCreateTag 改动实际物品。铁咒容器保留 null 空槽和解码失败，不把空书当异常。
- `spells`：对应 mod 命名空间下注册法术/符文，成本是基础值，不能当作服务端最终消费。
- `recipes`：实际客户端 `RecipeManager` 同步对象，按配方 ID 命名空间或类型命名空间匹配。`type` 是真实 RecipeType ID，支持精确过滤；`query` 是 ID 子串。不是读取 JAR 内 JSON 冒充服务器运行时配方。
- `limit` 1..32（默认16）、`scan_budget` 1..2048（默认256）。`complete=false` 的空页仍须继续。携带 `next_cursor` 续查，其他参数必须与上一页一致。游标单次使用、60秒有效、最多32个；切世界或配方对象重载失效。分页候选有界处理，但新页建立/核对候选快照需要枚举并排序客户端注册集合；不宣称总 CPU 工作量只有 scan_budget。
- `machine` 必须提供确切 x/y/z，只读已加载区块中的单个方块，不强加载区块。`unknown` 不等于0、不存在或加工失败。客户端同步新鲜度及服务端权威值均保持 unknown。

## 玩家动作与验证

动作的 schema 由上述 discovery 和 `describe_action` 返回。所有插件动作 `normal + input`，PACKET-only 会由核心拒绝；SIMULATE/MIXED 可用，调用时不会自动改 mode/transport。铁咒/Traveloptics 的 `cast`、`quick_cast` 在父任务执行时同一客户端线程同步调用一次模组原生处理器，没有核心按键子任务；其他委托动作仍需继续查询 `core_job.action_id`。出现未知交送状态时，先查原 action_id 与实际效果，不换 ID 自动重施。

交互例子（坐标、ID与物品必须取自当前读取）：

```json
{"action":"ars_nouveau.interact","arguments":{"x":0,"y":64,"z":0,"face":"up","hand":"main","expected_block":"ars_nouveau:real_block_id","expected_item":"minecraft:real_item_id"},"action_id":"magic-interact-001"}
```

`interact` 在适配器预检目标 mod、物品、菜单、空游标与已加载区块，再委托核心 `block.interact` 的目标方块、距离、可视形状、模式与传输规则；face 默认 up。只代表正常右键交互，不保证打开菜单或完成合成。NORMAL保留正常距离/可视规则；HACK沿用核心显式模式行为。

铁咒/Traveloptics 施法只对选中的 `ExtendedKeyMapping` 实例做一次按下/释放，并直接调用 `ClientInputEvents.onClientTick`；不广播 GLFW/Forge/全局键事件，不改键位，不受共享物理键、未绑定或 modifier 影响。模组原生处理器发送正常 `CastPacket` / `QuickCastPacket`，服务端正常校验法力、冷却、装备及施法限制。调用前要求同一客户端线程、无屏幕/容器、空游标、关闭法术轮盘，且本模组所有相关键位没有 held/queued/pending 输入；忙时返回 `native_input_busy` 并保留玩家输入。只读固定私有输入标志用于避免误消费玩家待处理输入；不写私有字段或法力/冷却/capability。Ars 的翻页/开书仍委托 `ui.key`，其原有键位冲突检查保留。

`cast`/`quick_cast` 在父任务客户端执行时重新核对法术命名空间、ID、source、source_index，再同步脉冲并调用原生处理器，没有身份检查与核心子任务之间的排队间隔。服务端选择和客户端快照仍可能不同步，必须读回效果；不宣称服务器原子身份保证。每次仅提交一次，finally 释放自己的输入；异常或未消费返回 `delivery=unknown`、`retry_safe=false`，清理残留脉冲防止后续 tick 误施法。取消尚未执行的父任务不会施法，已经提交的施法不能靠取消父任务撤回。其他委托动作仍有子任务排队间隔与取消边界。动作未提供 dry_run；传入未知 dry_run 参数会在 schema 层拒绝且不执行。

动作完成后重新观察法力、选择、cooldowns、casting、目标世界效果/库存。mana 减少、输入 submitted 或菜单 state_id 变化均不能单独证明完整目标成功。任何持续施法取消/效果仍需按具体模组正常操作验证。

## 工作台与装备边界

配方查询永远 `execution_supported=false`。魔法工作台/祭坛/符文/装备升级没有被包装成“通用配方自动执行”。铁咒 scroll forge、inscription table、arcane anvil 有动态规则，不属于三个炼金锅 RecipeType 的全集；未实现其专门菜单工作流，不用空列表断言没有规则。打开菜单后读取核心 `container` 的真实槽位和类型，再使用已支持核心菜单动作；不能由本插件查询结果推断任意模组菜单可自动加工。

Traveloptics 的特殊盔甲技能、武器形态、附加服务器资源与独立能力键未实现，明确 unsupported；其通用法术通过铁咒单模组客户端处理器及正常服务端施法包提供。本次仅完成离线编译、合同测试、ServiceLoader与API签名/字节码核对，未启动游戏，未实测服务端正向效果。

目标版本：`4.12.7`

目标 SHA256：`1f1debc282a0c379c1141f2840ea294eede6f6b544c589663f40bbe17b59a1af`

## Ars 专项

`ars_nouveau.next_spell` / `previous_spell` / `open_book` 必填 expected_item，使用 `ModKeyBindings.NEXT_SLOT/PREVIOUS_SLOT/OPEN_BOOK` 当前绑定，通过正常 `KeyHandler` 路径；书选槽按服务端同步后读回。

```json
{"action":"ars_nouveau.next_spell","arguments":{"expected_item":"ars_nouveau:novice_spell_book"},"action_id":"ars-next-001"}
```

`ars_nouveau.use` 必填 expected_item 和 expected_spell（从 player.selection.selected_signature 原样取得，示意为 `[ars_nouveau:glyph_self, ars_nouveau:glyph_light]`）。使用当前书，不编写任意新法术；基础魔力不足、学识、冷却与最终施法由模组自行验证。

机器资源：SourceJar/AbstractSourceMachine 的 source/max/transfer；Imbuement 同步材料与draining；EnchantingApparatus 同步物品和is_crafting。附魔装置消耗周围源质，装置本身没有源质库存时返回unknown。精确来源以实际客户端同步字段为准。

Ars 特殊配方详情来自实际同步实例的 public asRecipe（source、pedestal、reagent、glyph/exp、crush多产物概率等）；无此API的特殊子类保留 details_complete=false，不执行 assemble、随机产物或服务端配方逻辑。
