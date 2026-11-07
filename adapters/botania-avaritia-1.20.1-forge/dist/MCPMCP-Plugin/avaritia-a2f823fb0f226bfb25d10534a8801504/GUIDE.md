# Botania / Avaritia MCPMCP 适配器

Minecraft 1.20.1，Forge 47.4.28，MCPMCP/core/plugin-api 0.1.2，Java 17。两个 adapter.jar 独立安装，各一个 ServiceLoader provider。没有启动游戏；本 GUIDE 的游戏调用为待验收流程。

## 精确目标与安装

Botania Forge mod version 与 MANIFEST 均为 1.20.1-453-FORGE，SHA-256：222755726437ed9d908c58462b604c10d137c3756a4a228ab459dbbaf8bd4c19。

Avaritia Forge mod version 是 1.3.9.0；MANIFEST Implementation-Version 是 1.20.1-1.3.9.0-beta4，SHA-256：a2f823fb0f226bfb25d10534a880150403aca5ce6b593f93a5e8ee6a7fb47d51。plugin.json target_version 必须使用 Forge mod version。

元数据来自 D:/Games/mc/Cheat/versions/神秘启旅/mods 的真实 JAR，不按文件名推断。verify_targets.py 重新核对版本、完整哈希和反射成员；javap 证据在 evidence。

分发目录：dist/MCPMCP-Plugin/<mod-id>-<目标hash前32位>/，内含 plugin.json、GUIDE.md、adapter.jar。用户本地审查后复制到实例根 MCPMCP-Plugin、开启 trustJavaPlugins 并重启。本任务未安装、改实例、启动游戏或 commit。Java 同 JVM，没有操作系统文件沙箱。

get_plugins 核对兼容性、Java 冷加载与 revision。observe(kind=botania, arguments={}) 或 avaritia 返回全部 schema、固定 transport 动作和 unknown 语义。核心 capabilities 的固定 observations 列表不会自动枚举 Java kind，本 GUIDE 明确入口。坏参数返回 ok:false/problem，避免核心把顶层 error 当作 observer 回退。

## 配方查询

例：observe(kind=botania.recipes, arguments={query:mana, limit:8, scan_budget:256})；avaritia.recipes 可传 serializer=avaritia:shaped_table。也支持 recipe_id 精确筛选。返回 entries/scanned/complete/next_cursor；空页且 complete=false 继续。cursor 两分钟有效，绑定世界、配方对象快照和过滤，最多128个；续查保持参数。scan_budget 计算所有候选；不限制读取和排序完整客户端 RecipeManager 引用的成本。

选择目标 recipe ID namespace、实际 serializer namespace 或目标实现包，包含目标模组的原版 shaped 配方和其他 datapack namespace 的模组配方。只读客户端 RecipeManager；不覆盖核心 recipes、不写配方、不调用 assemble。显示输入/输出含数量和 NBT，替代物品最多64、材料最多256，超限标注。输入相关输出/余料/脚本转换并不保证由显示结果覆盖。

Botania：mana infusion 魔力与催化状态；petal apothecary reagent；runic altar 魔力与 reagent；terra plate 魔力；pure daisy 输入/输出方块与时间；orechid/ignem/marimorphosis 状态与基础权重；elven trade 多输出；brew 魔力。位置权重、容器相关药水输出等 unknown。

Avaritia：shaped_table 实际 width/height/tier/有序材料，支持9×9而不裁成3×3；shapeless_table/infinity_catalyst/eternal_singularity tier；compressor input_count/time_ticks；extreme_smithing template/base/additions。永恒奇点 getIngredients 会延迟修改材料列表，因此只读已存在 public inputs，注明可能不完整。未出现在客户端 RecipeManager 的动态/JEI规则不伪造；可读独立 jei 合同补充，查询支持不代表生产支持。

## 局部方块观测

observe(kind=botania.blocks 或 avaritia.blocks, arguments={x:100,y:64,z:100,radius:1,limit:8,scan_budget:64})。中心距玩家不超过64格且高度合法，radius=0..8，limit=1..64，scan_budget=1..4096；block_id 精确过滤。空气、非匹配项均消耗候选预算。只读取已加载区块，不强加载。unloaded_or_outside_positions 区分未读位置；complete 仅为遍历完成，all_positions_loaded 仅针对本页。分页间方块会变化，不是原子区域快照。

