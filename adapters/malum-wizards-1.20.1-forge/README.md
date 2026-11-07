# Malum / Wizards Reborn 独立 MCPMCP adapters

两个独立 ForgeGradle 子项目共享只读查询/正常输入基础代码，不互相依赖、不嵌入模组/core/API。仅各自 JAR 内声明自己的 ServiceLoader provider。

- `distribution/malum/`：Malum 1.20.1-1.6.7，manifest / adapter.jar / GUIDE。
- `distribution/wizards_reborn/`：Wizards Reborn 1.20.1-0.2.9，同样独立分发。
- `evidence/targets.json`：目标与只读依赖的真实路径、完整 hash、manifest、mods.toml。
- `evidence/*signatures*` / `*bytecode*`：真实目标/依赖 javap。
- `evidence/build-final-readable.log`：最后构建结果（原始 mixed-codepage 日志另保留）。
- `evidence/artifact-verification.json`：最终 JAR/hash/成员链接/独立性检查。

## 可重复构建（Windows git-bash）

在本目录执行，需共享仓库已构建的 `plugin-api/build/libs/plugin-api-0.1.2.jar` 和 `core/build/libs/core-0.1.2.jar`：

```bash
export JAVA_HOME='D:/Games/mc/mc-info-mcp/tooling/jdk17/jdk-17.0.21+9'
export GRADLE_USER_HOME='D:/Games/mc/mc-info-mcp/.gradle-user-home'
"$JAVA_HOME/bin/java.exe" -cp 'D:/Games/mc/mc-info-mcp/tooling/gradle-8.8/lib/gradle-launcher-8.8.jar' org.gradle.launcher.GradleMain --offline --no-daemon -Dnet.minecraftforge.gradle.check.certs=false check stageDistribution --console=plain
python verify_artifacts.py
```

构建使用 Java 17 / Gradle 8.8 / ForgeGradle 6.0.54 / Forge 1.20.1-47.4.28 official mappings。缓存完整时离线执行；`check.certs=false` 只用于这个明确的离线缓存流程，在线下载不要照抄。可用 `-PmodsDir=...` 指定只读目标模组目录。

`check` 实际运行两个独立 Java main contract tests，覆盖发现/注册隔离、schema/参数范围/拒绝、exact input 前提、cursor 查询与 world 身份/过期/容量、交互 preflight 各分支和单 provider；不启动 Minecraft。`verify_artifacts.py` 解析构建 JAR 和真实目标/依赖 class constant pool，逐项链接已引用成员，验证 reobf native use、无 raw C2S、未嵌入模组/core/API/测试类、Java 17 class 版本、目标完整 hash 和 service 唯一性。它是构建期证据，不是 JVM/游戏加载测试。

根 observer `malum` / `wizards_reborn` 自带所有合同；GUIDE 说明实时配方、已加载局部工作方块资源/物品/进度、真实菜单映射、受限原生右键，以及 unknown/服务端确认边界。

生产代码唯一有副作用的入口是正常 `gameMode.useItemOn` 单次右键；没有直接库存写入、resource setter、ritual/craft、脚本、反射或文件写。构建检查程序可在本项目 evidence 生成报告，不能把构建期 I/O 当插件运行时 I/O。Java 插件仍是用户本地信任的同 JVM 代码，无文件强沙箱。

此任务没有安装/修改客户端、启动游戏或提交 Git。游戏加载、真实同步、正向/拒绝/取消恢复及服务端结果由父任务安装验收。
