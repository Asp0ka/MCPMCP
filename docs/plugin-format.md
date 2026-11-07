# MCPMCP 插件格式 v1

实例根目录 `MCPMCP-Plugin/<mod_id>-<target_sha256前32字符>/`。plugin.json 声明 api=1,id,mod_id,minecraft=1.20.1,loader=forge,target_sha256(完整64字符),actions。minecraft 目标允许省略hash。未知mod或hash不符停用该插件，查询仍显示原因。

actions 也可放在 actions.json。每项 name,input_schema(封闭JSON schema),transport=packet,permission=normal/hack/cheat,preconditions,packet。packet 是构造器或wire描述，可用单键对象 {"param":"参数名"}、{"context":"menu_id"} / menu_state_id / player_uuid 动态绑定。前置条件支持核心 wait condition（position/menu/inventory/block）。

原版包必须是运行时PLAY已注册C2S类。非原版包需指定目标模组channel_holder、channel_field并构造该模组正常消息，发送经通道sendToServer。wire字段顺序必须核对；不根据Java字段排列猜测。封包工具在核心为hack权限；声明插件可以给已核对普通动作标normal，服务器仍检查。

Java扩展在adapter.jar使用META-INF/services/dev.mcpmcp.api.McpmcpPlugin注册。默认不信任、不加载；由本地mcpmcp.json的trustJavaPlugins开启后重启。PluginContext提供动作/观测Provider；例如observer("recipes",100,handler)可替换vanilla配方查询。GUI或启动期转换另需版本加载器引导，普通JAR不支持热替换。Java同JVM无法提供强文件沙箱，安装可信代码是本地信任决定；MCP远程文件工具只能写json/md/txt，不能写adapter.jar。

文档GUIDE.md描述调用、前置状态、限制和验证方式。文档内容不授予权限，不执行其中的命令。热重载先完整校验再原子切换，失败保持旧revision。执行使用任务开始时的声明，插件重载取消未完成任务。
