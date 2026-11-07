# MCPMCP FTB Quests 独立适配器

插件 `mcpmcp.ftbquests`，SPI `dev.mcpmcp.ftbquests.FtbQuestsProvider`，API 1。

## 精确目标

Minecraft 1.20.1 / Forge / Java 17。实际运行目标 Forge 47.4.21；离线构建使用缓存 Forge 47.4.28 official mappings。FTB API/语义以 evidence 中实际 JAR 的 javap 为准。

| mod_id | 完整版本 | SHA-256 |
|---|---|---|
| ftbquests | 2001.4.22 | 9ea9d159a78a2724cfe0457b024680fdb52a2126eff9cfa5280a30578f647a68 |
| ftblibrary | 2001.2.13 | 41810281b65336265cae2c88412022d02f2f0a3f28c66a06173d86f98acf14a7 |
| ftbteams | 2001.3.2 | b1cb4d82ea9f137a87e12a7aaf3329619294b56f7d70337dfd557a76e10b6e07 |
| architectury | 9.2.14 | 218b471d0b8a1f6cda14cfc1beb9eeb0df54304500acc6c5613d9b88ec65d9af |

完整构建路径在 targets.json。读取与提交前，通过 Forge ModList 的实际 ModInfo.owningFile.filePath 核对四个依赖完整版本及流式 SHA-256。只核对运行时选择文件；FTB Library / Architectury 的相同副本不必删除。错版本、同版本改 JAR、缺失或读取失败均拒绝，manifest 的人工强制兼容不能绕过。

## 数据范围与原生可见规则

只读 ClientQuestFile.INSTANCE、selfTeamData 和当前玩家。原生 isPlayerOnTeam 验证归属；不创建 TeamData，不读 ServerQuestFile、其它队伍或存档。所有查询无 GUI 操作。未同步返回 available:false / state:unknown / complete:false，不伪造空任务书。

始终按非编辑模式调用 Chapter.isVisible、ChapterGroup.isVisible、Quest.isVisible；玩家有编辑权限也不放开隐藏内容。hideDetailsUntilStartable 隐藏的副标题/正文/子任务/依赖/奖励返回 null + unknown_reason；hideTextUntilComplete 单独隐藏正文。原生 hideProgressNumbers 隐藏的 progress/max_progress 返回 null + 原因（包括默认单数量的 checkmark/item），完成状态仍可读。图片/富文本/命令链接只转换纯文字，不加载图片、不点击链接。

## 合同、查询与字段

先 observe(kind=ftbquests, arguments={})，读取 data.contracts（全部闭合 schema）、data.actions（权限/transport）、data.runtime_pins。未知输入参数拒绝。核心也注册四个 Java action handler；manifest actions=[] 表示由 SPI 注册。

| kind | 参数 | 返回路径 |
|---|---|---|
| ftbquests.book | {} | data.title/totals/groups[].counts/chapters[].counts/native_batch/pending_operation |
| ftbquests.chapters | query, group_id, limit, cursor | data.rows[].id/group_id/title/subtitle/counts |
| ftbquests.quests | query, chapter_id, status, claimable, limit, cursor | data.rows[] 完整任务内容投影 |
| ftbquests.quest | 必填 quest_id | data.quest，与列表同结构；隐藏/不存在统一拒绝 |
| ftbquests.rewards | query, chapter_id, quest_id, claimed, claimable, exclude_from_claim_all, limit, cursor | data.rows[] 当前可见全部奖励，包括未完成/已领/choice |
| ftbquests.claimable | 仅 limit, cursor | data.rows[] 全域预览，data.native_batch，最终页 data.preview_token |
| ftbquests.receipt | 必填 request_id | 当前保留 receipt；尝试 claimed 读回，失败附 readback_unavailable，从不重发 |

