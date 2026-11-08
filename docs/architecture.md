# 架构与迁移

## 边界

`core` 使用Java17/Gson，包含Policy、Jobs、Schema、扫描边界、容器预检、插件声明校验与verify任务。`plugin-api` 仅暴露JSON DTO、McpmcpPlugin与PluginContext，不引用Minecraft/Forge类型。

`versions/1.20.1-forge` 实现早期生命周期、客户端线程调度、观测、UI、原版协议、容器、Baritone及Forge插件消息目录。Forge版本层内置MCP Streamable HTTP服务，`core` 内嵌协议处理器与工具catalog；自动启动依次绑定本机配置的端口范围并发布不含凭据的运行时发现文件。仅绑定127.0.0.1并校验token、Host、Origin、请求大小及有界并发。`bridge` 保留为可选标准库stdio兼容transport，通过发现文件读取本次会话实际端口后转发到旧 `/call` 接口。

## 执行

HTTP工作线程只入有界队列，游戏请求在client/render回调排空。无世界或暂停时render仍泵UI任务；重入保护防止原生创建世界加载循环再次执行同一任务。Jobs一次执行一个有界任务，action_id去重，保留有限结果；世界epoch与策略变更取消旧任务。tick/cleanup内取消不会覆盖终态，cleanup先脱离任务以防递归。

Baritone寻路是持续任务，到达坐标才成功，取消/超时停止自己发起的寻路。持有输入记录归属；queued任务取消不能释放另一个任务。PACKET物品长按拦截本任务期间原版因未按模拟键而提前发送的release，结束先撤销归属再发release；不模拟鼠标键。

## 容器

关闭时发送交互打开，校验目标的预期菜单类型及对应Screen实例；手动use会清除待归属记录。已打开对象必须是先前归属的同一个菜单，不能靠陈旧坐标把新菜单冒认为原目标。非标准模组菜单先显式打开并读取，再不传target复用，或由插件适配。

原始槽位包不调用本地menu.clicked预测。发送当前ID/stateID/空预测差异，由服务器计算并同步结果；步骤等待真实物品变化后继续。before/after不把stateID或炉火进度变化当业务变化，包含物品NBT。输入槽、容量、步骤字段、按钮和count在副作用前预检。

取消在自己菜单内回填自己拾取的游标到原槽，等待同步，关闭自己临时打开的菜单；迟到菜单仅在归属仍有效时恢复。恢复有五秒边界，冲突不点击替换菜单，status给recovery_error。网络协议未提供通用打开请求nonce，因此同类型、无归属来源的任意模组异步菜单无法强证明服务器因果；保持可见限制，不能宣称完美防并发。

## 插件与文件

声明先完整验证再原子换revision，前置/后置条件有界。原始封包权限由核心给最低要求，声明permission不能下调。仅运行时已登记C2S、明确方向的通道可发，wire仅限已注册原版PLAY C2S。

可选Java使用ServiceLoader冷加载；每provider暂存注册，失败回滚并继续其他provider，默认不信任。Java与Minecraft同JVM，没有强文件沙箱。注册的消息类也不自动证明任意public构造器无副作用；模组适配需要审查和版本绑定。

## 移植

新增 `versions/<mc>-<loader>`，替换客户端生命周期/线程、注册表、菜单/槽位、封包codec、截图/UI/配置集成、模组目录和Baritone适配。保持core/plugin-api JSON合同，跑纯合同和版本场景矩阵。Forge SimpleChannel反射目录不能直接用于Fabric/NeoForge，适配层需重新实现；官方映射方法、封包id、菜单槽数不作为跨版本稳定ABI。

不复制版本依赖进core，不承诺同一个adapter.jar跨所有游戏/加载器。hash标识目标JAR，不代替行为测试。Recipe Provider仅替换查询，复杂机器合成仍需菜单/封包适配。
