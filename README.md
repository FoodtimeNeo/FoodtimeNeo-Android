# FoodtimeNeo-Android · 北交干饭吧

原生 Android 手机端，Java 17 + Android View；没有 WebView。界面使用同级 `FoodTime--frontend` 的颜色、Noto 字体、手写字和实际食堂/菜品图片。后端暂未连接，所有交互通过 `LocalRepository` 在本机演示并保存。

## 打开和安装

用 Android Studio 打开本目录，选择 JDK 17 或更新版本，安装 Android SDK 36，等待 Gradle 同步后运行 `app`。最低支持 Android 8.0 / API 26；已在 API 36.1 模拟器验证。

命令行构建（Windows PowerShell）：

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug --no-configuration-cache
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

SDK 路径由 Android Studio 创建本机 `local.properties`；该文件不提交。Gradle Wrapper 已包含在仓库中。可安装包输出为 `app/build/outputs/apk/debug/app-debug.apk`。

## 本地体验账号

| 身份 | 邮箱 | 初始密码 |
| --- | --- | --- |
| 同学 | 20260001@bjtu.edu.cn | Foodtime2026! |
| 管理员 | 20260002@bjtu.edu.cn | Foodtime2026! |
| 平台管理员 | 20260003@bjtu.edu.cn | Foodtime2026! |

登录页下方的体验按钮会填写账号和密码，随后点击“登录手账”。修改演示密码后请用新密码登录；清除本应用数据可恢复初始演示环境。验证码注册在本地直接显示验证码，不发送邮件。

## 已实现

- “食堂、我的”底部导航；管理员增加中间“管理”。每个 Tab 保留自己的页面栈和滚动位置，重复点击当前 Tab 不重置，系统返回逐层回到食堂/档口。
- 食堂、档口只展示封面和名称；档口菜品只展示名称与两位小数价格。支持档口内搜索和无结果提示。
- 菜品详情：16:9 图片、右上角上传、右下角图册、单维 1–5 星评分、无头像评论区、待审核点评。
- 原生系统选图、图片压缩与私有目录保存；图片、菜品、档口投稿及状态/驳回原因记录。
- 个人头像与 ID、我的评论与确认删除、我的提交状态筛选、旧密码验证与改密注销、确认退出。
- 管理员审核评论/图片/菜品/档口、审核日志、食堂/档口/菜品新增编辑启停、违规评论删除。平台管理员可启停账号和调整其他用户角色。
- 注册账号固定普通用户；待审核内容不公开；评论审核通过后按用户/菜品唯一更新；角色、父级启停状态与权限在模拟仓库再次检查。

## 代码与文档

`Design` / `Ui` 管理统一样式；各 `*Screen` 管理页面；`MainActivity` 管理导航、系统返回、草稿和选图；`FoodtimeRepository` 是接入边界，当前注入 `LocalRepository`。上传文件和模拟数据只存本机，模拟验证码、密码和会话不能用于真实生产认证。

- [需求拆分与冲突裁决](docs/implementation-plan.md)
- [后端接入说明](docs/backend-integration.md)
- [验收与视觉检查](docs/verification.md)

## 验收脚本

```powershell
# 构建 + 单元测试 + 静态检查
.\scripts\verify.ps1
# 已启动一个模拟器时，再执行原生交互验收
.\scripts\verify.ps1 -Device
```

设备验收使用独立临时数据，不清除体验数据。截图保存到设备应用外部目录 `acceptance-screenshots`。测试驱动在 `app/src/androidTest`，交互状态与审核规则断言随源码交付。

## 分支

从 `main` 建立本地 `dev`；14 个 `feature-*` 单元依次开发、检查并以 `--no-ff` 合并。Git 不允许分支名以 `/` 开头，因此将请求的 `/feature-xxxxx` 转为合法的 `feature-xxxxx`。提交沿用后端的 `feat(scope):中文说明` 与 `merge: 合并…到dev`。未推送远端。
