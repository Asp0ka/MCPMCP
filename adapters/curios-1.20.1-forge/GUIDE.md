# Curios 5.14.1 独立适配插件

插件 ID：`mcpmcp.curios`；ServiceLoader provider：`dev.mcpmcp.curios.CuriosProvider`。

## 目标与安装

- Minecraft 1.20.1、Forge、Java 17；目标实例 Forge 47.4.21。离线编译使用已有 47.4.28 缓存；已对 47.4.21 的相关 Forge API 做字节码核对，仍须真实游戏验收。
- 唯一审查目标：Curios **5.14.1+1.20.1**。
- 完整目标 JAR SHA-256：`1e817919a35b37cf30524aaec73f0ca5130452f23f168f844854df282eb8e51f`。
- `distribution/` 中的 `plugin.json`、`GUIDE.md`、`adapter.jar` 是完整安装包。将这三项安装到实例的 `MCPMCP-Plugin/curios-1e817919a35b37cf30524aaec73f0ca5/`。先退出对应游戏实例，保留用户支持文件；该子任务没有执行安装。
- 本地显式启用核心 Java 插件信任后重启。Java 代码与游戏同 JVM，不宣称文件沙箱。文本插件热重载不能替代 Java 冷加载。
- 插件 manifest 和运行时 `ModList` 都检查完整版本与完整 JAR 哈希。强制兼容声明不绕过此插件自己的 pin。生产 JAR 不打包 Minecraft、Forge、Curios、Gson、plugin-api 或 core。

## 可发现合同

首先 `observe(kind="curios", arguments={})`。返回所有 observer/action 的闭合 JSON schema；未声明字段拒绝。所有动作注册为 **normal / packet**，`PACKET`、`MIXED` 可用；`SIMULATE` 被核心策略拒绝，不切换执行方式。`dry_run` 默认 `true`，只有显式 `false` 才发包。

### 读取

| kind | 参数与内容 |
|---|---|
| `curios` | 无参数；版本 pin、完整 schema、安全流程与限制，不访问游戏。 |
| `curios.slots` | 可选 `identifier`、`index`、`cosmetic`、`offset`（默认 0）、`limit`（默认 32、最大 64）。无需开 GUI，读当前玩家 Curios capability 的普通/外观处理器、真实物品、完整指纹、渲染、可见性、容量和当前页可用的 `menu_slot`。菜单读取不触发 mayPickup 回调；规则提示见 candidates，实际检查由 dry-run/submit 执行。 |
| `curios.menu` | 无参数；`session`、`menu_instance`、`menu_id`、`state_id`、`kind`、游标、页面、外观视图、grid、实际菜单槽、玩家主背包 0..35、pending 操作。不会自动打开界面。 |
| `curios.candidates` | 必需 `inventory_slot`（0..35）；可选与 slots 相同的过滤/分页，`cosmetic` 默认 false。以该真实背包物品读取各槽的客户端原生 `can_equip`/`can_unequip` 提示。未知处理器或未知谓词返回 null，不硬判合法。 |

`slot_types` 同时返回当前实体的动态槽类型定义，包括零槽类型、基础/实际数量、外观、渲染、图标、排序和 validators。基础 `definition_locked` 不是穿戴合法性的结论；属性可改变实际槽数。没有硬编码 ring/necklace 等清单，扩展模组注册的通用槽位与 Curio 物品自动读出。

`identifier` 是 Curios 原生字符串，常见如 `ring`，并不强制是 `namespace:path`。`index` 是该 identifier 下单独处理器的索引。普通与外观是两个独立命名空间。`menu_slot` 是当前 `AbstractContainerMenu.slots` 的真实索引；不得用 `46 + index`、背包索引、上一页槽号或私有 `ProxySlot` 猜测。

分页返回 `total_rows`、`complete`、`next_offset`；按原过滤续读。它是实时 offset 分页，不是原子历史快照。最多 256 类型、4096 capability 行、512 菜单槽、每页 64 行。候选页最多调用 64 行原生谓词。过预算整体拒绝，不返回看似完整的截断结果。客户端未同步的服务端信息不在可见范围。

### 动作与必需参数

