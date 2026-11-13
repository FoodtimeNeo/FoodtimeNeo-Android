# 构建、测试与发布

## 工具链和普通检查

使用JDK17、Android SDK36及仓库Gradle Wrapper；compile/target SDK为36，最低API26。`local.properties`、SDK路径、签名和真实账号配置留在本机，不提交到仓库。

在Android根目录运行：

```powershell
.\gradlew.bat verifyArchitecture testDebugUnitTest lintDebug assembleDebug --no-configuration-cache
```

架构检查禁止domain依赖Android或其他层，禁止data依赖presentation，禁止presentation直接引用data。JVM测试不证明设备权限、渲染、真实网络或SMTP已通过；Lint和构建同样不代替设备验收。

## 地址与独立联调包

默认地址为 `https://bjtufoodtime.top/api/v1/`，无需ADB映射。已有同包名但签名不同的应用时，使用独立后缀，避免卸载原应用的数据：

```powershell
.\gradlew.bat assembleDebug '-PFOODTIME_DEBUG_SUFFIX=.integration' --no-configuration-cache
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

独立包名为 `cn.edu.bjtu.foodtime.integration`。连接本机专用Backend时，将完整Gradle属性用引号包住，尤其不要让PowerShell拆开含点号的参数：

```powershell
.\gradlew.bat assembleDebug '-PFOODTIME_DEBUG_SUFFIX=.integration' '-PFOODTIME_API_BASE_URL=http://127.0.0.1:8080/api/v1/' --no-configuration-cache
adb reverse tcp:8080 tcp:8080
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`127.0.0.1` 指向设备自身，通过ADB反向映射访问电脑8080服务。若电脑Backend监听18080，可使用 `adb reverse tcp:8080 tcp:18080`，应用地址仍保持设备端8080。多设备时为每条ADB命令指定同一 `-s <设备序列号>`。

`FOODTIME_API_BASE_URL` 应包含 `/api/v1/`。Release 默认禁止明文，`verifyReleaseBaseUrl` 会在地址不是 HTTPS（回环除外）时使 `assembleRelease`/`bundleRelease` 失败；debug 仅放行 `127.0.0.1`、`10.0.2.2` 回环。只改构建地址不会自动扩大明文白名单。Cookie绑定服务来源，切换来源会放弃旧会话；应用不预填线上凭据。

Debug和Release默认都关闭社区原型并使用真实接口。社区联调后端应包含V19–V23迁移；用FOODTIME_API_BASE_URL选择本机服务。

### IP HTTPS 连接检查

线上 HTTP IP 入口会重定向，客户端不会自动跟随；使用 HTTPS IP 地址及匹配该 IP 的可信证书。2026-10-29 起默认地址由 HTTP 改为 HTTPS，已有安装包需要更新；来源变化后需重新登录。

以下检查只发送 GET，请求 ping 和匿名 CSRF 会话，不使用账号或修改业务数据。独立 `.ipcheck` 包及 Cookie 存储用于验证 Android TLS、JSON、Secure Cookie 和会话复用：

```powershell
.\gradlew.bat assembleDebug assembleDebugAndroidTest '-PFOODTIME_DEBUG_SUFFIX=.ipcheck' '-PFOODTIME_TEST_RUNNER=cn.edu.bjtu.foodtime.data.remote.IpConnectionRunner' --no-configuration-cache
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w cn.edu.bjtu.foodtime.ipcheck.test/cn.edu.bjtu.foodtime.data.remote.IpConnectionRunner
```

## 会写入数据的设备验收

先按Backend的本地联调说明启动专用环境，启用所需样例并确认readiness为UP。将三个本地测试账号分别放入 `FOODTIME_TEST_USER_ACCOUNT`、`FOODTIME_TEST_ADMIN_ACCOUNT`、`FOODTIME_TEST_SUPERADMIN_ACCOUNT`，共同测试密码放入 `FOODTIME_TEST_PASSWORD` 后运行：

```powershell
.\scripts\verify.ps1 -Device -ApplicationSuffix .integration
```

可用 `-AdbPath` 指定ADB位置、`-Serial` 指定设备。[脚本](../../scripts/verify.ps1) 会构建测试包、显式覆盖为本机API、检查电脑8080readiness、建立映射、安装并运行 `RemoteUiRunner`。它不会代替你启动Backend，也不接受“默认线上地址”作为写入验收目标。

也可通过 `-UserAccount`、`-AdminAccount`、`-SuperadminAccount`、`-TestPassword` 提供同名参数。Device模式只接受 `.integration` 后缀；缺少参数会在构建前停止，不再使用硬编码账号。测试需要“联调示例食堂 / 档口 / 菜品”样例，账号密码必须属于当前专用测试库。

