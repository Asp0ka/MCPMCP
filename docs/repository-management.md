# GitHub 仓库管理

仓库：Asp0ka/MCPMCP，初始设为私有。main 为默认分支，feature/1.20.1-forge 保留已交付开发历史。

## 已配置自动化

- Push、PR、手动触发和每周一 02:17 UTC 运行 CI。Python 3.10 在 Ubuntu 22.04 / Windows 2022，Python 3.12 在最新 Ubuntu / Windows 测试；Java 17 / Gradle 8.8 在 Windows 构建、check 和 reobf。CI 构建 JAR 保留 14 天。
- Actions 固定完整 commit SHA，Dependabot 每周提出 Actions 更新 PR，最多 3 个；不自动合并，不自动升级 Minecraft、Forge 或适配器目标版本。
- Issue / PR 模板、CODEOWNERS、贡献与安全说明。
- CI 默认只读权限，PR 不使用 pull_request_target、不接入游戏凭据或本机客户端文件。

## 管理边界

CI 和 Dependabot 由 GitHub 执行；这不代表有一个常驻 AI 自动修复所有问题。后续开发可由当前助手执行分支、测试、提交、PR 和发布工作。未经明确指令不公开仓库、强推/改写历史、删除分支或合并未经验证的依赖更新。

完整游戏验收、正式发布包打包仍使用受授权的本机环境。GitHub CI 不替代主菜单、容器、库存、权限或世界状态的实机读回。

## 账号方案限制

当前私有仓库的 main 分支保护设置被 GitHub 返回 403：需要升级 GitHub Pro 或将仓库公开。仓库继续保持私有，不自动付费或公开。因此必需检查、禁止强推和 PR 审批目前是协作约定，不能声称服务器已强制执行。

Dependabot 漏洞告警和安全更新已启用；更新只提出 PR，不自动合并。Actions 固定 SHA 并使用只读工作流 token，禁止工作流代审批 PR。

Gradle Wrapper 8.8 使用官方 downloads.gradle.org 直连并校验完整 SHA-256；GitHub 上构建仍保持 TLS 和 wrapper 校验。首次 CI 的 Python 3.10 / Gradle 分发下载遇到 504，已避免不必要的新镜像运行时下载。Windows 回滚测试将临时根目录解析为真实长路径后构造夹具，安装器的路径越界保护保持。
