# FoodtimeNeo Android · 北交干饭吧

原生 Java 17 / Android View 应用，默认访问真实 Backend。视觉沿用所给 Web 风格的纸张底色、墨棕描边、实色阴影与中文字体。

完整入口见 [文档导航](docs/README.md)；构建参数、专用设备验收和Release说明见 [构建与测试](docs/build-and-test.md)。

使用 JDK 17、Android SDK 36 构建，最低支持 API 26：

```powershell
.\gradlew.bat verifyArchitecture testDebugUnitTest lintDebug assembleDebug --no-configuration-cache
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

默认请求服务器 `http://154.9.252.201/api/v1/`，手机或模拟器联网后可直接使用，无需ADB端口映射。地址可通过 `-PFOODTIME_API_BASE_URL=https://服务地址/api/v1/` 配置；明文HTTP仅放行指定服务器和本机 `127.0.0.1`，其他来源保持禁用。已有不同签名安装时，可以使用 `-PFOODTIME_DEBUG_SUFFIX=.integration` 构建独立联调包，保留旧安装的数据。切换服务来源后，旧来源的Cookie不会复用。

应用提供食堂/档口/菜品浏览与搜索、评分评论与图册、图片/菜品/档口投稿、社区双列食记、个人评论与提交记录、改密和退出。社区支持动态详情、单图发帖、评论、点赞和收藏；公开内容只显示审核通过记录。管理员可审核、维护三级目录、删除违规评论；平台管理员可搜索用户、调整他人角色和状态。普通用户底栏为“食堂 / 社区 / 我的”，管理员在社区与我的之间增加“管理”。各栏目保留页面栈和滚动位置。

角色、审核状态、账号权限均由Backend决定。Cookie用Android Keystore加密保存，密码不保存；重启后通过服务端恢复会话。评价与投稿审核通过后公开，评分使用每日更新的服务端物化视图。投稿价格可空，通过前由管理员补齐。模拟仓库仅在testShared源码中，不进入正式APK。

正式邮箱投递按用户要求暂不验收，注册页面不生成或自动填入验证码。Backend的dev环境启用测试开关时，可手工使用 `000000`，prod拒绝启用；具体以实际服务配置为准。仅在Backend显式启用本地联调样例时，才可使用其文档中的测试账号；应用不会预设管理员身份。

- [本地连接](docs/local-backend.md)
- [架构](docs/architecture.md)
- [功能与文档裁决](docs/implementation-plan.md)
- [接口映射](docs/backend-integration.md)
- [真实数据源](docs/remote-repository.md)
- [验收记录](docs/verification.md)
- [分支协作](CONTRIBUTING.md)

本地设备验收脚本使用专用Backend和ADB端口映射，并显式将被测应用构建为本机地址，不会将会写入数据的集成测试指向线上库：

```powershell
.\scripts\verify.ps1 -Device -ApplicationSuffix .integration
```

本轮按最新dev建立功能分支，使用中文提交，并以一次非快进合并回dev；复用旧功能分支时先让其指针指向最新dev。main发布流程由项目的CI/CD配置负责，本轮不发布main。
