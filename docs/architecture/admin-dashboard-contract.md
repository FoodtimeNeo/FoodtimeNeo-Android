# 平台数据看板接口约定

前端路由为 `admin-dashboard`，仅向有效 `superadmin` 开放。已接入后端真实统计，由 `ContentRequest.Query.ADMIN_DASHBOARD` 请求数据，再通过 `FoodtimeRepository.adminDashboard()` 向界面提供不可变快照；没有成功快照时返回 null，不提供样例数据。

当前接口：`GET /api/v1/admin/dashboard?days=7`，标准 `ApiResponse` 包装，以下为 `.data` 字段结构示例（数值仅用于说明）。

响应结构：

```json
{
  "registeredUsers": 1248,
  "onlineUsers": 86,
  "reviews": 3862,
  "replies": 5917,
  "posts": 742,
  "halls": 7,
  "stalls": 46,
  "dishes": 618,
  "generatedAt": "2026-10-30T12:00:00Z",
  "trend": [
    {
      "day": "2026-10-24",
      "newUsers": 7,
      "newReviews": 68,
      "newReplies": 104,
      "newPosts": 12,
      "onlinePeak": 91
    }
  ]
}
```

约定：

- 所有累计值和增量值均为非负整数。
- `registeredUsers` 仅计有效账号；点评、回复、食记累计值计正式内容，食堂、档口、菜品计启用项。
- `onlineUsers` 为最近 5 分钟产生已认证请求的去重用户数，同账号多端计一次。
- `day` 为 Asia/Shanghai 的业务日期，客户端保存为 LocalDate；设备时区不改变横轴日期。返回连续日期，无数据日由后端补零。
- `trend` 返回含当日的最近 7 个自然日，接口支持 days=1..31，Android 固定请求 7 日。
- 趋势中的 `newReviews` 为点评投稿（含待审等状态），`newReplies` 为新回复，`newPosts` 为新建食记记录；与累计正式内容的统计范围不同。
- `onlinePeak` 历史字段名保留，实际为当日行为去重人数，界面显示“日活用户”，不是并发峰值。
- 权限不足返回 `403`，普通管理员不可读取平台级数据。
- `generatedAt` 用于判断缓存新鲜度，前端不根据客户端时间推导统计结果。
- `generatedAt` 按北京时间显示，并标注时区。进入页面、返回前台及手动刷新会重新读取；趋势切换只使用当前快照，不轮询。
- 首次失败显示错误和重试；后续网络/服务故障保留上次快照并明确提示。401、403、退出、换号、角色或账号状态变化清除受保护快照；旧请求不能覆盖新账号或较新请求。
- 缺失字段、负数、小数、无效日期、不连续日期或非 7 项趋势均视为响应异常，不转换成零值或样例数据。

## 2026-11-02 验证

使用隔离本地后端、PostgreSQL/Redis 和 Android API 36.1 模拟器完成真实登录、8 项统计读取、趋势、刷新、低权限拒绝与退出清理检查（18 项）。另有 JVM HTTP 回归测试覆盖格式错误、首次失败、旧快照保留、401/403、角色变化和迟到响应。构建、架构检查、单元测试和 lint 通过。默认 API 地址继续为 `https://bjtufoodtime.top/api/v1/`；本次不更改生产环境。

