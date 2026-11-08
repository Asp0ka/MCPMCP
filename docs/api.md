# 调用合同（1.20.1-forge）

MCP外层工具19项（新增只读get_context），动态动作通过describe_action/list_capabilities发现。模组内置Streamable HTTP endpoint为 `/mcp`，仅绑定127.0.0.1并要求Bearer token；配置的端口范围默认为5210–5220，运行时实际端口见 `config/mcpmcp-runtime.json`。HTTP POST必须包含Accept `application/json, text/event-stream`；支持initialize/ping/notifications/initialized/tools/list/tools/call，不支持SSE/session/listChanged。旧Python stdio桥接仍可选使用；POST `/call` 是其兼容内部接口，不应配置为MCP HTTP transport。

## 原生聊天输入与运行上下文

先get_context()（或observe(kind="context")）读取mode、transport、native_chat_allowed、server.cheats_enabled与player.permission_level。主菜单返回unknown；单人服务器首读refresh_pending时重读，读取observed_at_ms确认快照时间。远程服务器的全局作弊设置不公开时是unknown，不能用玩家OP权限反推作弊开关。

使用execute_action(action="chat.submit", arguments={"text":完整单行文本}, action_id=唯一ID)提交AI自行构造的文本；不加、不删、不固定任何前缀。例如普通消息、/time query daytime、#mine 16 minecraft:iron_ore、.help均由当前客户端原生处理链和已装模组接管。仅校验非空单行及256字符上限；Minecraft本身仍会按正常聊天规则归一化空格，不能承诺逐字节发到服务器。

- MCPMCP不按文本前缀分类或根据mode封锁chat.submit的内容，游戏和模组自行接受或拒绝；此能力受input执行方式约束，PACKET拒绝，SIMULATE/MIXED可用。mode与服务器权限查询供AI按用户任务自行决策，开放输入不是自动授权作弊、改配方、改客户端文件或修改其他模组设置。
- 先确认已在世界、无打开界面；有界面返回close_existing_screen_first，不覆盖玩家正在编辑的聊天或容器。仅submitted表示进入原生提交链，不证明服务器执行、模组接管或采集完成。读get_chat_messages和结构化效果验证；未知前缀可能变成普通公开聊天，不能假设任意模组已安装或前缀固定。
- Baritone支持所需批量采集命令时，让AI用该客户端实际前缀和帮助自行组合指令，避免逐矿石“扫描+move.to+player.dig”；精确坐标拆除仍可用player.dig。采集数量语义按实际模组帮助核对，独立读库存增量，不把命令受理当产物成功。
- 原生提交后启动的Baritone/Meteor等后台流程不属于chat.submit这个短任务；get_job或cancel_job仅描述提交，不代表后台已停止。按模组帮助通过同一入口发送停止命令并验证，例如已核实默认前缀时#stop；不得依赖MCP stop能通用停止任意模组流程。
- chat.send和command是保留的旧直发兼容接口，有原有权限/前缀限制，不能替代原生聊天模组命令。优先chat.submit，原生ui.key的旧权限保护仍保留，权限设置页也不自动化。

## 任务

```json
{"action":"container.craft","arguments":{"recipe_id":"minecraft:oak_planks"},"action_id":"craft-unique-id"}
```

先execute_action，保留action_id，get_job轮询queued/running，最后独立observe。submitted只证明交送；observed_success表示对应可观察条件满足，不能推定未查询的服务器持久性。网络超时则delivery_unknown，同ID查结果再决定，不另生成ID重复请求。

cancel_job只取消该任务；stop取消全部任务并释放自己的移动/输入。status的recovery_active表示仍在恢复；recovery_error表示需要观察当前菜单/游标并处理。任务上限32、已缓存结果512、ID防重记录有界；容量满明确拒绝。

## 容器例子

先observe(kind="container")得到实际slot与container_slot。菜单槽号不是背包清单slot；例如玩家快捷栏0对应玩家菜单36，箱子菜单通常不同。不要将例子当固定映射。

```json
{"action":"container.move","arguments":{"from_slot":0,"to_slot":62,"count":2},"action_id":"take-two-unique"}
```

这复用当前已打开菜单；关闭状态时传target:{x,y,z,face,hand}，操作完恢复关闭。count省略/-1=全部、正数=数量，部分搬运上限32。先核对目标可放、容量和空游标，不隐式替换其他物品。

container.sequence/brew/smelt/workstation接收steps：

```json
{"steps":[{"op":"click","slot":0,"click":"swap","button":1},{"op":"wait_result","slot":2,"timeout_ticks":500},{"op":"click","slot":2,"click":"quick_move","button":0}],"timeout_ms":45000}
```

SWAP button0..8对应快捷栏，40对应副手，其余普通点击button0/1。新任务先验证steps，实际每步仍检查当前槽位。熔炉必须另外准备燃料；制图需有效map数据；酿造使用同步菜单数据。rename只支持packet路径，SIMULATE下使用真实UI文本输入。原版制箭台没有菜单。

