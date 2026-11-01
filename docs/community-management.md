# 社区管理与个人资料

`admin`、`superadmin` 在“社区 → 帖子详情”可以删除他人的帖子、评论和回复。普通用户只能删除自己的内容。管理删除须填写 1–500 字原因，作者删除使用确认对话框；请求期间阻止重复操作，失败保留页面并显示错误，成功显示提示并刷新或返回。

管理接口为 `DELETE /api/v1/admin/community/posts/{id}` 和 `DELETE /api/v1/admin/community/comments/{id}`，请求体为 `{ "reason": "删除原因" }`。后端独立验证当前会话与权限，软删除并记录操作者。删除一级评论会保留已通过的楼内回复。

已有能力及入口：

| 能力 | 入口 | 权限 |
| --- | --- | --- |
| 删除菜品点评 | 管理 → 评论管理 | admin / superadmin |
| 创建、编辑公告 | 管理 → 公告管理 → 创建公告 | superadmin |
| 设置当前公告或关闭公告 | 管理 → 公告管理 → 设置公告 | superadmin |
| Markdown 渲染 | 公告预览、登录公告、消息信箱 | 登录用户 |
| 修改昵称与头像 | 我的 → 编辑资料 | 登录用户 |

个人资料通过 `PUT /api/v1/users/me/profile` 保存；头像先上传，后端验证图片归属。公告使用 Markwon 渲染，链接与图片加载限制危险 URI 协议。

普通验证：`gradlew.bat verifyArchitecture testDebugUnitTest lintDebug assembleDebug --no-configuration-cache`。`CommunityPermissionsTest` 覆盖普通用户、两种管理员、游客与禁用账号。JVM 测试和构建不等于真机交互验收。