设备验收会创建、审核、删除测试内容并调整样例账号；必须使用专用本地库，不在154.9.252.201线上库运行。普通构建及JVM测试不需要执行这些写操作。历史运行结果见 [验收记录](../releases/verification.md)。

注册发送邮件仍取决于Backend配置。所有环境均须使用真实邮件验证码；固定测试验证码已移除，客户端不生成验证码、不伪造成功响应。真实SMTP尚未完成正式验收。

## GitHub CI与Release

- [CI](../../.github/workflows/ci.yml) 在main/dev的PR及push时运行架构、JVM测试、Lint、Debug与测试包构建；构建测试包不等于运行设备测试。PR进入main时要求来源为dev。
- [CD](../../.github/workflows/cd.yml) 在 main 合并提交的 Android CI 成功后运行，检出通过 CI 的同一 SHA，再执行架构、JVM 测试、Release Lint 和签名构建。支持 PR 合并与明确授权的本地合并推送，跳过过期、非合并和带 `skip-release` 的发布提交。支持在 Actions 选择 main 手动执行，且再次核对该准确提交的 main CI 成功；不再从 dev 构建固定签名制品。
- 产物为真正的release变体 `app-release.apk`，使用固定发布证书签名，并以 `apksigner verify` 校验。同步生成 `app-release.apk.sha256`；两份文件上传Actions artifact（保留30天）及GitHub Release。Release先以草稿创建，附件完整上传后公开；失败重跑可覆盖同一次运行的附件，不重复创建标签。
- Release需要 `ANDROID_KEYSTORE_BASE64`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD` 四个Actions Secrets，缺少任何一项会停止发布。本文不读取或保存它们的实际值。
- 自动标签为 `android-main-<运行编号>-<短SHA>`。仅同步dev或推送文档功能分支不会触发这条main发布流程；实际发布结果以Actions与Release为准，不能从YAML存在推断已经发布成功。

本地Release配置使用 `FOODTIME_RELEASE_STORE_FILE`、`FOODTIME_RELEASE_STORE_PASSWORD`、`FOODTIME_RELEASE_KEY_ALIAS`、`FOODTIME_RELEASE_KEY_PASSWORD`。保持发布签名连续，签名信息不得写入文档或Git。

CD通过Gradle环境变量传递签名参数，临时密钥只写入runner的临时目录，并在流程结束时清理；密钥不进入APK artifact。首次发布建立的固定签名保存在本机被Git忽略的 `.release-signing/`，需要单独安全备份，后续版本沿用同一证书。该目录与所有 `.jks`、`.keystore` 文件均被忽略，不能随源码提交。

下载APK后可使用 `sha256sum -c app-release.apk.sha256` 校验完整性。安装命令为 `adb install -r app-release.apk`；已有同包名且签名不同的debug包不能直接覆盖，安装正式包前请自行处理旧包及数据。

## 2026-10-21 评论修复验收与重新发布

此前 0.1.0 发布已撤回为草稿，修复版使用 versionName 0.1.1 / versionCode 2，沿用原固定证书以支持覆盖升级。CD 在验收期间暂停，三端通过后恢复；以后需要合并但不发布时，可给 PR 添加 `skip-release` 标签。手动发布仍须明确选择 main。

本轮永久原生回归入口：`ReviewReadQueueRunner`（多楼恢复及生命周期）、`EmbeddedRepliesRunner`（楼中楼组件）、`DishReviewPagingRunner`（Android HTTP/JSON、分页与 AI 卡片）、`ReviewInteractionsRunner`（真实本地 Backend 的提交、人工审核、点赞、回复、删除与评分）。构建时通过 `-PFOODTIME_TEST_RUNNER=cn.edu.bjtu.foodtime.presentation.<Runner>` 选择 runner，重新安装对应测试 APK 后执行 `adb shell am instrument -w <包名>.test/cn.edu.bjtu.foodtime.presentation.<Runner>`。

真实写入 runner 强制 `.completion` 独立包名后缀和 `127.0.0.1` API。本次使用 `-PFOODTIME_DEBUG_SUFFIX=.completion -PFOODTIME_API_BASE_URL=http://127.0.0.1:18080/api/v1/` 及 `adb reverse tcp:18080 tcp:18080`，仅连接独立 `foodtime_completion_lab`。它会在该环境创建专用测试菜品及评论，不要把端口反向映射到生产服务。其余三个 runner 使用进程内替身/loopback 服务，不向 Backend 写入。

发布 APK 不会自动部署 Backend；默认 API 服务器必须同步部署包含 V15–V18 迁移的兼容版本。当前记录只证明本地完整版本可用，不代表生产服务已升级。


## 社区设备验收（2026-10-23）

