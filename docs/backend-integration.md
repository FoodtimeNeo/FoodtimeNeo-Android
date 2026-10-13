# 后端接入说明

当前代码只调用本地模拟仓库，Manifest 未声明网络权限。这里明确区分已存在的后端接口和后续需要补充的接口，避免把模拟行为当成已接通。

阅读依据：同级 `FoodtimeNeo-Backend` 的最新 `origin/dev`，本次检查版本 `f06a09d`（2026-10-13）。未修改后端代码。

## 现有接口映射

所有接口前缀为 `/api/v1`，响应使用 `{code,message,data,timestamp,requestId}`；UUID 字符串作为 ID，金额用 `BigDecimal`，可空图片返回缺省图而不是伪造链接。

| 手机端行为 | 已存在接口 | 请求/注意事项 |
| --- | --- | --- |
| 获取 CSRF | GET /auth/csrf | 读取 data.headerName / data.token，同时保留匿名 Cookie |
| 登录 | POST /auth/login | `{account,password}`，account 是完整邮箱；附 Cookie 与 CSRF |
| 读取用户 | GET /auth/me | data 为 `{id,email,displayName,role}`；角色来自服务端 |
| 发验证码 | POST /auth/register/email-code | `{email}`；附匿名 Cookie / CSRF；202 后读取 resendAfterSeconds，不返回验证码 |
| 注册 | POST /auth/register | `{email,password,verificationCode}`；成功不自动登录 |
| 退出 | POST /auth/logout | 当前 Cookie / CSRF；成功清除客户端会话 |
| 修改密码 | PUT /users/me/password | `{oldPassword,newPassword}`；成功注销并转回登录页 |
| 食堂列表 | GET /dining-halls | 启用食堂；页面只使用 id/name/coverImageUrl |
| 档口列表 | GET /dining-halls/{diningHallId}/stalls | 页面只使用 id/diningHallId/name/coverImageUrl |
| 菜品列表 | GET /stalls/{stallId}/dishes | id/stallId/name/price；列表不加载图片或评分 |

Cookie 名称 `FOODTIME_SESSION`。登录后必须重新获取 CSRF，因为原匿名会话及令牌已被替换。写请求携带最新令牌，GET 业务请求携带 Cookie。服务端 401 应清空当前会话、回到登录；403 需要区分 CSRF 无效和权限不足；429 按 Retry-After 等待；503 显示重试并保留草稿。敏感写请求遇到网络中断不能自动重试。

## 接入入口

在 `data/remote` 中实现 `FoodtimeRepository`，然后只修改 `FoodtimeApplication` 中的装配。页面始终面向 domain 接口，不需要知道具体实现。该接口覆盖浏览、认证、投稿、评论、个人记录和管理；模型集中在 `domain/model/Models`，管理实体使用 `AdminEntity`，避免把本地 JSON 格式泄漏到界面和 domain。可以逐模块迁移，本地模拟实现应保留给离线演示与自动化测试。

网络 I/O 必须在后台线程执行；先取数据/更新缓存，再在主线程 `render()`。不要把同步网络调用放进当前 View 的点击回调。加载期间应显示明确的加载反馈并禁用提交，失败恢复按钮并保留草稿。缓存读接口可沿用当前列表模型。加入 `INTERNET` 权限；开发模拟器访问本机后端通常用 `10.0.2.2`，正式地址使用 HTTPS。

真实会话应由网络客户端管理 Cookie，CSRF 只保留内存；需要跨启动持久化 Cookie 时使用 Android Keystore 保护。不能沿用模拟仓库中的 sessionUser、客户端生成验证码、预置管理员身份或本地密码校验作为真实认证。真实后端再次检查角色、账号状态、父级启停状态和所有写权限。

图片选择结果为应用私有目录的 `file:` URI，最长边不超过1600像素、JPEG 压缩。接入上传后，将该本地路径变换为后端存储引用或 URL；浏览端对远程图需加入异步加载/缓存实现，当前 `Ui.image` 只读取打包素材与本地 URI。

## 仍待后端提供的能力

当前后端 docs 未定义下列接口，本次没有编造或调用它们：菜品详情与评分汇总、已审核评论列表、评论提交/删除、图片图册/上传、菜品和档口投稿、个人提交/审核状态、审核操作/日志、基础数据新增编辑启停、用户与角色管理。请在约定路径/字段、分页、图片存储策略后实现对应仓库方法。

数据库约束对应关系：

- 评论提交进入 `dish_review_submissions`；同一用户/菜品最多一条 pending；审核通过再更新 `dish_reviews`，用户/菜品唯一。
- 图片进入 `dish_images` 的 pending；图册只取 approved。当前本地模拟将 pending 图保存于 submissions、approved 图保存于 photos，适配时按数据库还原。
- 菜品和档口分别进入 `dish_submissions` / `stall_submissions`；通过后创建正式实体并保留 approved ID。
- `content_moderation_logs` 只关联一种提交，驳回原因必须回传。数据库设计附件未单列图片审核日志外键，图片审核 API 需和后端确认最终记录方案。
- 后端评分物化视图每24小时刷新；本地演示即时重算，以便验证交互。联调时以服务端汇总为准，不能假定审核或删除立刻改变公开评分。

产品文档的收藏、点赞等增强功能未列入较新的 FEATURE 范围，也没有本次后端模型支持；首版保留 FEATURE 核心闭环。消息、排行榜、社区和支付不占用当前手机导航。
