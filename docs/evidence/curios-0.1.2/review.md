# Curios 实现收尾审查

基点 `21ee5a1`，范围为新增Curios工程及release脚本/测试/文档改动。审查基于当前生产源码、真实目标字节码、离线回归和实机记录；没有以子任务完成状态代替游戏效果。

## Standards

- 注册独立 `mcpmcp.curios`、4个observer和6个闭合schema动作；均normal/packet，dry_run默认true。不改变核心权限，不修改其他模组或配置、配方。
- ModList实际文件full hash/version绑定；未知capability/handler/slot包装器只读。真实菜单/玩家/世界/连接/page/grid/slot对象绑定，过期session拒绝。
- 两侧完整ItemStack.save+ForgeCaps+完整int count指纹，空栈也绑定；预检后再次capture/校验，提交只有单一原生包。未使用危险的SlotItemHandler.getMaxStackSize(ItemStack)临时写入重载，无直接setStack/增减槽位。
- 单次UUID回执保留、歧义交送不重发、pending阻止盲目追加写；本地外观预测与服务器回页区分。原生事件合法性只作为客户端预检，不冒充服务端许可。
- 安装器选择完整13ID/14变体集合，构建/安装/验收hash闭环。增量只重建Curios，原12插件源码/JAR/manifest/GUIDE均与原凭据相符；发布拒绝旧hash证据与变化产物混用。

核对范围内没有未解决的阻塞项。原实现GUIDE安装示例只使用12位短hash，与项目规范32位目录不同，已改源码与分发GUIDE并重新构建安装。

## Spec

- 用户要求“实现Curios适配”：已实现动态普通/外观槽位、候选、菜单、显式open/close、hotbar穿戴/卸下/交换、渲染与分页动作；不依赖旧MCP实现或截图猜槽。
- 实机29项检查通过，包括Botania、Enigmatic Addons、Celestial Artifacts新增槽的可逆操作，以及NBT/session/游标/标签/重复/绑定诅咒/无效页/transport护栏；前后快照、action_id和执行时日志已保存。
- 当前实际只有一页，分页的相邻页正向仅离线验证；不宣称真实多页通过。显示只验证同步bit，不宣称renderer像素行为。
- 无遗留菜单、非hotbar直接换装、跨页自动找槽/多步pickup恢复、槽位增减或任意拓展主动技能；这些边界明确在GUIDE与正式文档列出，不能把通用饰品支持描述成整个Curios生态全功能覆盖。

最终实例主菜单、NORMAL/MIXED；测试饰品已全部取回，外观视图/显示标志恢复，生存模式、空游标，无恢复任务。生产JAR/离线测试/当前安装hash一致；提交后由发布脚本再验证嵌套包。
