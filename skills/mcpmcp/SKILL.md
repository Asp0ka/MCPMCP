---
name: mcpmcp
description: Use when operating or extending Minecraft MCP automation.
version: 0.1.2
author: MCPMCP contributors
license: MIT
platforms: [windows]
metadata:
  hermes:
    tags: [minecraft, forge, mcp, mcpmcp, gameplay]
---

# MCP Minecraft Plus 操作

通过 `mcpmcp` MCP 服务操作已启动的 Minecraft 客户端。先观察当前实例、模式和实时菜单，再执行有界动作并读回结果。核心不依赖 JEI/REI，移动需要 Baritone。

## When to Use

- 新建/加入世界、操作界面、观察世界、背包与原生配方。
- 玩家移动、交互、合成、容器与工作方块、聊天、插件适配与版本移植。
- 开发或调试客户端适配时读 [客户端适配流程](references/client-adapters.md)；组织游戏验收时读 [验收与交付](references/acceptance.md)。
- 不用于未经授权编辑整合包、配方脚本或其他模组文件。

## 前提

- 游戏已安装对应版本 MCPMCP JAR；配置路径指向当前实例 `config/mcpmcp.json`。
- 已连接 `mcpmcp` MCP 服务。通过 `tool_search` 查工具，使用返回的精确名称；不要调用旧 `minecraft_info` 来操作另一个实例。
- 凭据留在配置中，不复制 token 到聊天、日志、命令行或插件文档。
- 远程工具只允许修改 `MCPMCP-Plugin` 内声明/文本文档。修改其他客户端文件需要用户明确授权；不要用通关、合成或“兼容模组”推定获得修改配方、配置或存档文件的授权。这个限制属于本MCP的能力边界，不能声称它限制了另有权限的终端/文件工具或同JVM的可信Java代码。
- 用户已明确授权专用客户端自主开发和验收时，在该实例内持续完成必要操作，不重复索要同一授权。启动/停止前核对实例真实路径和进程参数；保留其他客户端、账号和存档，测试授权不外溢到它们。

## 操作顺序

1. 调用 `get_status`、`list_capabilities`，确认实例、world_epoch、连接、模式、执行方式、插件状态与恢复状态。主菜单时仍可使用 screen、screenshot 和 world/UI 动作。
2. 对每项动作读 `describe_action`。动作参数是版本合同；不能照抄别的版本或旧工程的槽号。
3. 按下方“观测与输入”规则，先读结构化玩家状态，再读与目标有关的背包/装备/局部方块/实体/当前菜单，确定目标坐标与可操作前提；不要每轮先截图。每次打开、关闭或切换界面后重新读取，断言预期菜单类型、身份和同步就绪，再执行后续步骤；打开失败后仍可能读到玩家菜单的同号槽位。`container_slot` 是来源容器索引；执行 `slot` 必须使用当前菜单返回的槽号。核对物品ID、count、NBT、目标容量和空游标，不混用背包与菜单索引。
4. 使用唯一 `action_id` 调用 `execute_action`，保留该 ID。`queued`/`running` 继续查 `get_job`；`submitted` 只证明已交送。按动作核对材料与产物总量、目标坐标、装备槽或NBT，不以任意状态变化代替目标效果；客户端槽位预测、state_id增加或炉火进度变化均不能证明服务端接受。需要持久性时关闭重开同一容器复查。
5. 网络超时返回 `delivery_unknown` 时，先查同一 ID 和实时状态，不换新 ID重复非幂等动作。ID结果已过期且无法确认效果时停止重试，报告不确定结果。
6. 取消用 `cancel_job`；紧急停止用 `execute_action(action="stop")`。之后核对移动、按键、游标、菜单和 `recovery_active/recovery_error`。恢复未完成时先解决，不启动下一批库存操作。

## 快速参考

| 需求 | 工具或动作 |
|---|---|
| 状态/发现 | `get_status`, `list_capabilities`, `describe_action` |
| 读数据 | `observe(kind, arguments)`, `get_player_state`, `get_inventory` |
| 区域 | `observe(blocks/entities/chunks/structures)`；参数见版本文档 |
| 配方 | `get_recipes(query, limit, cursor)`；用 `next_cursor` 继续 |
| 异步操作 | `execute_action`, `get_job`, `cancel_job` |
| 移动 | `move.to`，到达才成功；超时/取消停止 Baritone |
| 使用/挖掘 | `player.use`, `player.hold`, `player.dig` |
| 容器 | `container.move/craft/sequence/smelt/brew/workstation` |
| 装备/袋 | `inventory.equip/unequip/bundle`，玩家菜单槽号 |
| UI | `ui.click/focus/text/key/mouse/scroll` |
| 原生聊天/指令 | `chat.submit`，完整文本自行提供；先 `get_context` |
| 插件 | `get_plugins`, `read_plugin_file`, `write_plugin_file`, `reload_plugins` |

