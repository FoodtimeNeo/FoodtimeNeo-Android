# 餐厅与档口点评

原生 View 实现，沿用现有纸张样式、导航、表单、点赞控件。餐厅/档口卡片显示后端直接评分；详情保留原搜索、目录、菜品价格，在顶部增补评分及评论入口。

## 页面

- `venue-reviews`（`hall~id~rating` / `stall~id~rating`）：公开列表、评分筛选、游标翻页、本人待审/驳回内联预览、点赞、嵌入回复。
- `venue-write`（`type~id`）：1–5 整数星级、1–500 Unicode 码点正文；原文或驳回文本回填；失败保留草稿和操作 ID。
- `venue-mine`（`type~reviews|replies~page`）：两类对象和两类记录的筛选、修改、确认删除。
- 管理端：审核状态及投稿类型筛选、发布者身份/邮箱/时间、原公开内容与本次提交对照、人工通过/驳回、填原因下架。

`VenueScreen` 只操作 `VenueRepository`；`VenueRemote` 负责 HTTP 与不可变快照，`RemoteRepository` 统一接入现有刷新机制，无界面线程网络操作。

## 可见性与可靠性

公开列表和本人待审分别读取，只有服务端确认通过才进入公开列表。本人待审/驳回在原评论处显示「仅自己可见」，重新进入或刷新可恢复。修改期间原评分、原公开版本、点赞和回复保持；批准后按同一楼层更新。待审回复不计公开回复数，不影响一级点评评分。

只在有待审、当前页面和窗口有焦点时短时更新，2 秒间隔、最多 30 秒，可手动刷新。楼层控件按稳定 ID 更新，刷新不销毁展开的编辑器；草稿保存在现有 Activity 状态，收起保留。提交重试复用操作 ID，回复携带真实目标 ID。

快照按账号、角色和会话隔离，退出时清理，旧请求不能回填新账号。失败保留最后一次成功数据并显示错误，不伪造均分和计数。公开正文用 TextView 纯文本显示，未引入 HTML/WebView。继续使用现有 HTTPS 域名地址，未更改默认服务器。

## 验证与部署

`VenueRemoteTest` 覆盖接口映射、待审恢复、幂等操作 ID、审核后合并、离线快照与账号切换；`VenueReviewsRunner` 在独立 `.integration` 包 + 本地 8080 后端上检验实际原生表单、待审、刷新、AI 审核、楼中楼、点赞及个人记录。Runner 拒绝远程 URL，不得对云端测试。

正常质量门禁：`verifyArchitecture assembleDebug testDebugUnitTest lintDebug`。本地设备验收编译使用 `-PFOODTIME_DEBUG_SUFFIX=.integration -PFOODTIME_API_BASE_URL=http://127.0.0.1:8080/api/v1/ -PFOODTIME_TEST_RUNNER=cn.edu.bjtu.foodtime.presentation.VenueReviewsRunner`，先启动隔离数据库与后端并启用既有 local-integration 样例。先部署含 V37 的后端，再发布正常域名构建 APK；无新增客户端密钥。
