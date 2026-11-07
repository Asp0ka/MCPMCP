# 模组适配插件（0.1.2）

MCPMCP 核心仍只依赖原版协议与客户端公开 API。下列插件是可选 Java adapter，各自绑定目标模组的完整 SHA-256 与实际版本；不匹配时不会激活，也不会被声明式强制授权绕过。

安装位置：实例根 `MCPMCP-Plugin/<mod-id>-<目标JAR短SHA256>/`，内含 `plugin.json`、`adapter.jar`、`GUIDE.md`。Java adapter 需要该实例本地开启 `trustJavaPlugins` 并重启；默认关闭。

## 已交付

| 插件 | 目标 | 能力 | 边界 |
|---|---|---|---|
| `mcpmcp.curios` | Curios5.14.1+1.20.1 | 动态普通/外观槽、候选与真实菜单读取，受控开关/穿戴/卸下/交换、渲染与分页 | V2当前页hotbar单次SWAP；完整双侧指纹；拓展通用槽/饰品自动读取，专属技能另需适配；见curios-0.1.2.md |
| `mcpmcp.jei` | JEI 15.20.0.130 / 15.74.0.234 两个独立构建 | 分类、材料、配方、配方槽位、催化剂查询；`jei.transfer` 通过 JEI 已注册处理器填料；`jei.show` 打开配方页 | 查询不打开 GUI；填料要求已打开兼容容器、空游标；`submitted` 不等于已填料或已合成；15.20 无完成回调，需读槽位 |
| `mcpmcp.refinedstorage` | Refined Storage 1.12.4 | 网格菜单、存储行分页、提取/插入等受控动作 | 需要已打开 RS 网格；直接用网格 UUID 与实时菜单校验，不伪造服务端库存 |
| `mcpmcp.sophisticatedstorage` | Sophisticated Storage 1.4.79.2069 | 当前菜单、槽位、升级、分页查询与受控点击 | 只处理真实打开的菜单；不推断未同步容器 |
| `mcpmcp.sophisticatedbackpacks` | Sophisticated Backpacks 3.24.63.2057 | 背包打开、槽位/升级读取、合成升级页选择 | 需要真实背包物品与菜单；合成仍需核心容器动作 |
| `mcpmcp.ironfurnaces` | Iron Furnaces 4.1.8 | 各等级熔炉菜单、输入/燃料/输出/进度与增益读取 | 进度是客户端同步值，不证明服务端完成 |
| `mcpmcp.botania` | Botania 1.20.1-453-FORGE | 客户端配方枚举、法力池/扩散器/符文祭坛等局部方块资源 | 动态规则与机器自动化不完整；不修改配方或库存 |
| `mcpmcp.avaritia` | Re-Avaritia 1.3.9.0 | 客户端配方枚举、工作台/极限合成台结构查询 | 不提供无限物品或直接产出 |
| `mcpmcp.malum` | Malum 1.20.1-1.6.7 | 配方类型、祭坛/织机菜单与方块状态、单次原生交互 | 仪式合法性、灵魂数量等服务端状态可能 unknown |
| `mcpmcp.wizards_reborn` | Wizard's Reborn 1.20.1-0.2.9 | 配方、机器资源/进度、菜单槽位查询 | 未检查的动态仪式与结构有效性不推断 |
| `mcpmcp.ars_nouveau` | Ars Nouveau 4.12.7 | 法力、法术书、雕文、选法术、机器/仪式结构与配方 | 只读客户端同步值；不自动施法循环 |
| `mcpmcp.irons_spellbooks` | Iron's Spells 1.20.1-3.16.3 | 法力、法术选择、冷却、法术书、配方 | 服务端资源 unknown；不伪造施法成功 |
| `mcpmcp.traveloptics` | T.O Magic 6.3.0-1.20.1 | 与铁咒共享法力的法术选择/配方/机器视图 | 同铁咒边界 |

## 验证状态

真实整合包 `D:/Games/mc/Cheat/versions/神秘启旅`（Forge 47.4.21）中：

