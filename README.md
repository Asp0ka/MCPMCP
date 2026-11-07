# MCP Minecraft Plus（MCPMCP）

Minecraft 客户端自动化平台。首版目标 **Minecraft 1.20.1 / Forge 47.4.28 / Java 17**，版本 `0.1.2`。核心不依赖 JEI/REI，原版合成使用配方书和容器协议；Baritone 通过可选运行时接口提供寻路。

这是独立重写工程。协议、权限与任务位于 `core`，插件API位于 `plugin-api`，Minecraft类型与Forge实现位于 `versions/1.20.1-forge`，Python桥接位于 `bridge`。后续版本/加载器仍需实现各自适配层，当前JAR不能直接跨版本或直接用于Fabric/NeoForge。

## 使用

1. 向对应实例 `mods` 安装 `versions/1.20.1-forge/build/libs/mcpmcp-1.20.1-forge-0.1.2.jar`。按该游戏版本安装Forge；需要寻路时安装相容Baritone。无需JEI/REI。
2. 正常启动游戏。主菜单即启动本地MCPMCP服务；首次生成 `config/mcpmcp.json`，默认 `NORMAL + MIXED`、DEBUG关闭、Java插件不信任。
3. 用Python 3.10+运行：

   ```text
   python bridge/mcp_server.py --config "<实例目录>/config/mcpmcp.json"
   ```

   这是stdio MCP进程，不是交互式命令行。MCP客户端将此命令配置为服务，工作路径可任意；脚本和配置建议使用绝对路径。凭据由桥接直接读取，不能复制token到参数或聊天。
4. 将 `skills/mcpmcp/SKILL.md` 安装到AI客户端的技能目录。先调用 `get_status`、`list_capabilities` 和 `describe_action`，随后执行并读回。

本机已配置的Hermes服务名为 `mcpmcp`，指向专用测试实例。旧 `minecraft_info` 仍保留并指向旧实例，不能混用。专用实例是 `D:/Games/mc/Cheat/versions/1.20.1-Forge_47.4.28-test`；`Install.ps1` 的目录名保护只允许该测试实例。

## 能力

- 主菜单、暂停与游戏内观测；屏幕控件、原生鼠标/键盘/文本、全屏或区域PNG截图；本地存档创建/加入、服务器连接入口、离开世界、开LAN。
- 玩家/背包/装备、环境/区域方块实体、聊天、模组和路径、同步原版配方。扫描有预算和分页，未知数据保留unknown。
- Baritone有界移动/停止；视角、潜行、交互、挖掘、放置、长按、骑乘输入；聊天和显式指令。
- 玩家2×2/工作台3×3合成；槽位移动；熔炉、酿造、铁砧、砂轮、制图、切石等工作菜单的有界步骤；装备和收纳袋操作。
- 容器状态保持：已有菜单复用，临时打开的菜单结束后关闭。游标保护、任务取消恢复、手动菜单冲突拒绝，未恢复情况通过status报告。
- 19个MCP工具与动态动作目录；声明插件热重载，可选可信Java适配器冷加载，recipes等观测Provider扩展。
- 已知PLAY封包目录/描述、现有连接上的有界元信息捕获，以及受权限约束的构造器/已核对wire发送。

完整可调用参数以运行中的 `describe_action` 为准。`submitted` 不代表服务器接受；容器步骤等待同步、任务状态与独立读回共同构成验收。实测范围与限制见 [docs/acceptance.md](docs/acceptance.md)。

## 原生聊天输入与运行上下文

先get_context()（或observe(kind="context")）读取mode、transport、native_chat_allowed、server.cheats_enabled与player.permission_level。主菜单返回unknown；单人服务器首读refresh_pending时重读，读取observed_at_ms确认快照时间。远程服务器的全局作弊设置不公开时是unknown，不能用玩家OP权限反推作弊开关。

使用execute_action(action="chat.submit", arguments={"text":完整单行文本}, action_id=唯一ID)提交AI自行构造的文本；不加、不删、不固定任何前缀。例如普通消息、/time query daytime、#mine 16 minecraft:iron_ore、.help均由当前客户端原生处理链和已装模组接管。仅校验非空单行及256字符上限；Minecraft本身仍会按正常聊天规则归一化空格，不能承诺逐字节发到服务器。

- MCPMCP不按文本前缀分类或根据mode封锁chat.submit的内容，游戏和模组自行接受或拒绝；此能力受input执行方式约束，PACKET拒绝，SIMULATE/MIXED可用。mode与服务器权限查询供AI按用户任务自行决策，开放输入不是自动授权作弊、改配方、改客户端文件或修改其他模组设置。
- 先确认已在世界、无打开界面；有界面返回close_existing_screen_first，不覆盖玩家正在编辑的聊天或容器。仅submitted表示进入原生提交链，不证明服务器执行、模组接管或采集完成。读get_chat_messages和结构化效果验证；未知前缀可能变成普通公开聊天，不能假设任意模组已安装或前缀固定。
- Baritone支持所需批量采集命令时，让AI用该客户端实际前缀和帮助自行组合指令，避免逐矿石“扫描+move.to+player.dig”；精确坐标拆除仍可用player.dig。采集数量语义按实际模组帮助核对，独立读库存增量，不把命令受理当产物成功。
- 原生提交后启动的Baritone/Meteor等后台流程不属于chat.submit这个短任务；get_job或cancel_job仅描述提交，不代表后台已停止。按模组帮助通过同一入口发送停止命令并验证，例如已核实默认前缀时#stop；不得依赖MCP stop能通用停止任意模组流程。
- chat.send和command是保留的旧直发兼容接口，有原有权限/前缀限制，不能替代原生聊天模组命令。优先chat.submit，原生ui.key的旧权限保护仍保留，权限设置页也不自动化。