## 原生聊天输入与运行上下文

先get_context()（或observe(kind="context")）读取mode、transport、native_chat_allowed、server.cheats_enabled与player.permission_level。主菜单返回unknown；单人服务器首读refresh_pending时重读，读取observed_at_ms确认快照时间。远程服务器的全局作弊设置不公开时是unknown，不能用玩家OP权限反推作弊开关。

使用execute_action(action="chat.submit", arguments={"text":完整单行文本}, action_id=唯一ID)提交AI自行构造的文本；不加、不删、不固定任何前缀。例如普通消息、/time query daytime、#mine 16 minecraft:iron_ore、.help均由当前客户端原生处理链和已装模组接管。仅校验非空单行及256字符上限；Minecraft本身仍会按正常聊天规则归一化空格，不能承诺逐字节发到服务器。

- MCPMCP不按文本前缀分类或根据mode封锁chat.submit的内容，游戏和模组自行接受或拒绝；此能力受input执行方式约束，PACKET拒绝，SIMULATE/MIXED可用。mode与服务器权限查询供AI按用户任务自行决策，开放输入不是自动授权作弊、改配方、改客户端文件或修改其他模组设置。
- 先确认已在世界、无打开界面；有界面返回close_existing_screen_first，不覆盖玩家正在编辑的聊天或容器。仅submitted表示进入原生提交链，不证明服务器执行、模组接管或采集完成。读get_chat_messages和结构化效果验证；未知前缀可能变成普通公开聊天，不能假设任意模组已安装或前缀固定。
- Baritone支持所需批量采集命令时，让AI用该客户端实际前缀和帮助自行组合指令，避免逐矿石“扫描+move.to+player.dig”；精确坐标拆除仍可用player.dig。采集数量语义按实际模组帮助核对，独立读库存增量，不把命令受理当产物成功。
- 原生提交后启动的Baritone/Meteor等后台流程不属于chat.submit这个短任务；get_job或cancel_job仅描述提交，不代表后台已停止。按模组帮助通过同一入口发送停止命令并验证，例如已核实默认前缀时#stop；不得依赖MCP stop能通用停止任意模组流程。
- chat.send和command是保留的旧直发兼容接口，有原有权限/前缀限制，不能替代原生聊天模组命令。优先chat.submit，原生ui.key的旧权限保护仍保留，权限设置页也不自动化。

## 容器与工作方块

- 已打开目标菜单：省略 target 复用当前菜单，结束保持打开。目标原关闭：传 `target:{x,y,z,face,hand}`，状态机打开、等待、操作、验证、关闭。其他菜单冲突时不猜测。
- 先读取实际槽位，再构造全部 steps；不根据固定 UI 外观猜槽位。`container.move` count 为正数，省略/-1 表示全部；0、其他负数禁止。
- 工作台与玩家2×2：`container.craft` 传真实 recipe_id。`all` 会扩大合成数量，先核对材料和预算。
- `container.sequence` steps 可用 click、recipe、button、rename、wait、wait_result。熔炉/酿造/铁砧等读取 workstation 元数据后规划。
- 动态规则不一定是固定配方：酿造、铁砧、附魔等不能仅凭空配方列表断言不存在。制图必须提供真实有效地图数据，普通无 map ID 的 `filled_map` 不等于有效地图。
- 原版制箭台没有操作菜单。模组机器和非标准菜单需要适配插件，不能假装任意工作方块已通用兼容。
- 非空游标、源槽变化、容量不足、菜单被玩家替换时停止，不丢弃物品腾位置。取消可触发有界回填；恢复冲突时报告原槽和当前菜单，请用户处理，不能点击替换菜单。

## 模式与执行方式

- NORMAL 遵守正常客户端距离、可视和槽位规则；HACK 允许明确的额外封包行为；CHEAT 允许游戏指令；HACK_CHEAT 同时允许。服务器仍有最终权限。
- PACKET 禁止原生UI模拟和 Baritone；不要静默改模式或降级模拟。SIMULATE 通过客户端操作路径；最终仍由游戏发送协议包，不代表离线操作。
- 不通过 UI 设置或声明permission擅自提权；chat.submit的内容由AI结合get_context与用户任务自主决策，旧直发command仍需CHEAT。自动化不能操作控制自身权限的 MCP 设置；权限由用户在本地设置/配置。
- 原始封包仅针对经核对的 C2S 类/布局，最低权限由核心确定。不能用任意构造器、CustomPayload、命令方块/结构/书编辑载荷来绕过文件保护。

