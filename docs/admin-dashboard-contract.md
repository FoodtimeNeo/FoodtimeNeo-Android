# 平台数据看板接口约定

前端路由为 `admin-dashboard`，仅向 `superadmin` 开放。当前界面使用与以下结构一致的预览数据；Backend 接入后由 `ContentRequest.Query.ADMIN_DASHBOARD` 刷新缓存，并通过 `FoodtimeRepository.adminDashboard()` 向界面提供不可变快照。

建议接口：`GET /api/v1/admin/dashboard?days=7`

建议响应：

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
- `onlineUsers` 表示响应生成时仍具有有效会话且满足 Backend 在线窗口定义的用户数；在线窗口应由 Backend 统一定义。
- `day` 按服务器业务时区聚合，返回连续日期；无数据日期也返回零值，避免客户端补点。
- `trend` 默认返回最近 7 个完整自然日及当日，后端可接受 `days=7` 或 `days=30`。
- 权限不足返回 `403`，普通管理员不可读取平台级数据。
- `generatedAt` 用于判断缓存新鲜度，前端不根据客户端时间推导统计结果。

