# 0.1.2 原生聊天输入与运行上下文

## 变更

- 新增 `chat.submit`：AI提交自己编写的完整单行文本，MCP不添加、删除或固定任何前缀。普通聊天、`/命令`、`#Baritone`、`.`模组命令均由客户端原生聊天提交链处理，已安装客户端模组自行接管；仅校验非空、单行和256字符。
- 新增只读 `get_context`（及 `observe(kind="context")`）：返回MCPMCP `mode`、`transport`、`native_chat_allowed`、服务器作弊开关与玩家命令权限。单人集成服务器通过服务器线程异步取值，首读pending、重读得到快照；远程服务器不公开的全局作弊设置返回`unknown`，不以OP等级反推。
- `chat.send`、`command`保留为旧直发兼容接口，不改变原有权限限制；原生`ui.key`仍保留旧权限保护，权限设置页不可自动化。
- `get_chat_messages`增补原生聊天栏已显示消息采集，包括Baritone/Meteor等客户端模组直接写入聊天栏的反馈；只读显示列表，不读取发送历史，按消息对象身份去重，窗口100条。

## 实际验证（专用实例 1.20.1/Forge 47.4.28）

- `get_context`主菜单返回mode/transport、`cheats_enabled=unknown`、`permission_level=unknown`，不伪造。
- 无作弊生存世界：`cheats_enabled=false`、`permission_level=0`；普通消息正常出现在聊天；`/time query daytime`被服务器拒绝并显示错误；`#help mine`由Baritone接管，完整帮助文本被读回；`#goto`实际移动坐标改变，`#stop`返回`ok canceled`；`.mcpmcp_native_final_test`原样进入服务器聊天。
- 开作弊世界（配置切换CHEAT、HACK_CHEAT）：`cheats_enabled=true`、`permission_level=4`，原始`/time query daytime`返回“当前时间为17/18”。
- 权限上下文与本地模式一致，测试后恢复`NORMAL`；没有为测试改写存档规则以外的客户端模组文件。
- 构建：`ChatInputTest` 25、`DisplayedMessagesTest` 7、Core 56、兼容矩阵12、UI 31、观测44+4、Python22项通过；JAR与安装副本SHA256一致。

## 边界

- `chat.submit`的`submitted`只证明进入原生提交并交送，不证明服务器执行、模组接管或采集完成；后台Baritone/Meteor流程不受本模组`cancel_job`/`stop`控制，需按该模组帮助发送其停止命令。
- 未知前缀可能作为普通公开聊天发送；不能假设某模组存在、前缀固定或命令语义相同。
- 服务端命令权限仍由服务器决定；`get_context`不授予任何权限。
- 原生输入受执行方式限制：PACKET拒绝，SIMULATE/MIXED可用。
