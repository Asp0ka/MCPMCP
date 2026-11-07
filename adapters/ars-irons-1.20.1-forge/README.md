# Ars / Iron's / Traveloptics Forge 1.20.1 adapters

三个独立、精确版本固定的 MCPMCP Java provider，分发入口为 `distribution/<mod-id>-<sha256前32位>/`，每目录有 `plugin.json`、`GUIDE.md` 和 `adapter.jar`。每 JAR 只声明一个 ServiceLoader provider，不打包 core/plugin-api 或模组。

## 构建

在本目录使用 Bash：

```bash
JAVA_HOME='D:/Games/mc/mc-info-mcp/tooling/jdk17/jdk-17.0.21+9' GRADLE_USER_HOME='D:/Games/mc/mc-info-mcp/.gradle-user-home' 'D:/Games/mc/mc-info-mcp/tooling/gradle-8.8/bin/gradle' --offline -Dnet.minecraftforge.gradle.check.certs=false clean distribution --console=plain
python verify_targets.py
```

仅因使用已核对的本地离线 Forge 缓存关闭 ForgeGradle 在线证书探测；在线下载不应关验证。`distribution` 依赖 check、94项纯Java合同断言、三个 reobf 任务与真实成品 ServiceLoader 测试。API签名/hash检查见 verify_targets.py 和 verification.json，目标JAR信息见 targets.json，字节码证据见 evidence/。

生产代码只 compileOnly core/plugin-api 0.1.2，通过固定符号反射隔离可选模组；不接受调用者提供类名/方法名，也不 setAccessible。Minecraft 调用通过 Forge official mappings 编译并 reobf。目标模组签名经 javap 核对，运行缺API会明确报 target_api_unavailable。

未启动游戏或安装进实例，离线结果不能证明实际施法、网络服务端效果或魔法工作台全自动工作流。功能、调用、取消与边界见每目录 GUIDE。
