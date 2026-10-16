# 构建、测试与发布

## 工具链和普通检查

使用JDK17、Android SDK36及仓库Gradle Wrapper；compile/target SDK为36，最低API26。`local.properties`、SDK路径、签名和真实账号配置留在本机，不提交到仓库。

在Android根目录运行：

```powershell
.\gradlew.bat verifyArchitecture testDebugUnitTest lintDebug assembleDebug --no-configuration-cache
```

架构检查禁止domain依赖Android或其他层，禁止data依赖presentation，禁止presentation直接引用data。JVM测试不证明设备权限、渲染、真实网络或SMTP已通过；Lint和构建同样不代替设备验收。

## 地址与独立联调包

默认地址为 `http://154.9.252.201/api/v1/`，无需ADB映射。已有同包名但签名不同的应用时，使用独立后缀，避免卸载原应用的数据：

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

`FOODTIME_API_BASE_URL` 应包含 `/api/v1/`。明文HTTP只放行指定服务器和127.0.0.1，其他服务使用HTTPS；只改构建地址不会自动扩大明文白名单。Cookie绑定服务来源，切换来源会放弃旧会话；应用不预填线上凭据。

## 会写入数据的设备验收

先按Backend的本地联调说明启动专用环境，启用所需样例并确认readiness为UP，再运行：

```powershell
.\scripts\verify.ps1 -Device -ApplicationSuffix .integration
```

可用 `-AdbPath` 指定ADB位置、`-Serial` 指定设备。[脚本](../scripts/verify.ps1) 会构建测试包、显式覆盖为本机API、检查电脑8080readiness、建立映射、安装并运行 `RemoteUiRunner`。它不会代替你启动Backend，也不接受“默认线上地址”作为写入验收目标。

设备验收会创建、审核、删除测试内容并调整样例账号；必须使用专用本地库，不在154.9.252.201线上库运行。普通构建及JVM测试不需要执行这些写操作。历史运行结果见 [验收记录](verification.md)。

注册发送邮件仍取决于Backend配置。后端dev环境启用测试开关时，可以手工填写 `000000` 跳过邮件发送；客户端不生成验证码、不伪造成功响应，prod后端拒绝启用该通道。真实SMTP尚未完成正式验收。

## GitHub CI与Release

- [CI](../.github/workflows/ci.yml) 在main/dev的PR及push时运行架构、JVM测试、Lint、Debug与测试包构建；构建测试包不等于运行设备测试。PR进入main时要求来源为dev。
- [CD](../.github/workflows/cd.yml) 只在PR实际合并进main后运行，检出该合并SHA，再执行架构、JVM测试、Release Lint和签名构建。它创建GitHub Release并上传APK，不部署Backend或Web，也不安装到用户设备。
- Release需要 `ANDROID_KEYSTORE_BASE64`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD` 四个Actions Secrets，缺少任何一项会停止发布。本文不读取或保存它们的实际值。
- 自动标签为 `android-main-<运行编号>-<短SHA>`。仅同步dev或推送文档功能分支不会触发这条main发布流程；实际发布结果以Actions与Release为准，不能从YAML存在推断已经发布成功。

本地Release配置使用 `FOODTIME_RELEASE_STORE_FILE`、`FOODTIME_RELEASE_STORE_PASSWORD`、`FOODTIME_RELEASE_KEY_ALIAS`、`FOODTIME_RELEASE_KEY_PASSWORD`。保持发布签名连续，签名信息不得写入文档或Git。