所有动作共用：从**同次新鲜 `curios.menu`** 取 `session`、`menu_id`、`state_id`；另传新 `request_id` UUID；`dry_run` 可省略。会话绑定真实 world/player/connection/menu/screen/capability/slot 对象及 page/grid/view，30 秒过期；切屏、重建槽、切页、切外观或槽结构变化需重读。

| action | 额外必需参数 | 语义 |
|---|---|---|
| `curios.open` | 无 | 仅 `kind=player_closed`：当前玩家 inventoryMenu、没有任何 screen、空游标。发送一次 `CPacketOpenCurios(ItemStack.EMPTY)`。遗留菜单配置拒绝。等待再观察 `kind=curios_v2`。 |
| `curios.close` | 无 | 仅 token 绑定的真实 Curios v2 menu/screen，空游标。调用原生 LocalPlayer.closeContainer：一次 vanilla close 包并回到游戏画面。它不打开原版背包，也不关闭别的界面。 |
| `curios.swap` | `menu_slot`、`identifier`、`index`、`cosmetic`、`hotbar_slot`（0..8）、`intent`；两侧各 `expected_{curio,hotbar}_item/count/stack_hash` | 一次原版 `ClickType.SWAP`，`button=hotbar_slot`。`equip` 要求 Curio 空、hotbar 非空；`unequip` 要求 Curio 非空、hotbar 空；`exchange` 要求双方非空。整栈交换，超过容量拒绝，绝不拆分溢出或退回随机槽。 |
| `curios.page` | `expected_page`、`page` | 零基页号，目标等于当前页时 no-op，否则只允许相邻页。一包 `CPacketPage(menu_id,next)`，等待服务端回页后重新读取槽号和 session。多页移动需由调用者逐次操作和读回。 |
| `curios.cosmetics.set` | `expected_boolean`、`value` | 设置**外观视图**，不改物品本身。与原生 CosmeticButton 相同：本地 menu.toggleCosmetics + 一包 `CPacketToggleCosmetics(menu_id)`。`view_sync_pending=true` 时除 close 外禁止写；等 SPacketPage 再重建真实槽后解除。 |
| `curios.render.set` | `menu_slot`、`identifier`、`index`、`expected_boolean`、`value`、`expected_stack_hash` | 仅当前页可切渲染的普通 Curio 槽，一包 `CPacketToggleRender(identifier,index)`。外观槽、锁定槽、未知包装器拒绝。 |

布尔 set 已达目标时返回 `unchanged`，零包；需要改变时必须匹配 `expected_boolean`，不盲切。pending 不允许追加另一次变更，直到相关客户端读回或真实菜单实例更换；允许安全 close。相同 request_id 与完全相同 payload 在 120 秒内返回原回执，不再发送（最多保留 512 个）。同 UUID 换参数拒绝。dry-run 不占提交回执，可用相同 UUID 将 dry_run 改为 false 提交。提交之后不要改 UUID 自动重试。

`expected_curio_item/count/stack_hash` 来自 `curios.menu.data.slots` 中该 `menu_slot` 的 `item.item_id/count/stack_hash`；`expected_hotbar_*` 来自同次 `data.inventory` 中 `inventory_slot=hotbar_slot` 的 item。**空物品也须传 minecraft:air、0 和观察返回的完整 hash**，空值不是通配符。渲染动作 `expected_stack_hash` 取目标普通槽的 item。

完整指纹对 `ItemStack.save` 做按 tag 类型与 compound key 排序的 SHA-256，并额外包含完整 int count，含 ForgeCaps、普通 NBT、嵌套列表和大于 127 的数量；不是 tooltip、截断 NBT、Java hashCode 或只比较 ID/count。单栈指纹预算 1 MiB、深度 32、65536 节点，超限拒绝。

## 推荐调用流程

1. 无需界面先读 `curios.slots` 与 `curios.candidates`，了解真实槽位和手上物品的原生候选规则。
2. 读 `curios.menu`，记住初始是否已开。已有目标菜单必须保持，不要再次 open；存在其他 screen/menu 时停止，不接管。
3. 初始关闭时，显式 dry-run / submit `curios.open`，等待并重新读取真实 Curios v2 菜单。open 回执不能当菜单已就绪。
4. 若源/目的在背包非快捷栏，调用者先用核心 `container.move` 定向移到已核对的 hotbar 槽，再刷新观察。该插件不把多次 pickup 包装成同步单步任务。
5. 按真实槽 identity 和两侧完整 hash 对 `curios.swap` dry-run；读回未变化后显式提交。原版包使用空 carried 预测和空 changed-slots delta，不执行本地库存预测。
6. 再读两侧精确物品、数量、NBT/hash 和 Curios capability，确认预期交换；服务端是最终裁决。必要时关闭后重开同一 Curios 界面再核对持久性。
7. 初始已开则保持；初始关闭且调用者需要恢复游戏画面，显式 dry-run / submit `curios.close`，再确认 `kind=player_closed`、空游标。open→操作→close 必须由调用者显式组织。