## 观测与输入

### 观测顺序：截图识图是下下策

按以下顺序解决当前决策，只取能改变下一步的信息：

1. **直接结构化读取**：玩家和环境读player/state，物品读inventory/equipment/container/recipes，世界读blocks/entities，界面读screen控件树。直接取得ID、坐标、状态、数量和菜单槽号，不用图像重新猜测已有数据。
2. **局部补查与分页**：先小范围coarse，锁定候选后仅目标邻域detailed；缩小边界、使用当前版本支持的过滤，按返回的next_offset/next_cursor续查。空页、少量命中、complete=false或truncated=true不等于查完；方块limit消耗候选扫描预算，空气和过滤未命中也占预算。取得当前决策所需信息即停，不扫描无关大区域。通用arguments未列字段时先查版本文档/实现，不推断查询能力不存在。
3. **结构化诊断与替代操作**：读取get_job错误、world_epoch、玩家位置、目标及邻接方块、距离或菜单身份与同步。dig_target_occluded先根据数据核对遮挡，调整安全站位、绕行或逐步清除已确认可达的遮挡；每步读回后再排依赖动作，不重复同一失败批次。方块存在不等于可见可交互，动作被拒绝不等于观测失效。UI控件过期或切屏时重新读screen并处理分页：get_screen()只读首页，不接受cursor；续页必须用observe(kind="screen", arguments={"cursor":返回的next_cursor})。文本先聚焦，不能见到GUI就截图。
4. **最后才截图识图**：相关结构化路径无法回答问题，且剩余问题确实依赖视觉时，先说明“缺少的具体信息、已尝试的结构化路径、截图如何改变下一步”，再截最小必要区域并识别一次。没有未解视觉问题就不截图识图；仍无法判断则报告限制，不连续识图碰运气。

- 默认不做每轮截图、移动前后截图、挖掘失败截图或HUD/背包识别，因为这些已有更快且精确的结构化读回；动作效果以状态、材料与产物、坐标和任务结果验证。单次失败、未读完分页或unknown都不直接触发视觉兜底。
- 用户明确要求检查画面或排版时，可按需直接执行视觉检查；用户要求的里程碑截图仅作留证时，不额外调用识图。自绘UI未暴露且正常控件路径无法操作时，按第4步定位。
- 未加载区块不强制加载，客户端未知的史莱姆/结构/完整服务端NBT保留unknown；不能用看图补造缺失元数据。获准使用单人server:true时按同kind和返回ticket轮询；切世界后旧ticket、菜单和控件失效。
- 视觉与结构化结果冲突时核对同一实例、世界和时刻，精确事实以实时结构化读回为准。UI坐标是GUI缩放像素，截图区域是framebuffer像素，不能混用。
- 长按使用ticks限定并保留取消路径；stop后等惯性消退，再读坐标确认稳定。
- 修改调用策略时同步已安装skill、仓库skill与MCP初始化/工具说明，并分别验证；只改源码不能宣称正在运行的任务已采用新规则。长会话可能保留旧上下文，须让该任务重读新规则；仅更新这些指引无需重启游戏。截图能力保留，此优先级是Agent操作规则，不宣称程序已硬性禁止截图。

## 模组适配插件

可选 Java 插件按目标 JAR 的完整 SHA-256 与实际版本绑定，安装于 `MCPMCP-Plugin/<mod-id>-<短hash>/`，需该实例本地开启 `trustJavaPlugins` 并重启。查询根 kind（如 `jei`、`rs`、`botania`）先取合同；JEI 用 `jei.categories`/`jei.recipes`/`jei.recipe`，填料 `jei.transfer` 需已开容器并先 `dry_run`。

回答“是否适配”或扩展实现范围时，先核对当前源码注册、发布清单和实际验收记录，分别说明独立适配、间接读取与未实现能力；字段如 `includes_curios` 只证明上游法术管理器包含饰品来源，不证明通用饰品槽读取、穿戴或拓展技能已实现。通用 API 接入、拓展新增数据和拓展专属主动技能分别列范围，避免把已交付插件数量写成整个模组生态的覆盖承诺。

