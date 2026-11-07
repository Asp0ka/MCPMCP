# Forge 1.20.1 首版验收

本报告是本次实际请求/响应、构建输出和图像的人工归纳，不是自动生成的全量测试转录。仅操作专用实例 `D:/Games/mc/Cheat/versions/1.20.1-Forge_47.4.28-test`。旧工程/旧整合包的通过记录未移用为新工程证据。

## 构建

已执行 `Build.ps1 -JavaHome <JDK17> -GradleHome <Gradle8.8> -Offline`，退出0：

- CoreTest：56项检查。
- PluginValidationTest：14项；PluginStepTest：send-once/verify/终态/超时。
- LocalServerTest：真实回环HTTP鉴权、JSON请求/响应、Origin拒绝。
- ObservContractTest：44项；ObservPagesTest：4项。
- UiSafetyTest：31项；UiSettingsTest：当前配置实例修改/保存回归。
- PluginStandaloneTest：真实manager/helpers + 临时隔离替身，manager12项、reload及provider失败回滚等通过。它不是运行中的模组协议测试。
- Python bridge：13项unittest通过。

没有把这些不同粒度检查混加成一个“总测试数”。Windows符号链接fixture因缺创建权限跳过；不计通过。Forge旧API编译警告、Gradle8.8弃用提示、Baritone自身Mixin缺minVersion警告保留，未宣称零警告或Gradle9兼容。

## 已观察场景

- **主菜单/世界/UI**：主菜单MCP可读状态、控件、截图、模组、路径；原生创建、保存、加入`MCPMCP Verified`测试世界；暂停期间仍可通过MCP恢复；创造栏搜索标签、聚焦和文本输入读回；LAN published=true（端口25575），未进行外部玩家连接。
- **玩家/环境**：生命、饥饿、经验、装备、坐标、朝向、天气时间、脚下方块/光照、背包和NBT；区域方块粗详、实体粗详；客户端缺失结构信息诚实unknown，单人显式server票据返回区块/原版seed公式史莱姆结果及结构starts。
- **移动/动作**：Baritone实际到达；最终回归取消后停止；视角和持续潜行读回；泥土挖空再放木板；纯发包长按金苹果2→1并有实际效果；马匹上马、骑乘前进、下马有坐标读回（骑乘正向测试早于最终审查修复，最终输入/取消另有回归）。
- **合成/箱子**：无JEI/REI，玩家2×2和工作台3×3原版配方封包填料取物；源材料减少/产物增加。箱子部分拿放；初始打开保持同菜单，初始关闭由任务打开并恢复关闭。目标容量冲突拒绝。新实现不再以客户端预测点击当服务器结果。
- **工作方块**：最终JAR实际烧铁、酿粗制药水；铁砧重命名、砂轮除魔、有效地图扩展、切石产物；织布图案NBT、锻造下界合金剑、附魔击退I。每次读取实际菜单槽位后规划，产物/数量/NBT读回，临时菜单关闭。
- **背包/装备**：头盔穿脱；副手盾牌SWAP40；原版收纳袋通过真实菜单右键插入/取出圆石，检查Items NBT与数量。
- **聊天/图像**：发送后从接收缓存读到消息；本机DEBUG截图可见`[MCPMCP] player.look [packet]`；全图/400×250裁剪PNG及GUI/framebuffer坐标区分。
- **协议**：目录/构造器描述；已有连接捕获C2S选择封包；已核对short编码wire和构造器请求返回submitted，未把其当独立服务器持久性确认；S2C和任意Java类拒绝。
- **插件**：热重载非法语法/缺schema保留revision；合法revision可执行、旧revision拒绝；verify满足后成功；目标Baritone JAR实际hash不符停用；可信Java样例在主菜单冷加载并通过Hermes原生MCP调用Provider/动作。坏provider/注册回滚由隔离合同测试覆盖。测试Java插件收尾移出实例并关闭信任。
- **权限/恢复**：NORMAL拒绝显式指令、UI聊天slash和伪标normal的创模式库存包；PACKET拒绝UI/Baritone；SIMULATE拒绝raw packet。已完成A、恢复B、同ID重放A不会再次选择。取消搬运后圆石8回原槽、游标空、临时箱子闭；排队B取消不发送release破坏运行A；stop后惯性消退坐标稳定。

