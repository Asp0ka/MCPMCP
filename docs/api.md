# 调用合同（1.20.1-forge）

MCP外层工具固定18项，动态动作通过describe_action/list_capabilities发现。桥接使用stdio每行一个JSON-RPC消息；localhost HTTP的POST `/call` 是内部鉴权端点，不暴露公网。

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

## 界面、移动与模式

ui.key使用GLFW数字键码、tap/press/release；ui.mouse使用GUI逻辑坐标和有限按键；文本先ui.focus再ui.text。stop/切屏释放自动化持有的输入，保留玩家原先输入。UI不能改变MCP自身授权设置；普通模式通过聊天界面提交slash也被拦截。

move.to接收x/y/z/timeout_ms，持续running，到达才成功，取消/超时停止自己的Baritone。PACKET模式拒绝Baritone和UI；原始packet固定packet，不在SIMULATE偷发。观测截图不需要模拟输入，PACKET仍可读取。

配置模式的CHEAT只允许客户端发送显式指令，不赋予服务器OP；HACK只允许额外协议行为，不保证服务器接受。插件permission不能降低核心封包最低权限。
