# 贡献与验证

当前支持 Minecraft 1.20.1、Java 17、Forge 47.4.21–47.x。

1. 从 main 创建 feature/、fix/ 或 ci/ 分支，提交小范围修改并发起 PR。
2. 运行 `python -m unittest discover -s tests -v`；完整构建运行 `gradle --no-daemon build`（Gradle 8.8、Java 17）。Windows 也可用 Build.ps1。
3. 核心、plugin-api、版本适配层分离；不要把某一实例路径或第三方模组依赖搬进核心。
4. 适配器严格绑定目标版本和完整 JAR hash。CI 不下载整合包、不启动游戏，也不能替代真实模组/世界验收。
5. CI 产物仅是构建结果；正式发布需对应提交、最终 JAR、验收证据和校验值一致。
6. 不提交本地 config、token、账号、存档、.local、构建缓存或第三方模组。Java 插件的权限声明不是文件沙箱。

使用 Conventional Commits，如 feat:、fix:、ci:、docs:。依赖更新需审查；MC、Forge、Gradle 和目标模组基线不自动升级。
