# MCP Minecraft Plus 设计与交付契约

用户已授权自行规划、操作专用测试客户端并持续交付，不再等待中间审批。项目名 MCP Minecraft Plus，缩写 MCPMCP，Git 仓库，首个版本 1.20.1-forge / Forge47.4.28。参考旧工程仅用于理解协议和已有实测缺陷；新实现独立，不依赖 JEI/REI，Baritone 为移动实现。

## 绑定需求
- 当前实例根目录 MCPMCP-Plugin/<mod-id>-<target-jar-short-sha256>/ 存声明、GUIDE.md 和可选 adapter.jar。完整 hash、版本、能力、权限、热/冷加载状态可查询。
- 原版配方管理器查询，原版配方书封包或槽位点击合成。兼容菜单语义：玩家/工作台、炉、酿造、铁砧、砂轮、制图、切石、锻造、织布、附魔、箱/桶；原版制箭台无菜单必须明确说明。
- 容器有界任务状态机：原目标已开则保持；原关闭则打开/等同步/操作/验证/关闭；别的容器已开则拒绝冲突；游标非空不隐式覆盖。取消、超时、切世界、手动切菜单均释放输入并恢复自己打开的菜单。
- Baritone移动、视角、潜行、左右交互及长按、装备与收纳袋、聊天读取与发送、文本输入、屏幕与控件、截图、世界加入/创建/UI/LAN、骑乘/下马及骑乘输入。
- 区域方块和实体粗/详查询；所有扫描有预算、分页、加载范围、来源和截断；客户端缺失的结构/史莱姆区块/完整服务端NBT明确unknown，单人可显式通过Server线程增强读取，不通过跨线程直接访问。
- 自身生命经验饥饿护甲氧气、背包装备粗详、坐标朝向天气时间光照脚下方块水岩浆；模组和路径查询。
- 封包目录/描述/观察/有界捕获/声明式执行/前置条件/结果验证/批量。优先原生封包，wire仅按已核对布局；连接重建后上下文无效。
- 模式 NORMAL/HACK/CHEAT/HACK_CHEAT × PACKET/SIMULATE/MIXED。命令权限与执行方式独立；正常行为检查距离可视、槽位、世界状态。Baritone不是纯发包，纯模式拒绝；UI也拒绝，非静默降级。
- HTTP仅127.0.0.1随机token拒绝Origin，stdio桥接标准MCP；主菜单阶段可用，不依赖世界tick。正常暂停也需处理UI请求。
- config与模组设置界面可切模式/执行方式/debug；游戏内debug仅本地消息，有速率限制。
- 禁止通过MCP工具直接改他模组、配方、脚本、存档；只允许自身配置/日志/缓存/插件目录。插件Java同JVM不能宣称强沙箱，默认需显式本地信任才能加载二进制；纯声明可热重载。
- JEI/REI未来只通过RecipeProvider接口替换或增强，核心工具不依赖其类型。
- 原始证据、场景矩阵、构建测试、实际专用客户端验收；不可得的客户端数据不伪造为通过。

## 模块与接口
protocol 提供 JSON contract；core 提供 ActionSpec/Policy/JobScheduler/ScanBounds/PluginManifest验证；plugin-api 提供 McpmcpPlugin 与 PluginContext（JSON DTO，不暴露Minecraft类型）；版本工程实现 GameRuntime、Observations、Actions、ContainerSessions、UiActions、PacketService；bridge 提供mcp_server.py stdio转发。所有动作统一 execute_action(name,args,action_id)，异步任务 get_job/cancel_job；读操作 observe(kind,args)，能力查询 describe/list。

## 实现裁定
新项目为独立隔离目录，使用 feature/1.20.1-forge 分支，无现有主分支需保护；不再建立冗余worktree。核心Gson作为JSON DTO，Java17，ForgeGradle6.0.54/Gradle8.8。直接使用官方映射编译，独立模块不引用Minecraft类。源文件由父代理统一集成，不让并行任务编辑同一文件。实际操作仅测试实例，不关闭原整合包客户端。给定授权覆盖测试实例启动/停止、设置和测试存档。凭据不得记录进工程或日志。