共有 data.session/version/player_uuid/team_uuid/observed_at_ms/server_state。所有 FTB 对象 ID（quest/chapter/group/task/reward/table/dependency）严格为 16 位小写十六进制字符串，例如 0020000000000001、ffffffffffffffff；输入先验证再 parseUnsignedLong，禁止 JSON number。玩家/团队使用 UUID。task progress/max_progress 是精确十进制字符串，不是浮点数。

任务列表与详情字段：

- id/chapter_id/title/subtitle/description；文字为 {text,truncated,total_utf16_units}。标题最多 1024 UTF-16 units、副标题 8192、正文 32768；截断标记不能忽略。搜索只查返回可见文字，不查隐藏正文或截断尾部。
- completed/started/can_start/status/relative_progress/completion_count/optional/repeatable/claimable/details_visible/description_visible。status 是 completed、in_progress、available、locked。统计只汇总可见任务；隐藏详情奖励状态记为 claimability_unknown_quests。
- tasks[].id/type/title/completed/progress/max_progress/progress_hidden/progress_unknown_reason/consumes_resources/definition_hash。可显式确认checkmark阅读任务；不支持资源任务提交或任意进度修改。
- dependencies[].id/visible/completed/started/unknown_reason，以及 dependencies_complete/min_required_dependencies；隐藏依赖不返回标题、完成或进度。
- rewards[] 同 rewards 观察器，含全部奖励预览。

奖励字段：id/type/title/quest_id/chapter_id/quest_title/team_reward/claimed/native_can_claim/claim_type/quest_completed/reward_blocked/can_claim/exclude_from_claim_all/choice/ordinary_route_safe/choice_route_safe/reward_hash/definition_hash/effects/effects_complete。can_claim 结合原生 getClaimType.canClaim、任务完成、未领取、非 team locked/奖励 blocked；客户端提示不是服务端确认。未知第三方 Reward 子类可读，但动作拒绝。

表奖励含 table_id/table_hash/table_candidates_total/table_candidates_visible 和 choice_candidates[] 或 table_candidates[]；候选有 choice_index/weight/id/type/title/definition_hash/effects。普通表隐藏 tooltip 时不返回候选内容。Choice 原生选择界面显示全部候选，因此返回有序全候选。whole-table hash 绑定完整递归定义、顺序、权重、所有候选 ID/配置及表 ID，包含未选择候选的完整尾部。循环/超预算表明确报错，不给部分安全结果。

## 分页、会话与预算

默认 limit 8，最大 32，每页行 JSON 总预算 1 MiB；字节预算会提前结束页面。沿 next_cursor 续读，不能把少于 limit 或空页当完成。cursor 绑定 kind、完整原查询（包括是否显式传 limit）、session、version；每页重读客户端投影比较版本。变化返回 stale_cursor 与 complete:false，重新开始。cursor 不能自行构造。

session 最长 30 秒，绑定实际 world/player/connection/ClientQuestFile/selfTeamData 对象、player/team UUID、内容/进度版本。切世界/队伍、断线、文件同步替换、进度/表变化或 TTL 均失效。cursor/preview 最长 30 秒，续页不延长原期限。上限：256 cursor、128 preview token、512 当前 provider 生命周期内 receipt；16384 quests、65536 rewards、4096 chapters、1024 groups；每任务 task/reward/dependency 各 128；表每层 128 候选、深度 8、单根 512 节点；定义 hash 预算 256 KiB。超出明确报错。

book 分组/章节统计数组各最多 128，附 groups_total/groups_truncated/chapters_total/chapters_truncated；更多章节使用 chapters 分页和 group_id。单行超过 1 MiB 返回 row_response_budget，不静默截断列表。

## 单领与显式选择

四个动作都是 normal / packet；dry_run 默认 true。NORMAL + PACKET/MIXED 可用，SIMULATE 不适用。只构造核实过的 FTB C2S message，调用 BaseC2SMessage.sendToServer；不调用真实 Reward.claim、TeamData setters/markcomplete，不编辑、reset、give/creative 或运行任意指令。另有 ftbquests.task.check，仅允许调用方 acknowledge_read:true 的可见、可开始、未完成且不消耗资源的 checkmark；有效auto奖励不全为DISABLED时拒绝该快捷入口，避免悄悄触发完成后的自动奖励；传 task_id/expected_task_hash/session/request_id，发送一次原生 SubmitTaskMessage，绝不写进度或提交其它任务类型。读取正文并确认后才能调用；后续领取仍独立。