返回 block_id/state/坐标/implementation，以及来自实际收到的服务端方块实体 payload：魔力池 mana/capacity/outputting/can_accept/can_spare；扩散器 mana/rotation/can_shoot/库存；花药台 fluid（客户端方块状态）与库存；符文祭坛 mana/target_mana/比例；酿造台和泰拉凝聚板 mana；压缩机 MaterialCount/MaterialStack/Progress/Ejecting/库存；收集器 progress/库存。其他字段 unknown。满魔力不等于活石或法杖完成；扩散器绑定和配置相关周期不伪造。

被动监听区块 tag、方块实体更新、菜单 data；不调用 save/getUpdateTag/writePacketNBT，部分 Botania 写包函数会修改标志。未收到字段、类型错误、缺 Items 列表返回 status:unknown；只有明确服务端0才为已知0，明确空 Items 与缺失不同。observed_at_ms 为本地接收时间，最后快照可能落后，不是动作效果确认。最多缓存4096目标，切连接/登录/重生/区块卸载替换/方块更新时失效。首次监听可能错过先前数据，等待正常更新或自然重入区块，不强加载补读。

## 菜单与安全操作

observe(kind=avaritia.menu, arguments={}) 读取实际 menu_id、菜单槽号/container_slot、玩家库存标志、物品/count/NBT/游标。TierCraftMenu 用已核对的原生构造器布局和实时槽数共同确认网格：9×9 是结果槽0、row-major输入1..81；另区分3/5/7 tier。非目标菜单 applicable=false。压缩机/收集器进度仅在收到 ContainerData[0] 后可知，注明协议 signed_short；不读取默认0。槽位可能含客户端预测，不独立证明服务端确认。

动作：botania.interact.packet/client，avaritia.interact.packet/client。transport 固定 packet/client，permission=normal；PACKET不可client、SIMULATE不可packet，不切换模式。参数 x/y/z、expected_block、expected_item 必填；hand=main/off、face六方向可选。先 describe_action，带最新 plugin_revision。例：action=botania.interact.packet，arguments={x:100,y:64,z:100,expected_block:botania:rune_altar,expected_item:botania:twig_wand,face:up}。例子不证明目标存在。

必须无界面、玩家菜单、空游标、已加载目标、方块/手持物一致，再同步调用核心0.1.2 Actions.one(block.interact)，保留核心距离/形状可视/世界边界/当前模式/服务端权限规则，不构造模组包、不创建子任务或长按。只 submitted，独立读回目标、材料和菜单。正常右键可能显式打开菜单，不替换已打开界面。

已打开菜单搬运/取出：读 menu 和核心 container，describe_action(container.move/container.sequence)，根据本次槽位调用核心 action，不传 target。由核心持有菜单身份、空游标、同步和取消回填，结束保持原菜单。特殊菜单原关闭时不伪造自动开关会话；显式 interact 打开、读回确认后操作，最后按需 container.close。

没有自动填9×9、自动取完配方、魔力生产、材料投掷、法杖绑定状态机、多方块完整验证或机器自动化。TierCraftMenu 不支持原版 recipe-book 的 container.craft。取消遵循核心 action_id/job/cancel/recovery；本适配器单次交互没有额外后台任务。

## 构建

Git Bash 设置 JAVA_HOME=D:/Games/mc/mc-info-mcp/tooling/jdk17/jdk-17.0.21+9，GRADLE_USER_HOME=D:/Games/mc/mc-info-mcp/.gradle-user-home。适配器目录执行 python verify_targets.py，再执行 D:/Games/mc/mc-info-mcp/tooling/gradle-8.8/bin/gradle --offline -Dnet.minecraftforge.gradle.check.certs=false --console=plain clean check build，最后 python package_adapters.py。路径含空格时加引号。certs=false 仅用于已缓存离线 ForgeGradle 探测，不用于在线下载。

Forge47.4.28、official1.20.1、compileOnly core/plugin-api0.1.2；目标方法通过真实 javap 审核的反射读取。各 jar 均执行 reobfJar，不携带核心/API/Minecraft 类。check 挂接 JavaExec 合同测试；产物脚本检查单 provider、哈希版本、SRG重混淆。详情见 TESTS.md、evidence/build.log 与 artifacts.json。