所有提交只报 `submitted`、`server_confirmed=false`。`delivery_unknown` 表示发送可能已发生，同 UUID 仅查回执，勿用新 UUID 重试。读取 `pending_operation` 帮助定位；本地 cosmetic 预测、state_id 增加和 pending 清除都不是服务端因果证明。若 pending 长期不清，先查看状态并安全 close/reopen，不能连续 toggle 碰运气。

## 安全边界

仅审查过的原生 CuriosContainerV2 / CuriosScreenV2 / CurioSlot / CurioStacksHandler / DynamicStackHandler 及普通玩家热栏 Slot 可写；来源 capability wrapper 也核对原生实现。未知子类/包装器保持只读。发包前检查真实槽 identifier/index/cosmetic、两侧身份、state、空游标、active、visible/lock、原生 mayPlace / mayPickup、整栈容量及绑定诅咒。遵循 Curios 自身 event/canEquip/canUnequip 实现，不能拿客户端候选 true 当服务端许可。

特别注意 Forge `SlotItemHandler.getMaxStackSize(ItemStack)` 会临时 setStackInSlot。插件预检不用此重载，只用已审查 DynamicStackHandler 的只读 slotLimit 与物品 maxStackSize 计算容量；没有 capability setStack、resize、grow/shrink、服务端直接写或丢弃路线。dry-run 不发包、不切屏、不改物品/渲染/页面；会调用原生只读谓词及其正常 Forge 事件，因此第三方事件处理器自身的行为仍属已信任模组边界。

尚未实现：遗留菜单操作、任意背包源直接穿戴、多步 pickup、跨页自动寻找/换页穿戴、其他模组全局技能键、槽位增减、任意服务器扫描、任务级等待/恢复/成功证明。同步 PluginContext 不提供跨 tick 所有权，故不伪造这些功能。

## 离线构建与测试

前提：根工程已经提供 `plugin-api/build/libs/plugin-api-0.1.2.jar`、`core/build/libs/core-0.1.2.jar` 以及现有 ForgeGradle 缓存。当前目录执行：

```bash
export JAVA_HOME='D:/Games/mc/mc-info-mcp/tooling/jdk17/jdk-17.0.21+9'
export GRADLE_USER_HOME='D:/Games/mc/mc-info-mcp/.gradle-user-home'
'D:/Games/mc/mc-info-mcp/tooling/gradle-8.8/bin/gradle' --offline -Dnet.minecraftforge.gradle.check.certs=false --console plain clean check build
```

可传 `-PcuriosJar='<本地目标 JAR>'`；完整 hash 不符在配置阶段拒绝。目标别名只进入 `.gradle/target-dependency/`，不会夹进产物。`check` 显式执行 Java main `AdapterTest`，不是只有零测试的 Gradle test；包含公开 provider/闭合 schema/normal packet 策略、同步服务单次发送与错误防护、真实 NBT/ForgeCaps 指纹、会话过期、目标包 encode 与原生槽预检。`build` 将 **reobfJar 完成之后** 的 `build/libs/adapter.jar` 和文档拷到 `distribution/`。

测试界限：`ServiceTest.Host` 是显式脱离游戏的主机替身；`GuardTest` 使用实际 Curios/Forge 类，但通过测试专用 Unsafe 分配未初始化 Player/Slot/capability、手动种 event listener list 并用 Forge event ALLOW/DENY 替代外部模组谓词。不运行 ModLauncher，不证明 CuriosApi mixin 注入、真实世界同步或服务器成功；这些留给父任务真实安装验收。测试类、替身、Unsafe、依赖 JAR 均不进入生产产物。详细字节码审查与构建记录在 `evidence/`。
