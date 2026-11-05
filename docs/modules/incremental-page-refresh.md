# 页面按区域刷新

沿用现有布局、颜色与控件。页面只读取实际显示的业务数据，固定控件和编辑器保留原实例。

- 改密、资料编辑、绑定邮箱、头像裁剪和发帖等本地表单使用当前会话快照直接展示，不再额外刷新个人资料。写请求仍由服务端逐次检查身份与权限。
- 投稿档口、菜品分别使用 `HALL_TARGET`、`STALL_TARGET`，不读取不需要的菜单。点评与图片投稿使用 `DISH_TARGET`，仅读取菜品及缺少的父级上下文；图册使用 `DISH_IMAGES`，不读取评论与评分。已缓存的父级目录不在每次进入菜单、详情时重复读取，实际目标接口继续执行服务端可见性检查。
- 信箱初次进入按已选标签读取公告、系统消息或私信列表，不预加载隐藏列表。标签点击只刷新列表区域和未读信息，标题与标签控件保留。
- 页面可通过 `onContentRefresh` 提供局部更新。回调仅在当前路径和身份仍匹配时执行；导航取消、过期响应、角色变化继续走原有检查。私信刷新、发送和加载历史只更新消息区域，保留输入框、焦点及草稿。较早消息与已加载消息按 ID 合并，刷新不清空历史。

普通门禁：`verifyArchitecture testDebugUnitTest lintDebug assembleDebug --no-configuration-cache`。JVM 回归验证按需请求、父级复用和私信历史合并；控件实例、输入保留与滚动需要独立调试包的设备验收。

## 验证记录（2026-11-06）

代码基线为 `dev@bdedc33`，改动位于 `feature/incremental-page-refresh`。Windows、OpenJDK 21 的本地环境执行普通门禁通过；91 项 JVM 测试无失败，Lint 和 Debug 构建通过。

通过 `assembleDebug assembleDebugAndroidTest -PFOODTIME_DEBUG_SUFFIX=.integration -PFOODTIME_API_BASE_URL=http://127.0.0.1:18080/api/v1/ -PFOODTIME_TEST_RUNNER=cn.edu.bjtu.foodtime.presentation.IncrementalRefreshRunner --no-configuration-cache` 构建独立调试包，在已连接设备执行同名 Instrumentation Runner，9 项检查通过。验证静态表单不取数、云端读取完成前编辑器可用、后台与显式刷新保留编辑器实例和草稿、焦点与光标保留，以及发送后局部追加消息。使用进程内仓库替身，不写入业务数据库；本轮未构建或发布正式版本。
