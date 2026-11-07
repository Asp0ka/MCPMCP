# Curios 独立适配与实际验收

目标为神秘启旅实际安装的 Curios **5.14.1+1.20.1**，完整 SHA-256 `1e817919a35b37cf30524aaec73f0ca5130452f23f168f844854df282eb8e51f`。Minecraft1.20.1，实际 Forge47.4.21，Java17。核心仍为0.1.2，Curios是独立可信Java插件 `mcpmcp.curios`；核心不依赖Curios。

## 已实现

- `curios`：完整闭合合同、参数来源与限制。
- `curios.slots`：无需GUI读取当前玩家动态槽类型、有效容量、普通/外观物品、完整指纹、渲染标志与分页。包括拓展和整合包实际同步的新增槽、零容量定义，不靠硬编码戒指/项链列表。
- `curios.menu`：真实V2菜单、会话、世界/玩家/菜单/页布局身份、游标、当前页真实槽号与玩家背包。
- `curios.candidates`：指定真实背包物品的原生canEquip/canUnequip提示；只限客户端已同步状态，不冒充服务端许可。
- `curios.open` / `curios.close`：显式单次原生开关菜单；不能接管其他界面。
- `curios.swap`：hotbar0..8与当前页真实CurioSlot的一次原版SWAP；`intent=equip/unequip/exchange`分别穿戴、卸下和互换。两侧ID/count/完整ItemStack.save指纹（含ForgeCaps）必填，空栈也需精确身份。
- `curios.page`：当前页或相邻页的受控单包请求。
- `curios.cosmetics.set`：外观视图切换，按原生客户端按钮逻辑与服务端回页同步。
- `curios.render.set`：普通槽渲染标志设置；幂等目标值避免重复toggle。

全部写动作 `normal/packet`，`dry_run`默认true，SIMULATE拒绝而不降级。一次提交/UUID回执去重，不在不确定交送后自动重发。服务端原生饰品规则、绑定诅咒、重复装备、mayPlace/mayPickup与容量限制均保留；不直接setStack、增减槽位或修改配置。

## 实际验证

仅在独立存档 `MCPMCP Plugin Acceptance` 中操作，初始化命令只准备输入物品，不伪造穿戴结果。当前世界玩家槽定义包含 pendant、heart、talent、brooch、nightmare等拓展槽；实际普通ring为3。以运行态读取为准，JAR内默认JSON不能代替整合包数据。

`docs/evidence/curios-0.1.2/acceptance.json` 保存 **29项通过检查**、前后快照和任务结果，`events.jsonl`保存执行时追加的真实请求/响应；summary记录原始事件hash。

- Botania魔力戒指穿戴、Enigmatic Addons石英戒指互换与卸下，两侧物品读回。
- Celestial Artifacts无主吊坠在独立pendant槽穿戴与取回；不代表附属主动技能适配。
- Botania单片眼镜在charm外观槽穿戴/卸下；功能槽显示标志切换并恢复。只验证同步标志，不宣称像素可见或显示开关等于停用功能。
- dry-run不改变装备；初始已开菜单保持打开；显式关闭回到游戏，重开后饰品状态保持。
- 同ID/count不同NBT的旧指纹拒绝；旧session、错误槽号、非空游标、错误标签、神秘遗物重复戒指均拒绝，未改变目标装备。
- 生存模式下绑定诅咒拒绝卸下。随后仅为测试恢复显式切创造，通过正常Curios操作取回，再恢复生存；未删除或覆盖已装备物品。
- 同request_id重放返回原回执，不把显示标志再切一次；SIMULATE拒绝packet动作；当前页no-op与越界页拒绝。

实机当前只有一页，**相邻多页正向翻页未实测**；离线测试覆盖页面/槽重建。所有提交仍只报submitted，不在插件内伪造server_confirmed。

## 构建、安装与发布

Curios工程离线clean/check/build通过，`AdapterTest`显式运行 **152项公共合同与保护检查**；真实NBT/ForgeCaps、Curios编码器/原生槽测试与脱离游戏Host替身的限制见adapter GUIDE。父任务增量构建凭据绑定当前源码、构建、分发与安装hash，未改动的12个旧插件沿用且核验此前同一二进制的验收凭据。

```text
python scripts/plugin_release.py --project curios-1.20.1-forge
python scripts/install_plugins.py
python scripts/install_plugins.py --apply
```

实际安装位置为 `MCPMCP-Plugin/curios-1e817919a35b37cf30524aaec73f0ca5/`。更新可信Java插件前退出该实例，保留备份与用户支持文件，重启后核对java_loaded。当前实例共13插件；JEI仍有两个互斥变体，完整发布共14个构建变体。

增量模式只重建指定工程，其他工程的源文件、JAR、manifest和GUIDE必须与先前构建凭据一致，任一变化即拒绝拼接旧证据。打包同时要求旧12插件原验收hash与新Curios验收hash分别一致。

最终恢复：全部测试饰品取回玩家库存，功能/外观槽为空，外观视图恢复功能页，显示标志恢复，角色生存，空游标，无恢复任务；客户端主菜单，NORMAL/MIXED，保留该实例既有Java信任设置。

## 范围边界

不支持遗留Curios菜单、任意背包索引直接换装、跨页自动找槽/批量装备、多步pickup自动恢复、增减槽位、其他模组的专属主动技能。非hotbar物品先用核心container.move按真实槽号定向暂存，再重新读取Curios身份。

普通饰品API支持不等于所有拓展功能全覆盖。未知包装器保持只读；网络竞争与服务器私有状态可能导致本地预检与最终结果不同，调用方必须读回。完整参数及调用示例以插件内 `GUIDE.md`和`observe curios`合同为准。
