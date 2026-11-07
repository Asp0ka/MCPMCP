# 模组适配插件（0.1.2）

MCPMCP 核心仍只依赖原版协议与客户端公开 API。下列插件是可选 Java adapter，各自绑定目标模组的完整 SHA-256 与实际版本；不匹配时不会激活，也不会被声明式强制授权绕过。

安装位置：实例根 `MCPMCP-Plugin/<mod-id>-<目标JAR短SHA256>/`，内含 `plugin.json`、`adapter.jar`、`GUIDE.md`。Java adapter 需要该实例本地开启 `trustJavaPlugins` 并重启；默认关闭。

## 已交付

| 插件 | 目标 | 能力 | 边界 |
|---|---|---|---|
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

- 12 个 adapter 冷加载成功，`get_plugins` 全部 `active`、`java_loaded=true`、`java_error=null`。
- JEI 15.20.0.130：分类、材料分页、橡木木板配方、槽位与催化剂读回；背包合成格填料后原木减少 1、结果槽出现 4 木板，游标为空、菜单未关闭。
- 各插件根 observer 返回合同或明确 `available=false` 原因；Ars/Iron's/Traveloptics 读到法力与法术书，Malum 返回 569 条候选、Wizard's Reborn 返回 479 条候选并成功分页；Botania/Avaritia 的局部方块与菜单合同可用。
- Botania/Avaritia 的配方由各自 JEI 分类暴露（如 `botania:mana_pool`、`avaritia:extreme_craft`），客户端 `RecipeManager` 的直读结果为空；查询这两类动态配方请使用 `jei.recipes(category=...)`，插件本身不会伪造配方。
- 菜单型插件（RS 网格、精妙存储/背包、更多熔炉）需要真实方块与已打开菜单，未在本轮全部构造正向场景；未验证部分按各自 GUIDE 的前提执行，报告以实际读回为准。

## 构建

每个 adapter 是独立 Gradle 工程，`compileOnly` 引用 `core`/`plugin-api` 0.1.2 与目标模组 JAR，产物经 `reobfJar`。JEI 两个版本分别构建：

```text
gradle -p adapters/jei-1.20.1-forge build
gradle -p adapters/jei-legacy-1.20.1-forge build
```

重新分发时保持 `plugin.json` 中的 `target_version` 与 `target_sha256` 与目标 JAR 一致，否则插件不会激活。
