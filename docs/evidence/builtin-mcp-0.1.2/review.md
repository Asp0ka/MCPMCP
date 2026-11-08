# 独立审查与修复

审查deleg_f1e22171基点8a56edb→工作树，发现1项Important与1项建议；主MCP协议、端口顺延、手动启停/保存语义未发现确定性阻塞。

- Important：旧发现文件被信任时可能将Bearer发给端口复用者。先增加真实loopback回归：伪造服务、旧PID、旧instance、redirect、验证后Connection:close，旧实现5项失败。修复后桥接先用随机challenge请求/identity且不带Bearer，校验签名(HMAC-SHA256，本地token为key)、PID/instance/port/challenge完全匹配，在同一条保持连接的TCP上发送旧/call。连接不能复用、签名错误或会话不匹配时拒绝，不跳转、不重连重发。Java /identity只有签名证明，不提供游戏控制，不返回token，仍校验Host/Origin；标准/mcp Bearer权限保留。10项发现测试通过；MCP HTTP测试从47增至55断言通过。最后JAR实际游戏桥接及标准MCP SDK连接也通过。
- 建议：resize重建控件会覆盖未保存草稿。实际旧JAR复现自动启动草稿从关恢复开，config未改；修复init读取现有EditBox值并保留auto。最终JAR相同缩放操作后结构化读回仍为关，config字节未改。

完整Gradle build/check/reobf和Python46测试通过。原版旧模组没有发现文件时仍保留旧/call直连兼容，不提供新identity证明；本次新模组创建runtime文件并强制走验证。该证明只验证配置中持有共享token的本地服务，不隔离同账户其它拥有token/文件权限的代码。

真实库存/适配器全量玩法及世界内长按时停服未在本轮重做，仍按验收说明列明。