最终`all_regressions`输出八项通过：sneak、brewing_shape_hit、shield_swap、hold_schema、consume、cancel_container、queued_cancel、move_cancel。queued_cancel随后又补了“取消B不新增release封包”的强化断言并通过。

结构化场景矩阵在 `evidence/manifest.json`：28组已测、1项不适用、4组未测。这是场景分组统计，不是所有功能/API的逐项覆盖率。原始JSONL含开发失败、错误测试前提与预期拒绝，不应按error数量简单判最终失败；所有最终声明应对照报告和请求ID。

## 修复过程的重要回归

1. 创建世界的原生加载渲染导致Jobs重入，已用嵌套pump回归修复。
2. Gson编译/游戏runtime接口版本对齐为2.10。
3. 潜行被Input.tick重置；改用原生输入更新事件维持自己状态。
4. 酿造台/铁砧复合形状的聚合边界中点可能在空隙；改每AABB可见面命中。
5. 副手SWAP40原被拒绝；按ClickType校验修复。
6. hold.button schema错误与纯发包被原版提前release，均有实际消费失败→通过证据。
7. 声明插件降低权限、raw SIMULATE绕过、聊天UI slash/self-settings绕过；最终发送/语义提交双重约束。
8. queued cleanup、tick内cancel、cleanup递归；取得资源才清理，脱离任务再回调。
9. 容器归属、取消回填、count0/负数、步骤中途才检查、装备语义、预测误判；预检和真实同步/后置条件修复。
10. 插件完整预检/verify/ServiceLoader错误隔离、设置旧config捕获、recipes分页alias；合同测试与实际正向读回。

独立全分支审查及逐项处理见 `review-resolution.md`。并非以“编译成功”代替审查或游戏验收。

## 限制与未测

- 未在第三方多人服务器或正版账号认证场景完成端到端验证；当前测试是离线单人实例。正常启动器的日常认证不由测试脚本替代。
- 未完成从零生存到末影龙通关；交付的是可供AI使用的平台，非已经证实能自主完成所有游戏目标。
- 任意模组/机器/全部hash尚无通用兼容承诺；RS、精妙背包的新平台插件本轮未交付，旧工程相应实测不证明本平台支持。已有扩展接口与样例供逐版本适配。
- Fabric/NeoForge和其他Minecraft版本仅架构预留，无对应已验收JAR。
- 未做长期稳定性/弱网/帧时间性能压测，不能把有界扫描或短场景成功写成所有整合包绝不卡顿的保证。
- 原版制箭台无菜单，不适用。结构数据只限已加载starts、不是全世界定位；史莱姆是原版seed公式，模组可改规则。
- 服务器不提供通用菜单请求nonce；同类型未知模组异步开菜单不能完美证明因果，手动干预尽量拒绝并保留恢复错误。
- Windows所有junction/重解析点/并发路径替换未完成平台穷举验证；MCP文件能力受限不等于对独立终端/文件工具的OS强隔离。可信Java同JVM没有文件沙箱。
- Java/mod packet公共构造器仍是本地信任边界；只使用已审查适配，不把“已登记C2S”误认为构造器绝对无副作用。

## 收尾状态

实例只安装MCPMCP自身JAR，源码与安装JAR SHA256相同：`5c0348b0b491e4faba18197254a52b6de3ef858044c466ba7ee7f3952079fe3f`。

验收世界保留场地和测试物品，未恢复为测试前空世界；这是专用新建测试存档。保存前游标空、菜单关闭、移动停止、潜行释放。最终客户端停主菜单，配置`NORMAL + MIXED`、DEBUG=false、trustJavaPlugins=false，java_loaded=0；仅保留无害原版声明示例，测试provider/权限/hash fixtures备份在工程`.local/client-fixtures-backup`。

Hermes `mcpmcp` 已保存配置、激活18工具并通过原生工具实际读主菜单；旧minecraft_info未移除，仍指旧实例。