## 原生聊天输入与运行上下文

`chat.submit` 接受AI编写的完整单行文本，不补前缀、不按用途分类；普通消息、`/命令`、`#Baritone`、`.`模组命令走客户端原生提交链。`get_context` 只读返回当前 mode、transport、服务器作弊开关和玩家命令权限，远程未公开值返回 unknown。详见 [0.1.2 更新记录](docs/native-chat-0.1.2.md)。

## 插件设置与语言

Forge模组列表 → MCP Minecraft Plus → 配置 → 插件列表，可滚动查看插件对应模组是否安装、目标/已装版本、版本与JAR指纹是否一致、加载状态。插件manifest可选 `target_version`；旧插件未声明版本时显示“未指定”，仍沿用hash兼容规则。

对已安装但版本/指纹不符的插件可点“强制加载”并确认，也可“取消强制”。选择保存到本模组配置，绑定当前声明与目标模组文件，更新后失效。缺少模组、平台/API不符、无效声明不会因此启用；权限与Java信任仍生效，Java适配器变更需重启。

设置、插件列表与确认框支持English (US)和简体中文，跟随Minecraft语言选项，无独立语言开关；其它语言回退英文。验收与截图见 [0.1.1 更新记录](docs/plugin-settings-0.1.1.md)。

## 模式和配置

| 配置 | 值 |
|---|---|
| `mode` | `NORMAL`, `HACK`, `CHEAT`, `HACK_CHEAT` |
| `transport` | `PACKET`, `SIMULATE`, `MIXED` |
| `debug` | 本机客户端聊天调试，限速，不输出完整载荷 |
| `trustJavaPlugins` | 默认false；本地明确开启后重启，加载已审查Java插件 |
| `port` | 默认5007；修改需要重启 |
| `scanBudget` | 有界观测预算，实际约束64..8192 |
| `token` | 自动生成，仅本地配置保留 |

模式、执行方式、DEBUG可由用户在Forge模组设置中调整；配置每秒重读，非法配置保留上一版。MCP自动化不能操作控制自身权限的设置界面。PACKET拒绝UI模拟与Baritone；SIMULATE拒绝原始packet API；没有静默降级。NORMAL的正常交互有距离/可视等检查，CHEAT并不替代服务器OP权限。

## 文件保护

MCP公开的文件操作仅允许 `MCPMCP-Plugin` 内 `.json/.md/.txt` 文本，不提供一般客户端文件编辑，不允许写二进制适配器。路径规范化、真实路径和符号链接检查防止普通越界。未经用户同意，不应通过其他工具修改整合包配方、配置、脚本或其他模组。

这限制的是MCPMCP能力，**无法禁止另有终端/文件权限的AI使用其他工具**。可信Java代码运行在同一JVM，也不是强沙箱；需要程序级隔离所有AI文件访问时，须在操作系统/执行宿主另外限制文件权限。Windows junction/重解析点的所有变体未完成平台验证。

## 构建和验证

Windows，JDK17、Python3.10+。首次在线构建下载Gradle与Forge依赖，离线构建要求缓存完备：

```powershell
$env:JAVA_HOME = '<JDK17>'
.\Build.ps1 -JavaHome '<JDK17>'
# 使用已有Gradle和已缓存依赖；GRADLE_USER_HOME必须指向实际缓存目录
$env:GRADLE_USER_HOME = '<已有Gradle缓存>'
.\Build.ps1 -JavaHome '<JDK17>' -GradleHome '<Gradle8.8>' -Offline
```

`Build.ps1` 执行Gradle build与Python unittest。Gradle check包括核心合同、插件验证/verify、HTTP回环、观测分页、UI权限/持有输入/设置，以及隔离测试替身下的真实PluginManager合同。测试替身仅在scratch中编译，不进入交付JAR；不能把这些测试视为实际模组兼容验收。

`-Offline`仅跳过ForgeGradle自身的在线证书连通性探测，不用于在线下载或关闭下载校验。Gradle8.8现有弃用提示仍存在，未宣称支持Gradle9。

```text
python scripts/live_acceptance.py --scenario all_regressions
```

实测脚本只允许专用测试实例，要求已进入验收世界并允许测试指令；会修改测试场地与物品，不用于日常存档。原始证据见 `docs/evidence`。`scripts/launch_test.py` 是专用离线单人验收启动器，使用既有库，不下载，不含正版账户凭据；日常与多人使用正常启动器。

## 打包

构建与提交后执行 `python scripts/package.py`，生成 `dist/MCPMCP-0.1.2-1.20.1-forge.zip`，包含JAR、stdio桥接、技能、文档、插件示例及对应提交的sources.zip；校验包完整性和SHA256，不带游戏配置、凭据或存档。

## 开发

- [版本实现与迁移](docs/architecture.md)
- [插件格式与Java Provider](docs/plugins.md)
- [MCP调用与观测合同](docs/api.md)
- [验收结果与限制](docs/acceptance.md)
- [AI操作技能](skills/mcpmcp/SKILL.md)

插件格式v1使用 `MCPMCP-Plugin/<mod-id>-<目标JAR短hash>/`，详情见插件文档。原版制箭台没有菜单；任意模组、整合包、第三方服务器和从头通关不属于已经全部验证的承诺。
