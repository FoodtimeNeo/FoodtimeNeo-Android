# 帖子浏览量

浏览量使用独立统计队列，不占用评论和用户写操作的工作队列；退出 Activity 时停止剩余任务，失败仍按原请求编号重试。

可选设备测试使用 `FOODTIME_TEST_RUNNER=cn.edu.bjtu.foodtime.presentation.ViewStatisticsRunner`，配合独立 Debug 包名运行。该测试通过阻塞统计任务并发起普通读取，验证两者不会互相排队，不需要业务账号。

帖子详情发布日期右侧显示浏览次数，列表不显示。每次点击进入详情生成请求 UUID；同一页面重绘、评论或点赞后刷新继续复用编号，已成功统计后不重复提交。请求独立执行，失败不阻塞阅读，再次重绘可用相同编号重试。

统计接口 `POST /api/v1/community/posts/{id}/views`，正文包含 `clientRequestId`，返回 `viewCount`。后端会在事务中去重并累计，旧服务器不支持时仍可浏览帖子。历史帖子从 0 开始计数。