显式选择CommunityRepositoryRunner可运行真实社区接口闭环（21项），选择CommunityUiRunner可运行原生页面闭环（12项）；后者evidenceOnly=true模式只读已存在的联调样例并验证图片翻页、全屏缩放与关闭（2项）。使用FOODTIME_TEST_RUNNER构建属性指定完整Runner类名。页面Runner只接受`.integration`独立调试包、本机`http://127.0.0.1:8080/api/v1/`和关闭原型的数据源，开始登录前即校验这些条件。

通过Instrumentation运行参数userAccount、adminAccount、testPassword传入本地测试账号；接口Runner还需要superadminAccount。账号凭据由调用方提供。完整写入测试创建临时帖子并以带原因软下架结束，重复运行遵守正常上传/提交限制。当前结构、接口说明及后端验收见Backend的docs/community-delivery.md。

正式包的网络安全配置禁止全部明文连接；仅 debug 资源覆盖允许 `127.0.0.1`／`10.0.2.2`，并由 release base URL 门禁再次校验。`IpConnectionRunner` 同时验证服务器 IP 的 HTTP 被禁止、调试回环例外和真实 HTTPS API。修复版保持版本名1.0.1，versionCode递增至5；正式下载制品须由原发布证书签名后更新。

验收阶段使用独立后缀的 Debug APK；固定签名制品只能从 CI 通过的 main 合并提交构建。签名密钥只由 GitHub Actions Secrets 注入，不导出到开发机。

## 发布打包与应用内更新

### 版本号

`app/build.gradle` 中 `versionCode` 每次发布递增 1，`versionName` 使用语义化版本。v1.5.0 使用 `versionCode 19`。

### 正式 APK 由 CI 构建

`.github/workflows/cd.yml`：`dev → main` 合并提交在 Android CI 通过后触发 CD。

1. 校验 main 当前提交、合并提交与 `skip-release` 标签
2. 从仓库 Secrets 恢复签名密钥（`ANDROID_KEYSTORE_BASE64`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD`）
3. 执行 `verifyArchitecture testDebugUnitTest lintRelease assembleRelease`
4. `apksigner verify --print-certs` 校验签名，`aapt dump badging` 读取 versionName
5. 发布 GitHub Release `v{versionName}`，附件为 `app-release.apk` 与 `.sha256`

也可在 Actions 页面手动 `workflow_dispatch` 触发（仅 main，并核对该准确提交的成功 CI）。

### 本地构建正式包

需要本机有签名密钥时：

```powershell
.\gradlew.bat assembleRelease `
  "-PFOODTIME_RELEASE_STORE_FILE=C:\keysoodtime-release.jks" `
  "-PFOODTIME_RELEASE_STORE_PASSWORD=***" `
  "-PFOODTIME_RELEASE_KEY_ALIAS=foodtime" `
  "-PFOODTIME_RELEASE_KEY_PASSWORD=***" --no-configuration-cache
```

产物为 `app/build/outputs/apk/release/app-release.apk`。未提供签名属性时构建的是 unsigned 包，仅供体积估算，不能分发。

### 应用内更新检查

后端公开接口 `GET /api/v1/system/app-release`，通过环境变量配置：

| 变量 | 说明 |
| --- | --- |
| `APP_RELEASE_VERSION_CODE` | 最新 versionCode；为 0 表示关闭提示 |
| `APP_RELEASE_VERSION_NAME` | 展示用版本号 |
| `APP_RELEASE_APK_URL` | 下载地址，相对路径会按 API 源解析 |
| `APP_RELEASE_NOTES` | 更新说明 |
| `APP_RELEASE_MIN_VERSION_CODE` | 低于该版本强制更新 |

App 启动后自动检查一次；「我的」页「检查更新」可手动触发。

发布步骤：

1. 递增 `versionCode`，更新 `versionName`
2. 合并到 main 触发 CD，拿到签名 APK
3. 把 APK 放到 Web 的 `/downloads/FoodtimeNeo-Android.apk`
4. 更新 Web 的 `src/constants/download.js`（版本、大小、更新时间）
5. 更新后端 `APP_RELEASE_*` 环境变量并重启


## α／β 测试版

`versionName` 与标签保留 `2.0.0α`／`v2.0.0α` 等展示形式，版本提取由 `scripts/release/release_version.py` 校验，CI 同时测试 α、β、无后缀及非法输入。npm／Maven 对应内部形式 `2.0.0-alpha`。Android 升级仅比较递增的整数 versionCode，测试版始终允许稍后更新。

CD 明确标注 prerelease，不取代 GitHub latest 正式版；安装包、清单与增量包仍对应同一通过 CI 的 main SHA。清单记录 channel、prerelease 和 sourceCommit，标签路径按 UTF-8 编码。v2.0.0α 的公开更新配置见 [发布说明](../releases/release-2.0.0-alpha.md)，线上是否提示由部署配置决定。
