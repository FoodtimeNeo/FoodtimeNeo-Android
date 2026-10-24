# 1.0.0 发布验收

versionName 为 1.0.0，versionCode 从2递增至3，正式包名仍为 `cn.edu.bjtu.foodtime`。沿用首次发布的固定 RSA3072 证书，证书 SHA-256：

`94e7db3a30937a0cd8bc8596f80666122e3bd3f3323e55bfef14f72e9c454b92`

密钥和口令只在构建进程中读取，不进入源码、文档或安装包附件。`apksigner verify --verbose --print-certs` 通过；实际APK元数据验证为 1.0.0 / 3。

## 功能与历史合并

随最新dev交付真实Backend社区实现：多图发布、紧凑楼中楼、点赞收藏、个人记录、完整人工审核及权限控制。

main 曾在 `7e61329` 撤回旧社区原型合并 `b0eb13f`。本次发布在功能分支纳入这两个历史节点，并明确保留当前已验收的dev完整实现；该历史同步节点未改变源码树。随后通过dev向main的发布PR交付，避免旧撤回在自动合并时再次删除社区页面和接口。

RemoteUiRunner 更新为当前个人页栏目、实时评分标签和按本轮对象定位的审核/删除断言。账号、口令由设备参数传入；运行前要求 `.integration` 调试包、loopback真实接口及关闭原型，避免误向默认服务器执行验收写入。

## 本机验证

2026-10-24，JDK17 / SDK36 / API36.1模拟器：

- `verifyArchitecture testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` 通过，16项JVM测试全部通过。
- `lintRelease assembleRelease` 使用原证书构建成功；Lint无错误，保留原有2项 DefaultLocale 警告。
- 正式 RemoteUiRunner：19项真实原生页面检查通过，包含三种角色、会话、点评审核、图片投稿、目录增删改、用户状态及改密恢复。
- CommunityUiRunner：12项原生社区页面检查通过。
- CommunityRepositoryRunner：21项设备真实HTTP检查通过；明确使用 `1@bjtu.edu.cn`、`2@bjtu.edu.cn`、`3@bjtu.edu.cn` 三种服务端身份。

三组设备检查使用独立本地QA数据库和ADB端口映射，未写生产服务器；结果存在覆盖重叠。测试前唤醒模拟器并在测试期间保持屏幕开启。

原始日志、签名校验和APK保存在联合工作区 `qa/release-1.0.0/`。GitHub CD在发布PR合入main后重新生成正式APK及SHA-256附件，下载入口见README。默认正式API地址保持构建配置；main合并和APK发布不代替Backend的生产部署，社区功能需要包含V19–V23的后端。外部正式邮件投递不在本轮验证范围内。
