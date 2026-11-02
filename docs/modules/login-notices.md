# 登录公告展示

主动登录和进程冷启动恢复会话后，请求 users/me/notices/current 一次。普通导航及返回前台不轮询。LoginNoticeController 通过非配置实例保留 Activity 旋转前的检查、弹窗和提交状态，进程冷启动重新检查；退出和切换账号清除状态并丢弃旧回调。

公告使用 content 单字段，Markwon 在原生 TextView 中渲染标题、段落、强调、列表、引用、代码、表格、删除线和网络图片。未启用 HTML/WebView；链接仅打开 HTTP(S)/mailto，图片禁止 file/data/content 协议。网络图片使用可访问的绝对 HTTP(S) 地址。

弹窗提供默认未勾选的“下次不再显示”和“确认”。点击确认才提交对应公告 ID 和 dontShowAgain；未勾选记录已读、下次登录仍可弹，勾选则服务端跨端免弹。失败保持选择并允许重试，提交期间禁止重复点击。取消不设置免弹。未读计数独立查询，不作为登录弹窗判断依据。

检查失败不影响主功能，可从通知信箱点击“重试登录公告”。历史公告可按需加载并用相同 Markdown 渲染，普通阅读接口不会设置或清除免弹。

需要 Backend V27。config 严格只有固定 id=1、可空 notice_id 两列；为空不弹公告。公告创建、编辑与管理配置页面不在本轮范围，Android 不新增发布入口。

设备验收使用隔离 .integration 包和 loopback HTTP 替身，选择测试 runner：

    .\gradlew.bat assembleDebug assembleDebugAndroidTest '-PFOODTIME_DEBUG_SUFFIX=.integration' '-PFOODTIME_TEST_RUNNER=cn.edu.bjtu.foodtime.presentation.LoginNoticeRunner' --no-configuration-cache
    adb install -r app/build/outputs/apk/debug/app-debug.apk
    adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    adb shell am instrument -w cn.edu.bjtu.foodtime.integration.test/cn.edu.bjtu.foodtime.presentation.LoginNoticeRunner

该 runner 不访问部署服务器的业务写接口。
