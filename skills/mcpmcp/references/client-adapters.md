# Minecraft客户端适配开发与调试

在修改MCPMCP、版本层或模组插件时使用。按下列顺序把失败定位到边界，再用原失败场景验证；不要把旧平台的成功移用到新平台。

## 1. 固定目标与真实运行依赖

- 从获授权实例的版本描述、实际加载模组列表和目标JAR读取版本，记录目标平台与完整hash；示例manifest和目录短hash不是兼容性证明。
- 查项目已有构建入口与缓存。编译依赖对齐游戏实际runtime版本，避免新库API编译成功、游戏中出现NoSuchMethodError。
- 优先检查本地对应版本映射JAR：`terminal`中用`javap -classpath "<mapped.jar>" -c -p <类名>`读真实调用；不要按别的MC版本或字段名猜语义。大classpath写javac参数文件，避免Windows命令长度限制。
- 离线构建显式传递实际`GRADLE_USER_HOME`，因为换缓存目录会把已有依赖变成离线不可用。只在已确认ForgeGradle卡于EnvironmentChecks联网探测、且明确使用离线缓存时使用`--offline -Dnet.minecraftforge.gradle.check.certs=false`；在线下载保持正常校验。

## 2. 在单一边界复现

记录MCP入参、桥接转发、游戏动作、同步快照和任务终态，检查同一参数在各层是否保持同一类型、分页语义和槽位命名空间。发现schema错误先补外层到执行器的回归，不能只测底层方法。

观测能力也需可发现的参数合同：在工具描述或可查询的kind说明中解释适用场景、范围/过滤、候选扫描预算、分页和unknown，不能仅列出blocks/entities等名称并留一个无说明的arguments对象。提供最小局部查询与翻页示例，字段以真实执行器为准；验证从工具发现到结构化结果的完整链路，而非仅证明内部方法能读方块。让调用者区分目标不存在、未读完、未加载、不可交互四种结果，避免遇到遮挡就退回截图。

对主菜单或创建世界卡住，先取线程栈再归因于启动器：原生加载界面可能内部继续渲染并重入调度器。Runtime与任务pump需重入保护；tick内cancel后检查任务是否已脱离，cleanup先摘除任务再回调，防止终态被覆盖或递归清理。

## 3. 把持续动作变成有归属的任务

- 路径移动保持running直到实际到达，超时/取消停止自己启动的寻路；立即返回submitted并丢掉任务会失去cancel能力。
- 输入只在取得资源后记为owned；取消尚未开始的排队任务不得释放正在运行任务或玩家的按键。用运行A、排队B、取消B且观察无新增release包的场景验证。
- 检查真实读取路径：LocalPlayer潜行取自Input且每tick更新，修改实体字段不等于持续潜行；在版本原生输入更新事件维护自己的状态。
- 纯发包长按物品先抓取真实包序列。原版未看到模拟use键时可能立即发RELEASE_USE_ITEM；若版本实现需拦截，只覆盖本任务持有期，结束先撤销归属再允许release。不能静默按模拟键来让PACKET测试通过。
- stop、异常、切世界和切界面释放自己输入；下马模拟必须有界释放，不能永久留下shift。
- 模组功能被全局按键冲突挡住（例如施法键与其它模组同为`g`）时，不要改用户`options.txt`键位让测试通过；改用该模组自己的客户端入口发单次脉冲（专属`ExtendedKeyMapping`实例加一次`ClientInputEvents.onClientTick`调用），保留法术ID/source索引校验、已有输入保护和`finally`释放，禁止跨tick重发。`submitted`仍要读效果、冷却和法力变化才算服务端接受。

## 4. 实现容器会话与恢复

先预检所有steps、count、槽位范围、物品身份、容量和装备语义，再打开或pickup；无效后续步骤不能在前面已搬物后才被发现。装备动作要求玩家菜单并验证目标装备槽，不能把箱子quick_move当穿戴成功。

固定世界epoch和菜单对象身份；目标菜单出现后核对类型与归属，不凭最近交互坐标或“出现任意菜单”接管。手动切换清除待归属记录。

判断菜单槽是否属于玩家库存不能比`slot.container`：模组菜单普遍用`SlotItemHandler`包装真实库存，裸身份比较会把玩家槽判成只读并拒绝quick_move。应`getItemHandler()`取出`InvWrapper.getInv()`再与玩家`Inventory`做同一对象比较，并限定原始索引0..35；也不要用“菜单末尾36个槽”猜玩家库存。已审查的升级/合成/增益槽优先按注册表判定，未知类型保持只读。回归至少覆盖：包装玩家槽可填可回、背包自身槽仍锁定、增益槽不被当玩家槽。协议无请求nonce时保留无法证明因果的范围限制，不伪造强关联。