## 观测

observe(kind,arguments)支持player/inventory/item/equipment/state/container/blocks/entities/chunks/structures/mods/paths/recipes/chat/screen/screenshot/packets/packet/packet_capture。

- blocks：min/max:{x,y,z}，detail=coarse或detailed，include_air、limit、offset。返回候选预算、next_offset、加载/截断与来源。
- entities：radius或min/max，detail，limit与cursor；过滤未命中仍消耗候选预算，继续next_cursor。
- recipes：query、limit、cursor。便捷get_recipes同样使用cursor，不是offset；next_cursor可能在本页空时仍存在。
- inventory/equipment：detail=coarse/detailed；item查询按具体slot。NBT有限深度/节点，不代表完整服务器私有数据。
- chunks/structures：客户端缺失seed/结构元数据时unknown。单人server:true显式提交到服务器线程，返回ticket，重复同kind+ticket取结果；ticket限时且切世界失效。offset翻页；structures若next_starts_offset非零，下一请求使用返回next_offset与starts_offset继续同区块内结构，不跳过剩余starts。只扫已加载数据，不生成区块。
- chat：after、limit，环形缓存512；表示初始化后收到的消息，并非全历史。
- screen：GUI逻辑尺寸、控件id/标签/可见/激活/焦点/文本长度、next_cursor。控件标识30秒有效，切屏或控件替换失效；文本值默认不回传。
- screenshot：可选x/y/width/height均为framebuffer左上角像素；observe返回MCP image内容，不写任意截图文件路径。
- packets：query/direction/offset/limit；packet按id或class描述；packet_capture按after/limit读取有界元信息，非无限原始payload。Forge目录是有界发现，不保证覆盖所有自定义传输。

大查询必须检查complete/truncated/next_*，unknown不能改成false或空集合结论。服务端NBT、未同步结构、模组私有库存不编造。

## 观测优先级与识图兜底

默认顺序：直接结构化读取 → 局部粗查/目标细查/过滤分页 → 任务错误诊断与安全站位/控件刷新 → 最后截图识图。player/state、inventory/container/recipes、blocks/entities和screen分别回答状态、物品、世界及界面问题；不能用图像替代已有精确字段。

blocks的block字段为精确方块ID过滤，支持radius或min/max；limit消耗候选扫描预算，空气与过滤未命中也计入。空页仍按next_offset续读；entities/screen按next_cursor继续；get_screen()不接受cursor，screen续页须调用observe(kind="screen", arguments={"cursor":返回的next_cursor})。dig_target_occluded先检查当前位置、目标及邻接遮挡，基于数据绕行或清障再读回，不因一次拒绝直接截图。客户端unknown和未读完也不触发视觉兜底。

只有相关结构化路径无法解决且问题确实依赖视觉时，说明缺少的信息、已尝试路径、截图对下一步的作用，再截最小区域并识别一次；仍无法判断则报告限制。用户明确要求检查画面或排版可按需使用；单纯留证不额外识图。不每轮截图、不靠截图读HUD/背包或验证动作成功。这是Agent调用指引，保留截图能力，不构成程序硬禁用。已运行会话可能缓存旧skill和MCP描述，须在该会话重读规则；无需重启游戏。

## 界面、移动与模式

ui.key使用GLFW数字键码、tap/press/release；ui.mouse使用GUI逻辑坐标和有限按键；文本先ui.focus再ui.text。stop/切屏释放自动化持有的输入，保留玩家原先输入。UI不能改变MCP自身授权设置；普通模式通过聊天界面提交slash也被拦截。

move.to接收x/y/z/timeout_ms，持续running，到达才成功，取消/超时停止自己的Baritone。PACKET模式拒绝Baritone和UI；原始packet固定packet，不在SIMULATE偷发。观测截图不需要模拟输入，PACKET仍可读取。

配置模式的CHEAT只允许客户端发送显式指令，不赋予服务器OP；HACK只允许额外协议行为，不保证服务器接受。插件permission不能降低核心封包最低权限。


## 内置MCP HTTP

Forge客户端模组默认随游戏启动在本机回环地址提供Streamable HTTP MCP，单一endpoint `/mcp` 同时用于POST和返回405的GET探测。使用配置文件token的Bearer认证；运行时实际端口写入同目录 `mcpmcp-runtime.json`（不包含token）。MCP工具目录从core内置资源读取，和旧stdio桥接保持一致。HTTP `/call` 是仅供旧桥接兼容的内部接口，不应配置为MCP transport。当前仅支持无状态JSON请求/响应与19项工具，不支持SSE/session/listChanged。


发现文件只作候选地址，兼容bridge发送Bearer前通过 `/identity?challenge=<64位随机hex>` 验证HMAC-SHA256签名与PID/instance/port。该只读接口不接收凭据、不操作游戏；证明通过后必须沿用同一TCP连接发送 `/call`，禁止redirect或带凭据重连。旧模组没有发现文件时保留旧直连兼容，不能宣称旧版具备此校验。