- `jei.transfer` 的 `submitted` 只说明交给 JEI 处理器；读 `jei.transfer` 回执和容器槽位才算填料。JEI 15.20 无完成回调，必须读槽位。
- RS 网格、精妙存储/背包、更多熔炉等菜单型查询要求真实已打开菜单；不在菜单时返回 `available=false`，不要推断服务端库存。
- 精妙/熔炉 quick_move 合同v2要求 `expected_stack_hash`，从同次observer的 `data.slots[].item.stack_hash` 获取，并一起绑定menu_id/menu_token/state_id、item_id/count；包含普通NBT、ForgeCaps和完整count。旧指纹拒绝后重新观察，不用同ID/count猜身份。背包开启合成升级时，quick_move遵循该模组的升级优先路由，不承诺固定目的槽；读回实际role，定向放置用container.move。
- 灵灾/术士/新生魔艺/铁咒/植物魔法/无尽贪婪的配方可读客户端 RecipeManager；空页 complete=false 必须按 next_cursor 续查，不能断言为空。查看器额外动态配方也可查各自 JEI 分类。整合包可能改写配方，验收以实时配方为准，不能照搬 JAR 默认 JSON。
- 泛型配方（如Quark的`slab_to_block`）`outputs`是全部候选变体，当前变体看`displayed_outputs`/`slots[].displayed`；配方句柄必须沿用原查询焦点，查过别的物品也不能改变旧句柄。
- 模组键位冲突不得靠改用户键位解决；插件用该模组自身的客户端输入入口发单次脉冲，再按效果、冷却、法力读回判定。Traveloptics调用Iron原生入口时也必须先精确核验Iron依赖版本/full hash，不能只绑定附属自身JAR。submitted但法力不足不算施法失败重发理由；先确认资源与冷却，满足正常条件后再独立提交。
- Curios独立插件 `mcpmcp.curios` 已支持5.14.1+1.20.1 V2；先 `observe curios` 读闭合合同，`curios.slots` 无GUI读取动态普通/外观槽及拓展identifier，`curios.menu`取session/menu/state与真实当前页槽，`curios.candidates`只查指定背包物品的客户端原生谓词。不要硬编码ring数量或用46+index猜槽号，页外ProxySlot不能点击，外观页也可能混入无外观支持的功能槽。
- Curios写动作为normal/packet，dry_run默认true。`curios.swap`只做hotbar0..8与当前页真实CurioSlot单次SWAP，intent为equip/unequip/exchange，两侧ID/count/完整stack_hash连同identifier/index/cosmetic必填，空栈也要精确hash。非hotbar先core container.move定向暂存再刷新。保留原生标签、重复限制、绑定诅咒和事件；未知包装器只读，不直接写capability或增减槽位。
- Curios开关显式组织：原已开目标菜单保持；原关闭先curios.open→读回→操作/读回→curios.close→读回，其他界面冲突停止。page仅相邻，切页/切外观后重读session；render.set与cosmetics.set用expected_boolean+value防盲切，pending时先观察，交送不确定勿换UUID重发。Curios泛用饰品支持不等于附属主动技能覆盖；显示标志false也不等于非空外观饰品消失或功能停用。
- 插件返回的业务错误现在会原样上报，不再变成 `unknown_observation`；看到 `not_in_world` 先进入世界。

## 插件

- 目录 `MCPMCP-Plugin/<mod-id>-<目标JAR短SHA256>/`，正式manifest保留完整hash、api、平台和actions。示例与开发方法见工程 `docs/plugins.md`。
- 声明和GUIDE可热重载；更新后读取revision，调用时可传 `plugin_revision` 防止旧规则执行。无效重载必须保留上一版。
- Java适配器只在用户明确本地信任且重启时加载。不能通过文本工具安装adapter.jar。Java同JVM没有强文件沙箱；只加载用户审查过的代码。
- Recipe Provider可替换原生recipes查询；不能由此推定真实合成也已支持该模组机器。

## 验证

保存操作前后实时状态、任务ID、终态、恢复结果与缺失前提；每项请求/完整响应执行时追加JSONL，汇总从记录计算。装备看equipment、合成看材料和产物、移动看坐标、聊天看接收记录。保留通过、预期拒绝、不适用、前提缺失和未测的区别，旧工程测试不证明重写平台通过。

交付前逐条核对用户要求，完成已授权且有条件验证的部分；仍缺实现或验证的要求继续列为未完成，不用“首版”或一组场景通过替代完整范围。最终答复用简短中文说明产物、实际验证、保留状态和未完成边界，详细证据放项目文档。
