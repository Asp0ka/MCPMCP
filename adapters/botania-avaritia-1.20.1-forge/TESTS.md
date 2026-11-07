# 验证范围

## 已运行的非游戏检查

- verifyTargets/verify_targets.py：读两个目标实际字节、MANIFEST、mods.toml，核对精确版本和完整SHA256；42个目标模组类 javap -p -c，断言所有定制反射方法/字段存在，日志保留完整字节码。另有Minecraft官方映射与MCPMCP0.1.2核心 javap 证据。
- 两个子工程 check：真实PluginContext注册回调、真实core Schema/PluginValidation/Policy、封闭schema、查询限额/半径/整数/未知参数拒绝、合同发现、固定transport与权限、每次操作只调用一次后端、游标绑定世界/查询/配方快照及过期、显式0与unknown区别、空库存/缺字段区别、符文祭坛比例/零目标、9x9与其他tier槽位布局。NBT解析使用真实Minecraft类，后端替身仅测试provider公共合同，不冒充游戏。
- clean check build：Java17、Forge47.4.28、official1.20.1编译；botania:reobfJar和avaritia:reobfJar实际执行。
- packageAdapters/package_adapters.py：分发完整plugin.json、GUIDE和各adapter.jar；核对各唯一ServiceLoader行、不捆绑core/plugin-api/Minecraft、Minecraft.getInstance已变为SRG、复制字节一致、输出独立hash。

## 必须保留为未测

未启动游戏、未改目标实例。没有真实世界/菜单/服务端材料与产物的正向验证，编译和合同测试不能证明游戏加载、Forge网络管线顺序、动态配方实际同步、玩家手动切菜单、取消回填、远程服权限拒绝、服务端最终接受或多方块完整性。

后续人工或授权游戏验收：

1. 两个目标分别及同时加载，检查get_plugins Java加载/兼容性/revision；错误hash/版本应停用而非暗中信任。
2. 主菜单读botania/avaritia合同，进世界分页查询真实不同serializer配方；9x9材料不裁剪，永恒奇点只读字段，动态规则缺失有JEI提示。
3. 对池/扩散器/花药台/祭坛、压缩机/收集器从unknown等到实际自然同步，比较服务端已知资源；检查显式0、缺字段、卸载、重生与换服。局部查询不同过滤/小预算续页，不加载原未加载区块。
4. NORMAL+PACKET/SIMULATE/MIXED各验证固定transport拒绝与允许，错误block/item、非空游标、其他screen、范围/遮挡/世界边界应拒绝。右键只submitted，独立比较材料和机器状态。
5. 显式打开9x9后读真实槽位，用核心container.move/sequence做一笔材料搬运与取出，核对空游标/菜单保持/服务端最终同步；取消由核心回填，仅原菜单同一身份可操作。目标原关闭时不声称有自动临时菜单会话。

游戏运行验收不在本次授权范围，本项目没有用模拟结果填充这些记录。