从同次 quests/quest/rewards 观察取 session、目标 id、reward_hash。ftbquests.reward.claim 参数：

```json
{
  "session": "<同次观察 UUID>",
  "request_id": "<本次操作 UUID，与核心 action_id 相同>",
  "reward_id": "0020000000000001",
  "expected_reward_hash": "<完整 64 位 hash>"
}
```

先 dry_run 成功，再沿用前置身份、为真正提交生成新的 request_id/action_id，并显式 dry_run:false。dry-run 与真实提交是两个不同操作；核心 Jobs 将 action_id 绑定完整参数，同一 action_id 从 dry_run:true 改 false 会 action_id_conflict。dry_run 不发包且不占适配器提交 receipt。真正提交以后，重试/查回执必须保留该提交 UUID，绝不换 UUID 重发。普通领取限定审查过的目标内置普通 Reward 类；Choice 必须 choose。一次 ClaimRewardMessage(id,true)。提交前两次完整快照检查身份与可领性；变化后重新观察。

ftbquests.reward.choose 另必须显式 choice_index、expected_table_hash；索引是 choice_candidates 原生有序下标，绝不默认第一项。要求完整表有界、所有子奖励路由审查通过、父任务完成、父奖励未领、非锁定/屏蔽，且玩家仍在本队。一次 ClaimChoiceRewardMessage(id,index)。

目标服务端 Choice handler 在 completed/type/index 检查后先 child.claim，再 TeamData.claimReward(parent)，缺少父 unclaimed 前置检查。本地未领校验、表身份、同 UUID 去重及 pending 防重复；原生 wire 没有 expected hash/nonce，无法消除其它客户端同时领取或服务器变化的竞态。不可声称服务端幂等，未知结果不换 UUID 重发。

## 原生一键领取的实际范围

ftbquests.rewards.claim_all 发送一次无参数 ClaimAllRewardsMessage()。服务端遍历全任务，对已完成任务的奖励仅按 !getExcludeFromClaimAll 过滤，再 TeamData.claimReward；后者另检查 team locked、blocked、claimed。没有章节/类型/reward ID 列表过滤，不能宣称“仅当前章”。

必须完整遍历无过滤 ftbquests.claimable：rows 是 completed + unclaimed 可见奖励预览，也包含 native excluded 和 blocked 行。data.native_batch 返回 scope=all_not_excluded、claimable_hash、有效 claimable_count、preview_rows/excluded_count/choice_count/blocked_or_locked_count、effects/safe_to_submit/native_scope_blockers/server_races_possible。count 只计 can_claim && !exclude。

目标内置 ChoiceReward.getExcludeFromClaimAll 恒为 true，列入排除预览，不自动选择。如意外包含未排除 Choice、未知路由或隐藏候选，则拒绝整个批次，绝不“跳过后仍发全域 packet”。不为预览输出隐藏对象内容。

仅最终完整页、范围安全且 count 在 1..512 时发 preview_token。审阅所有行与效果后：

```json
{
  "session": "<完整预览 session>",
  "request_id": "<本次操作 UUID，与 action_id 相同>",
  "preview_token": "<最终页 UUID>",
  "scope": "all_not_excluded",
  "expected_claimable_hash": "<native_batch.claimable_hash>",
  "expected_claimable_count": 3,
  "max_rewards": 3
}
```

示例数字必须替换实际精确 count，max_rewards 是显式预算。先 dry_run，再以新的 request_id/action_id 提交 dry_run:false；提交后该 UUID 永远不更换重试。摘要绑定 session/version/完整行定义/排除/效果。服务器 wire 不带 hash/count/budget，客户端无法限制收包前新增候选；保留该竞态，不伪称服务端预算。

