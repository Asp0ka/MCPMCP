# FTB插件收尾审查

基点49e7aca，审查范围：新增FTB Quests工程、release来源链、测试和文档。按源码、真实FTB JAR字节码、175项最终离线检查及30项最终二进制实机检查核对；子任务初始161检查和未安装报告不是最后状态。

## Standards

- normal/packet与dry-run、只读无GUI、canonical16 hex ID、四依赖实际ModList文件fullhash保持。无任务编辑/reset、直接Reward.claim/TeamData setter、其它队伍/服务端文件访问。
- 同session绑定整个同步任务文件/当前玩家队伍/内容与进度version；全表hash保留未选候选、顺序、权重、配置尾部；分页变化/超期拒绝。pending和回执阻止未知交送或Choice再次提交；原生服务器并发限制如实列明。
- nativeClaimAll范围全局all_not_excluded。只有完整未过滤预览才发token；排除/隐藏/未知类型不会被静默丢弃后仍发全域包。预配置命令和custom效果保持原生分类，未将其伪装为普通物品领取。
- 父审查发现标题不足以标识物品、普通奖励未完全遵循原生隐藏规则，已补ItemReward ID/count/hash/随机与only_one字段和blocked/INVISIBLE过滤，并补真实对象投影回归。
- 自绘UI点击未能建立任务前提，补独立task.check，仅显式阅读确认、可见且原生可开始的checkmark、无资源消耗、有效auto奖励全禁用时发送一次SubmitTaskMessage；前后进度读回独立，绝非完成命令或直接改映射。缺省dry-run、重复/pending/错误类型/auto副作用拒绝均纳入测试。
- 构建来源明确、分发与安装字节相同，旧13份二进制不变且分别沿用旧基线和Curios验收，FTB独立匹配最终安装hash。无第三方依赖/测试夹入生产JAR。

## Spec

任务书、章节、任务列表、完整正文/依赖/进度、奖励状态和单领/选择/一键均已实现。实机普通单领面包+3；全域一键9项逐flag确认，七种物品增量匹配；显式index2领取小屋物品但不放置。重复/未完成/错类型/数字ID/旧session/换世界/分页变更/SIMULATE均拒绝，退出重进后状态持久。NORMAL+PACKET无作弊路径通过。

未发现本轮尚未解决的阻塞项。保留边界：多人团队/XP正向/重复任务冷却/command实际效果未测；所有第三方奖励类型无覆盖承诺。ClaimChoice原生先子奖励后父flag，适配器不宣称服务器幂等；原生一键没有服务端hash/count/allowlist；客户端同步标志也不能证明效果，必须读实际库存或对应世界状态。

源代码和发布脚本检查已完成；最终包仍由提交后的package.py输出与递归hash审计确认，不能仅依据本文宣称生成成功。
