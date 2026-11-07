# FTB Quests 插件与验收（0.1.2）

独立插件 `mcpmcp.ftbquests` 适配神秘启旅实际 **FTB Quests2001.4.22**，MC1.20.1 / Forge47.4.21 / Java17。核心不依赖FTB；新增后实例共14插件、发布共15构建变体（JEI两版互斥）。四个运行依赖按实际ModList文件完整版本/SHA-256绑定：FTB Quests、FTB Library2001.2.13、FTB Teams2001.3.2、Architectury9.2.14。

## 查询与操作

先 `observe(kind="ftbquests", arguments={})` 查看当前闭合合同。

| 入口 | 内容 |
|---|---|
| ftbquests.book | 任务书、章节组、总体/章节统计、同步上下文、pending |
| ftbquests.chapters | 章节/组、搜索和分页 |
| ftbquests.quests | 按章节、可领取/完成状态搜索任务；包含任务内容与进度 |
| ftbquests.quest | 指定任务完整正文、依赖、子任务、奖励及选择候选 |
| ftbquests.rewards | 可见奖励、类型、完成/领取状态、效果与完整定义指纹 |
| ftbquests.claimable | 无过滤全域预览；完整读完才给一次性范围凭据 |
| ftbquests.receipt | 同操作UUID的保留回执与领取标志读回；不重发 |
| ftbquests.reward.claim | 普通单领奖励 |
| ftbquests.reward.choose | 显式选择候选；必须index和完整表hash |
| ftbquests.rewards.claim_all | 一次原生全域一键领取 |
| ftbquests.task.check | 已阅读确认的可见、可开始、未完成checkmark任务；不提交资源任务 |

任务/章节/奖励等64位ID始终是 **16位小写十六进制字符串**，不能用JSON number或丢前导零。只读当前玩家同步的selfTeamData，不访问其它队伍或ServerQuestFile；保留原生隐藏任务/正文/奖励/数字规则。文字截断附标志，隐藏进度数字是null/unknown，不是零。

ItemReward提供 `item.item_id/configured_count/stack_count/random_bonus/only_one/stack_hash`，不是从标题猜物品；实际到账仍需库存读回。Choice和表奖励提供有序候选及完整表hash，不能默认选第一项。

全部动作 **normal/packet**、dry_run默认true；NORMAL+PACKET可正常领取，SIMULATE拒绝。session绑定世界/玩家/连接/任务文件/队伍对象及内容/进度版本，分页有界且随变化失效。request_id与外层action_id使用同UUID；dry-run和真正提交各用不同UUID，实际提交重试保持原UUID。

`task.check` 还需 `acknowledge_read:true`、task_id和expected_task_hash；任务必须为原生checkmark、不消耗资源、可开始，且该任务所有有效auto奖励均DISABLED。只发送原生SubmitTaskMessage，不能编辑任务、任意设进度、reset、跳依赖或消费其它任务资源。完成后的领奖独立执行。

## 一键领取范围

原生ClaimAllRewardsMessage无章节/类型/ID列表参数，作用域固定 **all_not_excluded**。插件必须完整预览全部可领取行及递归效果，核对scope/hash/count/max_rewards后单次发送；不能给全域包加不起作用的章节筛选。选择型奖励由原生排除，不自动选择。隐藏可领、未知路由或意外未排除Choice使整个batch拒绝。

预配置command/custom/阶段/成就/随机表可能有服务器副作用，预览明确列出；正常领取并不等于提供任意指令工具。客户端摘要和预算不在原生wire内，不能消除收包前服务器候选变化或队友并发。Choice服务端先发子奖励再标父状态的限制也保留；本地未领校验、pending与去重不能变成服务端幂等承诺。

## 实机验收

仅在独立存档 `MCPMCP Plugin Acceptance` 进行；没有改任务定义/配置或用进度命令完成前提。信息任务先读取正文，再显式正常勾选。最初自绘FTB页的通用UI点击未产生进度，已保留失败记录；随后新增受控checkmark入口并对最终二进制重新测试。

最终 **30项实机检查通过**，完整前后快照/任务ID见 `docs/evidence/ftbquests-0.1.2/acceptance.json`，请求响应执行时追加于events.jsonl，摘要记录hash。

- 任务书、章节、分页列表、正文/依赖/进度/奖励查询，未打开GUI；读到2359个可见任务（仅该测试时刻，不是固定数量）。
- 常用键位信息任务完成后普通单领面包3，领取标志false→true、库存+3；重放同UUID和新UUID重复领奖无新增。
- 完整全域预览共9项：两项阅读任务面包奖励和旧模组验收自然完成的普通/随机物品奖励。逐项审查后一次ClaimAll，9个领取标志全部变true，库存精确增加：面包6、魔源宝石8、金锭8、宝石粉24、微量魔力晶体1、发光水晶碎片16、随机宝石1。没有执行command/custom奖励。
- 显式选择“暂定居所”候选index2，得到 `prefab:item_house` ×1；未使用物品生成建筑。越界索引、Choice误用普通领取、已领取重复选择均拒绝。
- 未完成任务、错类型ID（chapter作为reward）、JSON数字ID、旧session/换世界、修改分页条件、SIMULATE均被拒绝；菜单保持，空游标、无恢复任务。
- 保存退出重进后库存与领取标志一致。合法奖励保留在独立测试存档，不删物品、不reset已领状态。

175项离线公共合同/原生编码器/可见规则/任务投影/依赖与回执检查通过，核心完整Build和36项Python测试通过；测试替身没有被当成游戏效果证据。原13插件二进制保持此前已验收hash。

## 安装与发布

退出目标游戏后，在项目根运行：

```text
python scripts/plugin_release.py --project ftbquests-1.20.1-forge
python scripts/install_plugins.py
python scripts/install_plugins.py --apply
```

实际安装目录：`MCPMCP-Plugin/ftbquests-9ea9d159a78a2724cfe0457b024680fd/`。可信Java需本地授权并重启，manifest/fullhash/平台/依赖检查仍保留。完整GUIDE随插件分发。

发布脚本串接旧12插件、Curios与FTB各自的独立验收hash；任一旧二进制改变即拒绝拼接旧证据。提交后运行 `python scripts/package.py` 更新主包、14插件包和JEI二选一包，均带精确提交和hash。

最终恢复主菜单、NORMAL/MIXED、空游标、无恢复任务，保留实例原Java信任设置。真实多人个人/团队奖励、XP正向、重复兑换冷却、预配置命令效果、所有第三方奖励类型尚未验证；不对未测情况作覆盖承诺。
