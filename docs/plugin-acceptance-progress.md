# 模组插件实际验收进度

本轮仅使用新建的 `MCPMCP Plugin Acceptance` 存档，未进入用户已有世界。实例为神秘启旅 / Minecraft1.20.1 / Forge47.4.21。初始化命令只准备场地、输入材料与机器资源；目标产物均由被测交互/加工生成。完整请求响应执行时写入 `.local/plugin-acceptance-20261107-151018/events.jsonl`，场景结果写入同目录 `scenarios.json`。

## 已经真实读回

- RS：原生放置创造控制器使网络激活；合成取出后插入网络、single/half/stack/shift提取、单件/整叠插入、重复请求不重复提取；原木样板自动合成4木板，网络原木减少1、木板增加4；清空合成矩阵后材料守恒；错误物品与旧session拒绝。
- 精妙存储：17圆石与2原木存入、排序、关闭重开后数量保持；原生与封包快捷取回；归属修复后7花岗岩往返。
- 精妙背包：库存身份校验打开、搬运、安装真实合成升级、选择合成页、JEI填料、取出木板；修复后升级槽不误识别为玩家槽，背包自身槽仍锁定。
- 更多熔炉：3原铁经核心填料生成3铁锭；修正InvWrapper归属后，插件快捷填入2原铜与煤，真实加工成2铜锭并取回，空游标、保持同菜单，增益槽不误判。
- Botania：池初始100000魔力；玩家正常丢入1钻石后变为90000，玩家收回1魔力钻石。
- Avaritia：9×9真实菜单；实时配方显示整合包肉丸需要22材料，按实时配方放入后消耗全部输入，生成并取回1寰宇肉丸。
- Malum：祭坛原生单件放入/取回，客户端同步输入槽变化，错误维度拒绝。此场景不代表自动仪式。
- Wizards Reborn：插件把1奥秘粉尘投入Wissen祭坛，资源最终0→200，材料消耗且进度归零；错误维度拒绝。
- Ars：插件投入紫水晶碎片至灌注室，真实生成魔源宝石并取回；实际雕文ID为glyph_self/glyph_light，法术书Self→Light施法后新增夜视（该场景摘要未保存法力快照，不将法力扣减计为此场景独立通过项）；PACKET模式即时拒绝input施法。
- JEI：两版基础填料既有证据保留；旧版Quark泛型配方句柄先固定橡木再查询白桦，仍用2橡木台阶产出橡木木板。新增displayed_outputs与槽displayed后实测显示橡木当前焦点，outputs明确标注全部候选。
- 铁咒：保留 `options.txt` 原 `key_key.irons_spellbooks.spellbook_cast:key.keyboard.g` 冲突未改，插件改用单模组原生 `ClientInputEvents.onClientTick` 脉冲（`global_key_event=false`）。施放 oakskin 后新增 `irons_spellbooks:oakskin`（amplifier2、391ticks），900ticks冷却生效。
- Traveloptics：同一冲突条件下施放 astral_sense，新增 `traveloptics:astral_sense`（891ticks），1200ticks冷却生效，法力100→1。

## 本轮修复

1. 更多熔炉使用Forge原生SlotItemHandler/InvWrapper玩家槽。旧代码只比slot.container身份，误报只读；新代码核验wrapper与底层Inventory对象身份，保留未知升级、增益、锁槽及越界拒绝。
2. JEI句柄保存IFocusGroup，summary/detail/show/transfer沿用原焦点；补当前displayed_outputs，避免把泛型全部候选误作当前结果。
3. 插件错误上报回归：业务错误保留、低优先级成功回退、LinkageError转换可见错误、未知kind仍交给核心路径。Standalone PluginManagerTest通过25项。

## 纠正与边界

前一轮误将Botania/Avaritia的空未完成页当成RecipeManager没有配方。实际继续候选分页后可读Botania配方与78条Avaritia配方；文档和skill已纠正。

Quark详情outputs按JEI API返回全部可选变体，不能要求每项均为焦点；当前显示变体看displayed_outputs与slots[].displayed。旧错误断言及后续修复读回均保留在事件/场景记录里，不能机械把历史失败当最终未修复。

最终审查后的两项保护已安装实测：施法前精确核验 Iron’s 版本/full JAR SHA-256；quick_move 两路径必填 expected_stack_hash，覆盖完整 ItemStack.save（普通NBT、ForgeCaps及完整count）。同ID/count替换NBT后旧指纹在原生与封包路径均被拒绝，刷新后可正常搬运并烧出1金锭；树肤术、星界感知、存储往返、背包合同/原生路由、JEI焦点和PACKET拒绝已用最终安装hash重测。`final-delta.json` 的16项检查全部为true，并保留对应1.6MB实际请求响应。基线29场景与最终增量分开记录，不将历史测试冒充新二进制覆盖。

未修改的JEI旧版、RS、Botania、Avaritia、Malum和Wizard重建后JAR容器hash可能变化，但逐个Java class内容与基线字节一致，见 baseline-binary-comparison.json。

背包选中合成升级时，原生quick_move会优先送入升级合成格，不能承诺固定送往玩家或存储；按observer角色读回后再计划，定向恢复用container.move。星界感知首次法力78不足未生效，等待法力100后独立施法才计通过；失败/前提记录没有删除。

客户端已回主菜单，恢复NORMAL/MIXED，空游标且无恢复任务。仍待提交和最终包完整性核验；没有将其他等级、全部升级、全部法术、自动仪式或多人服推为已通过。

场景汇总为29项、28项通过；唯一失败项是修复前的 `jei_quark_focus_retention` 诊断记录（当时断言要求泛型配方的每个候选产物都等于焦点，与JEI API语义不符），已标记 `category=diagnostic_reporting_defect`、`resolved_by=jei_displayed_focus_and_transfer_fixed`，不计为当前缺陷。