不要把本地menu.clicked预测快照作为服务端确认。一种已经跑通的原版方案是发送当前menu/state ID、空预测delta，让服务端执行并同步，再等待相关物品变化；每步设预算和超时。业务对比含NBT，忽略纯state_id和工作进度；无变化或无确认时返回不确定/失败，不报成功。

取消后只回填本任务拾取的游标到原槽，验证物品、容量及同一菜单，等待实际回填同步后再关闭自己菜单。覆盖pickup后取消、打开回包未到即取消、手动替换菜单、超时和回填冲突；无法安全恢复时报告recovery_error和当前状态，不点击新菜单、不丢弃物品。

## 5. 使用真实形状与版本协议

复合VoxelShape的总体bounds面中点可能在空隙。遍历组成AABB的候选面，对候选点用游戏原生ray trace核对方块/命中面/距离，选择真实可见命中；不要为酿造台、铁砧等失败直接取消遮挡检查。

按ClickType验证button，普通左右键与快捷栏SWAP不是同一枚举域；版本确认支持时副手SWAP使用40。用实际装备和读回验证，不从允许一个数字推定语义完成。

JEI等查看器的泛型配方对象可同时代表多个变体（`slab_to_block`查橡木时也可能命中白桦）。配方句柄必须保存完整`IFocusGroup`，summary/detail/show/transfer全部沿用同一焦点，后续查询不得覆盖已返回句柄的焦点。通用配方API的outputs是全部候选，不能断言每项都等于焦点；另给`displayed_outputs`与`slots[].displayed`（`slot.getDisplayedIngredient()`）表示当前显示变体，并显式标注outputs语义。回归用“查A→查B→复查A句柄”加缺料实测填入与产出。

## 6. 在最后语义入口执行权限

模式权限与执行方式独立。raw packet真实transport固定packet；插件permission不能降低核心最低权限。在构造或wire解码前核对已登记PLAY C2S及明确方向；不把类存在或通道可见当构造器安全。

检查所有同义入口：显式command、聊天UI Enter/点击命令链接、启用cheats、自动化自身设置、batch子动作、hold攻击。正常攻击各入口复用距离/可视/身份检查；batch先复用完整子schema。远程文本声明不能授予自身权限或绕过文件能力。

## 7. 分阶段验证插件加载

manifest、外置actions、schema、packet绑定、preconditions与verify全部暂存预检后才换revision。缺packet或不支持的verify不能当合法新版本；失败保留旧版本。verify发送一次后有界轮询，不每tick重复发包。

把本地强制兼容授权绑定到当前插件声明摘要和实际目标版本/JAR，执行声明动作及Java handler/provider时复核当前授权。撤销必须立即阻断旧快照权限，不能等待成功重载；用“撤销授权同时另一声明损坏”的组合回归验证，并在无关声明修复后重试发布，避免配置已更新却永久保留旧注册。已加载Java的重启提示以实际加载记录为准，不能因adapter文件被移除而消失；缺模组、版本不符、hash不符、平台不符与目标JAR不可用分别展示，强制兼容不跳过schema、权限或Java信任。

Java provider按配置/构造/注册阶段隔离失败；捕获ServiceConfigurationError，注册成功才提交暂存action/observer，继续其它provider。默认不信任，冷加载一次。读取manifest/actions/adapter使用同一安全路径解析；Java同JVM与模组公共构造器仍属本地信任边界，不能宣称文件强沙箱。

安装脚本枚举插件清单时跳过`build/`、`stage/`、`evidence/`、`.gradle/`等派生产物：适配工程常同时保留`stage/`分发副本和`distribution/`，会得到重复plugin ID并把整批安装判失败。全量预检（hash、版本、平台、唯一provider、目录冲突）先于任何复制，预检不过则一个文件都不写；目标实例有Java进程运行时拒绝替换adapter。只更新已知包文件并备份旧件，保留用户在插件目录写的支持文件。

## 8. 整合测试后重跑原失败场景

等待worker明确交付完整文件再跑共享构建，不将正在写的测试当最终编译失败。纯Java main测试显式挂入check；需替身的manager合同放隔离临时classpath，不进入生产JAR。只在真实游戏或独立服务端读回后声称协议正向效果通过。
