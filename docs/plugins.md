# 插件API v1

## 目录与manifest

实例根 `MCPMCP-Plugin/<mod-id>-<目标JAR短SHA256>/`，建议短hash为完整SHA256前32个十六进制字符。非minecraft目标manifest必须声明完整64字符target_sha256；运行时核对模组JAR，缺模组、hash或平台不符时停用并显示原因。目录命名是分发约定，兼容性以完整hash和平台检查为准。

```json
{
  "api": 1,
  "id": "example.vanilla",
  "mod_id": "minecraft",
  "minecraft": "1.20.1",
  "loader": "forge",
  "actions": [{
    "name": "example.select_hotbar",
    "description": "演示快捷栏选择封包，需独立读回",
    "permission": "normal",
    "transport": "packet",
    "input_schema": {"type":"object","properties":{"slot":{"type":"integer","minimum":0,"maximum":8}},"required":["slot"],"additionalProperties":false},
    "packet": {"packet_class":"net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket","mode":"constructor","signature":["int"],"values":[{"param":"slot"}]}
  }]
}
```

原版minecraft目标可省hash。模组目标增加target_sha256。actions可另存actions.json（数组）；GUIDE.md说明前提、调用、槽位来源、验证和恢复。示例见examples/MCPMCP-Plugin。

## 绑定与权限

packet字段：constructor使用静态packet_class、signature和values；wire使用fields。值可用单键 `{"param":"参数名"}`、`{"context":"menu_id"}`、menu_state_id、player_uuid。schema字段和packet结构封闭，未知字段拒绝；绑定在实际执行时解析，不复用旧菜单ID。

原版需已注册PLAY C2S；Forge消息需目录中明确C2S通道，可指定channel。不再支持任意channel_holder/channel_field反射。预检读取public构造器类型，不构造消息。复杂不受支持的构造参数使用可信版本适配，不绕开类型约束。

原始packet.send要求HACK；声明动作的permission只能增加要求，最终发送再次检查核心最低权限。普通选择包可normal，未核对的移动/交互/模组消息最低hack，创模式库存/聊天命令最低cheat；命令方块/结构编辑、CustomPayload和书编辑等载荷禁止。传输固定packet，在SIMULATE拒绝。

这并不保证任何第三方模组C2S构造器无本地副作用；只为经审查版本建立适配，避免加载不可信模组或Java代码。

## 条件与任务

preconditions和verify是条件数组，条件kind：position、menu、inventory、block。position使用x/y/z/tolerance；menu使用class；inventory使用item_id/min_count；block使用x/y/z/block_id。可绑定参数。初始条件在发送前检查，verify在发送一次后逐tick轮询，满足才observed_success，超时/取消由Jobs负责。无verify只返回submitted。

调用前读取get_plugins的revision，可传额外plugin_revision。参数校验剥离此元字段，发送前及verify期间固定revision；不匹配拒绝。reload_plugins取消未完成任务；全部声明验证完成后交换，失败保留上一revision。

## Java冷插件与Provider

adapter.jar通过 `META-INF/services/dev.mcpmcp.api.McpmcpPlugin` 声明实现类。接口：

```java
public interface McpmcpPlugin {
    String id();
    void register(PluginContext context) throws Exception;
}
```

PluginContext支持action(name,schema,transport,permission,handler)、observer(kind,priority,handler)、execute、observe。例如observer("recipes",100,handler)可优先替换原版配方查询；Provider必须诚实返回来源、完整度、分页和错误。避免Provider再次调用同kind导致递归；不能把查询支持等同于机器合成支持。

默认trustJavaPlugins=false，用户本地审查并开启后重启。text MCP不能写jar/class，热重载不加载新Java。provider配置/构造/注册错误被隔离，失败注册不残留半个action/observer；管理器只冷加载一次。示例源见examples/java-provider，不绑定游戏类。

Java同JVM没有安全沙箱，manifest不授予可靠OS隔离。不能用这个机制向用户宣称“插件代码无法修改文件”。

## 测试

python scripts/PluginStandaloneTest.py 运行真实manager/helper与临时隔离测试替身，覆盖非法声明回退、预算、send once/verify、revision、坏provider继续、注册回滚；不代表任意模组已经兼容。实际整合包每个目标hash另做正向效果、拒绝、取消、恢复和版本迁移测试。