- 原12插件验收保留；新增Curios后共13个adapter冷加载成功，`get_plugins` 全部 `active`、`java_loaded=true`、`java_error=null`。
- JEI 15.20.0.130：分类、材料分页、橡木木板配方、槽位与催化剂读回；背包合成格填料后原木减少 1、结果槽出现 4 木板，游标为空、菜单未关闭。
- 各插件根 observer 返回合同或明确 `available=false` 原因；Ars/Iron's/Traveloptics 读到法力与法术书，Malum 返回 569 条候选、Wizard's Reborn 返回 479 条候选并成功分页；Botania/Avaritia 的局部方块与菜单合同可用。
- Botania/Avaritia 的客户端 RecipeManager 查询已读到实际配方；空页 complete=false 仅表示当前扫描预算尚未命中，必须继续 next_cursor。额外的查看器动态配方也可用 JEI 分类查询（如 botania:mana_pool、avaritia:extreme_craft）。
- 后续独立存档 `MCPMCP Plugin Acceptance` 已构造 RS 存取/自动合成、精妙存储搬运/排序持久化、背包升级合成、熔炉加工、Botania 灌注、Avaritia 实时9×9配方、Malum 插入/取回、Wissen 生成、Ars 机器/施法，以及铁咒/Traveloptics 施法场景。明细见 `plugin-acceptance-progress.md` 和 `evidence/plugin-acceptance-0.1.2/`；加载成功、客户端同步、提交和真实产物/效果分别记录，不能由代表场景推定全部功能覆盖。
- 当前审查后的增量修复与最后安装副本必须由 `final-delta.json` 绑定具体 hash 后才可打包。历史场景证明当时的二进制，不能自动代表后来新构建；未覆盖等级、升级、法术、仪式、多人与其他目标版本仍未验证。

## 构建与交付流程

开发脚本需要 Python 3.11+（MCP stdio bridge 本身仍支持 Python 3.10+）。退出目标实例后：

```text
python scripts/plugin_release.py
python scripts/install_plugins.py
python scripts/install_plugins.py --apply
```

`plugin_release.py` 从明确工程执行所有适配器的 clean/build/reobf 和分发任务，生成按源文件、manifest、GUIDE、产物 SHA-256 绑定的构建凭据；不是搜索任意旧 JAR 来匹配。安装器要求完整13插件集合、唯一目标版本/full hash、SPI/provider、规范路径及目标进程均通过预检；全量暂存再复查，保留备份和用户支持文件。系统不能提供跨进程事务，复制过程中不要另外启动游戏。

完成实际增量验收、分别保存原12插件 `docs/evidence/plugin-acceptance-0.1.2/final-delta.json` 和Curios `docs/evidence/curios-0.1.2/acceptance.json`、提交源代码后运行：

```text
python scripts/package.py
```

主包包含核心、精确提交的源码、文档，以及 `optional/MCPMCP-Plugins-1.20.1-forge.zip`（测试实例13插件）和 `optional/MCPMCP-JEI-Variants-1.20.1-forge.zip`（JEI15.20/15.74二选一）。两个 JEI 变体都分发，但不得同时安装同 ID 的插件。包内 manifest 和 SHA256SUMS 记录对应提交、构建/安装/验收凭据及 hash；打包先写临时文件并验证后替换正式包。

Curios新增范围与29项实机检查见 `curios-0.1.2.md`。单独增量构建可用 `python scripts/plugin_release.py --project curios-1.20.1-forge`，其余源码/产物/文档必须与此前构建凭据一致；不重建不等于省略来源验证。

## 独立构建

每个 adapter 是独立 Gradle 工程，`compileOnly` 引用 `core`/`plugin-api` 0.1.2 与目标模组 JAR，产物经 `reobfJar`。JEI 两个版本分别构建：

```text
gradle -p adapters/jei-1.20.1-forge build
gradle -p adapters/jei-legacy-1.20.1-forge build
```

重新分发时保持 `plugin.json` 中的 `target_version` 与 `target_sha256` 与目标 JAR 一致，否则插件不会激活。
