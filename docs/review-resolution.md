# 独立审查处理记录

审查基于初始分支差异和并行实测，详见验收材料；本记录不将静态推测写成已发生事故。重要发现均在同一修复阶段处理，测试替身、纯合同和实际游戏证据明确区分。

| 审查项 | 处理与证据 |
|---|---|
| F01 声明降低封包权限 | PacketPolicy定义不可下调最低权限；实际normal创模式包拒绝回归通过 |
| F02 SIMULATE raw包 | packet.*固定packet，最终发送再次检查；HACK/SIMULATE实际拒绝 |
| F03 UI权限 | slash/点击命令链接/启用cheats需要CHEAT，自动化禁止MCP自设权限；UiSafety31和实际聊天slash拒绝 |
| F04 queued Held清理 | 未取得资源不清理；强化游戏回归取消B不新增release包，运行A保持running |
| F05 菜单归属 | Screen菜单对象+预期类型+手动use清除归属，移除陈旧全局坐标复用；显式open后带target复用实测；协议无nonce的边界保留 |
| F06 取消恢复 | 游标origin/物品身份/容量检查，回填后等同步、关闭自己菜单，五秒超时报错；实际取消8圆石回原槽且闭菜单 |
| F07 UI输入生命周期 | UiInputs仅拥有自己输入，tap finally释放、stop/切屏释放；下马输入有界；实际stop惯性后稳定 |
| F08 hold绕过正常reach | 复用实体距离/可视检查，expected_item持续检查；未知/遮挡不直接发normal攻击 |
| F09 预测误判 | 移除menu.clicked本地预测，空delta等待服务器物品同步，NBT纳入业务比较，忽略stateID/炉火进度；最终多工作方块产物读回 |
| F10 count0/负数 | ContainerContract副作用前拒绝，目标容量/可放预检；核心与实际菜单保持关闭回归 |
| F11 设置旧实例 | 每次点击取Runtime当前config并保存，标签读同publicView；UiSettingsTest替换配置实例回归 |
| F12 声明预检/verify | 封闭manifest/action/packet/schema/condition与预算，完整预检后换revision，verify send-once轮询；非法结构保留与实际verify通过 |
| F13 provider错误 | ServiceConfigurationError隔离，逐provider暂存注册/回滚，继续其它provider；独立fixture真实manager合同12项 |
| F14 recipe alias分页 | offset移除，cursor转发；Python13含cursor与旧offset拒绝 |

补充发现：Jobs tick内cancel、cleanup递归有RED→GREEN测试；batch子schema/steps副作用前校验；装备要求玩家菜单并验证目标装备；制箭台明确无菜单；结构starts内分页可用starts_offset继续；加载manifest/actions/adapter使用统一安全路径。

## 保留的范围限制

- 不宣称相同类型未知模组异步菜单具备通用服务端因果nonce。手动切换/无法归属时拒绝或报告恢复错误，仍需具体模组适配与竞争场景测试。
- Windows所有重解析点及并发路径替换未穷举；OS未授予symlink测试权限，因此该fixture跳过。
- Java插件与模组消息构造器是本地信任边界，没有同JVM强沙箱。默认不信任Java，文本工具不可安装二进制；此边界不会因manifest声明而消失。
- UI可调用普通模组回调，不能普遍拦截每个模组私有副作用；只对当前暴露的核心、原版语义和自身设置做可执行限制。
- Forge旧API/Gradle弃用、Baritone的minVersion警告保留，作为迁移维护项，不阻断当前目标版本运行。

没有把上述范围限制包装为完全消除的安全保证。所有受系统限制/未测范围在README和验收报告中保留。
