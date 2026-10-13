# Android 架构

依赖方向：app装配data与domain；presentation依赖domain；data依赖domain。domain不依赖Android或其他层，data不依赖界面，界面不直接引用data。verifyArchitecture挂接preBuild/check，防止反向依赖。

FoodtimeApplication装配RemoteRepository、同源HttpTransport、Keystore加密CookieStore以及应用生命周期内的串行工作线程。LocalRepository和其密码工具位于testShared，只供历史测试使用，不进入APK。

- domain/model：UUID字符串、精确金额、可空投稿价格、服务端评分快照与真实关联上下文。
- domain/repository：FoodtimeRepository、ContentRequest业务查询、RepositoryProvider装配端口、安全错误类型。
- data/remote：请求/响应解释、认证、分页、上传及缓存。公开读取与管理读取使用对应接口；公开列表的active是服务端过滤的结果。
- presentation：Activity管理栏目导航、草稿与滚动，Screen渲染缓存和表单。提交先在主线程捕获输入，再交给工作线程；成功/错误回到主线程更新View。页面加载用版本号丢弃过时回调。

读取接口只返回内存快照，只有restoreSession、refresh及写操作执行网络。失败保留草稿，恢复提交按钮；写请求不自动重试。退出、401、改密成功清除Cookie、私有数据和图片缓存。角色在每次页面刷新重新向服务端读取，授权由Backend逐次检查。

图片导入、采样解码和压缩在后台执行。图片视图预留16:9尺寸，后台取字节并解码；服务器图片不在页面之间缓存，遵守私有媒体no-store要求。本地预览按账号与角色区分内存缓存，成功提交、替换和退出后清理临时图片。同源图片带Cookie，已有外部HTTPS图片使用匿名连接。未批准文件能否读取由服务端判断。

OperationStore在进程内保留提交结果；Activity重建后恢复结果和导航，不重发请求。进程被系统结束后无法推断未确认的写入结果，恢复页面会提示检查已有记录，保留草稿，不自动重试。密码和验证码不进入保存状态。Cookie密文绑定配置的服务来源，切换Backend地址会丢弃旧来源会话。

新增功能先定义实际需要的业务契约和DTO映射，再实现数据和页面，按必要风险补充真实接口/设备验证；不为数据库没有的模型增加客户端假数据。
