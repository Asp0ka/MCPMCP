# 最终审查处理状态

审查基线为 cb5cfeb 之后的未提交工作树。报告中的问题按实际代码和原始事件重新核实，不以审查子任务的完成状态代替修复或实机验证。

| 项目 | 核实与处理 | 当前验证边界 |
|---|---|---|
| Traveloptics 的 Iron 精确依赖 | 确认依赖范围宽于已核对字节码，已补施法前运行时版本/full SHA-256保护 | 离线拒绝与最终二进制oakskin/astral_sense效果重测通过 |
| 包装玩家槽的 NBT 身份 | 确认同ID/count不同NBT可能通过原checkItem，已补完整ItemStack.save源物品指纹保护 | 最终原生/packet旧NBT指纹均拒绝，刷新指纹搬运与烧炼通过 |
| 漏装/空计划成功 | 安装改为明确13变体目录清单，按12个预期ID择一精确目标；缺manifest、构建、GUIDE或版本/hash匹配均报错 | Python选择器/缺项回归通过；最终实际清单仍待全构建 |
| 冷加载保护 | 分离/等号gameDir、相对路径/cwd识别；读取不明进程或argument-file拒绝，复制前再次核验 | Python进程形式回归通过；目标游戏已正常退出 |
| 路径/产物/安装完整性 | 拒绝符号链接/junction/reparse与越界；验证SPI注释/唯一provider/对应类/适配器类包；全量暂存复查、备份、失败恢复、保留支持文件 | 路径/SPI/暂存漂移/复制失败回滚回归通过；不是跨进程事务，复制时不要启动游戏 |
| 来源链 | 明确当前工程reobf输出；源码指纹、manifest、GUIDE、build/install receipt与验收hash逐一绑定 | 新脚本要求fresh clean/build及final-delta，未拿任意旧同hash JAR作构建证据 |
| 分发与变体 | 主包接入12插件ZIP和JEI二选一ZIP，提交后打包，临时ZIP验证后替换；补JEI GUIDE及仓库skill引用 | 最终产物必须实际运行打包器核验，当前未宣布完成 |
| Wizard摘要 | 原始events第647行记录Wissen200、空材料、进度0，已补到scenarios及supporting-readbacks | 客户端延迟同步终态；独立服务器确认仍unknown |
| Ars法力摘要 | 原场景只存夜视，文档收窄，不把未入摘要的扣法力计为独立通过项 | 保留夜视实测 |
| 归属回归摘要 | 提炼既有events响应和行号补摘要，不只保留空before/after的checks | 新NBT合同已完成独立增量实测 |
| 铁咒/Travel未测 | 审查读取时较旧；后续有oakskin与astral_sense正向效果/冷却记录 | 历史二进制已通过，新依赖保护后的最终二进制仍需重测 |

原29场景记录保留：28通过，1条修复前JEI诊断断言已标resolved_by后续通过场景。不同粒度测试不合算覆盖率。全量13变体clean/build已通过，精确12插件安装读回完成，最终16项增量实测见final-delta.json。未改逻辑的6份目标class与基线逐字节一致。提交前凭据扫描无匹配；提交后已实际执行package.py，递归检查主包、源码快照和两个可选插件ZIP，目标集合、SPI与hash均通过。无客户端配置、存档、缓存、日志目录或已知凭据。精确提交与最终包hash以发布包manifest及dist校验文件为准。
