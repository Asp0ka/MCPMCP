# AI操作技能验证

交付技能：skills/mcpmcp/SKILL.md。技能编写前的实际操作基线中，制图测试因猜测快捷栏槽位而失败；读取当前菜单真实slot后成功。技能因此规定先读菜单/物品数据，不复用示例槽号，未知投递同ID先查，空页继续分页。

独立只读应用测试已完整读取技能和API文档，对五个条件给出满足的方案：case-1超时先同ID查；区分menu slot/container_slot/背包slot；next_cursor存在时不下空集合结论；PACKET不使用Baritone或静默切模式；未授权不改配方文件。测试还明确没有实际调用游戏、没有把方案通过当游戏成功。

这证明文档可用于该场景的操作判断，不代表任意模型永远遵守规则。硬限制仍由模式/文件能力/任务边界执行。技能结构检查通过，name/description/version齐全，正文无机器私有路径；测试与当前项目示例一致。

当前长会话的Hermes工具schema缓存可能仍显示旧get_recipes.offset；新启动stdio通过tools/list已核对为cursor。已有会话可用observe(kind=recipes, arguments={cursor:...})继续，刷新会话发现后使用便捷工具。此为宿主发现缓存，不修改游戏数据规避。
