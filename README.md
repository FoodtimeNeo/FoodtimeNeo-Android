# FoodtimeNeo Android · 北交干饭吧

原生 Java 17 / Android View 应用，默认访问真实 Backend。视觉沿用所给 Web 风格的纸张底色、墨棕描边、实色阴影与中文字体。

使用 JDK 17、Android SDK 36 构建，最低支持 API 26：

```powershell
.\gradlew.bat verifyArchitecture testDebugUnitTest lintDebug assembleDebug --no-configuration-cache
adb reverse tcp:8080 tcp:8080
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Backend须在开发电脑监听8080，应用地址为 `http://127.0.0.1:8080/api/v1/`。地址可通过 `-PFOODTIME_API_BASE_URL=https://服务地址/api/v1/` 配置；明文HTTP仅放行127.0.0.1。已有不同签名安装时，可以使用 `-PFOODTIME_DEBUG_SUFFIX=.integration` 构建独立联调包，保留旧安装的数据。

应用提供食堂/档口/菜品浏览与搜索、评分评论与图册、图片/菜品/档口投稿、个人评论与提交记录、改密和退出。管理员可审核、维护三级目录、删除违规评论；平台管理员可搜索用户、调整他人角色和状态。普通用户底栏为“食堂 / 我的”，管理员增加“管理”。各栏目保留页面栈和滚动位置。

角色、审核状态、账号权限均由Backend决定。Cookie用Android Keystore加密保存，密码不保存；重启后通过服务端恢复会话。评价与投稿审核通过后公开，评分使用每日更新的服务端物化视图。投稿价格可空，通过前由管理员补齐。模拟仓库仅在testShared源码中，不进入正式APK。

正式邮箱投递按用户要求暂不验收，注册页面不生成或自动填入验证码。仅在Backend显式启用本地联调样例时，才可使用其文档中的三个测试账号；应用不会预设管理员身份。

- [本地连接](docs/local-backend.md)
- [架构](docs/architecture.md)
- [功能与文档裁决](docs/implementation-plan.md)
- [接口映射](docs/backend-integration.md)
- [真实数据源](docs/remote-repository.md)
- [验收记录](docs/verification.md)
- [分支协作](CONTRIBUTING.md)

设备验收需要已启动的专用Backend和ADB端口映射：

```powershell
.\scripts\verify.ps1 -Device -ApplicationSuffix .integration
```

本轮按最新dev建立功能分支，使用中文提交，并以一次非快进合并回dev；复用旧功能分支时先让其指针指向最新dev。main发布流程由项目的CI/CD配置负责，本轮不发布main。