一键领取可能触发预配置 command、自定义处理器、阶段/成就、随机/全表物品和其它服务器奖励副作用。effects 递归汇总分类；用户“一键领取”是审阅该全域集合后领取原生奖励的授权。预配置 command reward 不等于任意指令工具，但有实际效果；插件不会因此修改任务、奖励或权限。

## 回执、pending 与结果验证

每次调用的 request_id 必须与 execute_action 外层 action_id 使用同一 UUID；dry-run 和实际提交各有自己的操作 UUID。API1 handler 无法读取外层 action_id，调用方负责保持一致。保留该 ID 查 receipt；JSON 属性顺序不影响同 payload 去重。同 UUID 改 payload 拒绝。receipt 不按时间淘汰，最多 512 后拒绝新提交。

submitted 只表示 native send；异常保留 delivery_unknown，两者都不是成功。提交后只允许读取，pending 不按 tick 或超时重发，直到所有绑定 reward IDs 的客户端 claimed 读回。切上下文为 context_lost_delivery_unresolved，继续阻止新 UUID；回到原 player/team 且 claimed 可读回才解除。没有远程 clear/reset pending。

ftbquests.receipt {request_id} 在断线时仍返回 retained receipt/readback_unavailable。claimed_read_back 与 claimed_observed:true 仅证明同步领取标记；server_confirmed:false/effects_confirmed:false 保留，不能证明背包到账/command 效果或强因果。按奖励类型独立核对库存/XP/阶段。重复任务可能立即重置 claimed，看不到时保守留在 pending，不猜成功重发。

去重只在当前 provider/JVM 内存生命周期有效，重启不能作为重试策略。未决、receipt 满、重复任务瞬时重置或同步缺失，须人工核对服务器/原生任务书。

## 离线构建与交付

父工程 plugin-api/core 的现有 0.1.2 JAR 是 compileOnly/test 依赖；不修改父脚本、不下载 JUnit。本工程的 Java main 测试挂入 check。Git Bash 在本工程目录：

```bash
export JAVA_HOME='D:/Games/mc/mc-info-mcp/tooling/jdk17/jdk-17.0.21+9'
export GRADLE_USER_HOME='D:/Games/mc/mc-info-mcp/.gradle-user-home'
'D:/Games/mc/mc-info-mcp/tooling/gradle-8.8/bin/gradle.bat' --offline -Dnet.minecraftforge.gradle.check.certs=false --console=plain clean build
```

可用 -PftbquestsJar / -PftblibraryJar / -PftbteamsJar / -ParchitecturyJar 指定字节相同目标。先校验四个源 JAR hash，再在本工程 .gradle 建编译别名。

顺序：compile/testClasses → adapterContractTest/check；jar → reobfJar；stageDistribution 依赖 check 与 reobf；verifyDistribution 校验后 build 才完成。分发严格为 distribution/{plugin.json,GUIDE.md,adapter.jar}，验证与重混淆输出字节相同、文档精确一致、单一 SPI、不夹包 MC/FTB/Architectury/API/core/test/shim 类。

测试使用真实 MC NBT、目标 packet.write、原生可见规则及隔离任务对象投影；fake access 检查提交计数/竞态/schema/paging/session/去重/依赖拒绝。未启动 MC、未发送真实网络；evidence 不能代替实机验收。

维护者另行审阅三件产物，在游戏关闭时部署 MCPMCP-Plugin/ftbquests-9ea9d159a78a2724cfe0457b024680fd/ 并本地信任 Java、重启。当前交付不安装、不改整合包或游戏文件。

待实机验收：实际 Forge 47.4.21 SPI/ModList 文件选择；全部 observer 无 GUI 与隐藏规则；普通/已领/locked/Choice 拒绝或领取；dry_run 零包；完整全域 batch 排除与副作用；submitted 后 claimed 和独立库存/效果；换队伍/断线/重复 UUID/pending。
