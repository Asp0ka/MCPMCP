# Ars / Iron's / Traveloptics Forge 1.20.1 adapters

三个独立、精确版本固定的 MCPMCP Java provider，分发入口为 `distribution/<mod-id>-<sha256前32位>/`，每目录有 `plugin.json`、`GUIDE.md` 和 `adapter.jar`。每 JAR 只声明一个 ServiceLoader provider，不打包 core/plugin-api 或模组。

## 构建

在本目录使用 Bash：

```bash
JAVA_HOME='D:/Games/mc/mc-info-mcp/tooling/jdk17/jdk-17.0.21+9' GRADLE_USER_HOME='D:/Games/mc/mc-info-mcp/.gradle-user-home' 'D:/Games/mc/mc-info-mcp/tooling/gradle-8.8/bin/gradle' --offline -Dnet.minecraftforge.gradle.check.certs=false clean stageDistribution --console=plain
python verify_targets.py
```

仅因使用已核对的本地离线 Forge 缓存关闭 ForgeGradle 在线证书探测；在线下载不应关验证。`stageDistribution` 依赖 check、纯Java合同/输入状态测试、三个 reobf 任务、真实成品 ServiceLoader 测试和 `verify_native_input.py`。后者执行实际目标 JAR 的 ClientInputEvents/ExtendedKeyMapping/CastPacket/QuickCastPacket，加离线宿主替身截获提交，验证一个包、后续 tick 不重发；不执行服务端逻辑。staging 位于本 adapter 的 `stage/MCPMCP-Plugin/`，不安装到游戏。API签名/hash及 build/distribution/stage 逐字节一致性检查见 verify_targets.py 和 verification.json，目标JAR信息见 targets.json，字节码证据见 evidence/。

铁咒/Traveloptics 原生施法会独立检查 Forge 实际加载的 Iron’s `1.20.1-3.16.3` 与 targets.json 中完整 SHA-256；在读取选择或任何原生输入前 fail closed，缺模组、版本/hash 不符、元数据/JAR 读取失败均不提交。成功按冷加载适配器缓存于当前 JVM，不依赖另一个可选 provider。`verify_native_input.py` 分别只加载 Iron’s 与 Traveloptics 成品，覆盖六类拒绝、玩家输入保留、失败后恢复和成功缓存。`verify_targets.py` 将测试绑定成品 hash，并刷新 verification.json / evidence/native-delivery.json；`stageDistribution` 收尾自动执行 verifyDistribution 调用该脚本，父任务也可单独再次运行。

生产代码只 compileOnly core/plugin-api 0.1.2，通过固定符号反射隔离可选模组；不接受调用者提供类名/方法名。铁咒原生输入路径只读固定私有输入标志（KeyMapping isDown/clickCount、ExtendedKeyMapping wasDown/canBeConsumed），以保留玩家 held/pending 输入；仅这组字段开启反射读取，不写私有字段，不修改资源/capability。施法对专属映射做一个实例脉冲，直接调用单模组公开 onClientTick，交由正常 CastPacket/QuickCastPacket 服务端链处理；不广播全局键或事件。Minecraft 调用通过 Forge official mappings 编译并 reobf。目标模组签名经 javap 核对。

未启动游戏或安装进实例，离线结果不能证明实际施法、网络服务端效果或魔法工作台全自动工作流。功能、调用、取消与边界见每目录 GUIDE。
