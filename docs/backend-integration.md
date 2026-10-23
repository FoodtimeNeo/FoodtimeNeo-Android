# Backend接口映射

默认应用已连接真实Backend，前缀为/api/v1。响应为code/message/data/timestamp/requestId；UUID作为ID，金额按BigDecimal处理。

当前默认服务为 `http://154.9.252.201/api/v1/`，直接通过设备网络访问。仍可用 `FOODTIME_API_BASE_URL` 构建属性切回专用本地服务；Cookie按来源绑定，切换地址会清除不匹配的旧会话。应用不保存账号密码，也不预填线上账号。明文连接只为该指定IP和本机地址放行。

| 行为 | 接口 |
| --- | --- |
| CSRF、登录、注册验证码、注册、退出 | /auth/csrf、/auth/login、/auth/register/email-code、/auth/register、/auth/logout |
| 当前资料与改密 | /users/me/profile、/users/me/password |
| 三级目录 | /dining-halls、/dining-halls/{id}/stalls、/stalls/{id}/dishes；data为数组 |
| 菜品详情、实时评分、评论、图册 | /dishes/{id}及/rating、/reviews、/images；评论/图册分页 |
| 社区动态 | /community/feed/cursor、/community/posts/{id}；发帖POST /community/posts，提交title、content、imageUrls、clientRequestId |
| 社区互动 | PUT/DELETE /community/posts/{id}/like或/favorite、/community/comments/{id}/like；状态与计数以服务端为准 |
| 图片文件 | POST /uploads/images；GET /media/{id} |
| 投稿 | POST /dishes/{id}/image-submissions、/stalls/{id}/submissions、/dining-halls/{id}/submissions、/dishes/{id}/reviews |
| 个人记录 | /users/me/reviews及/review-submissions、/dish-submissions、/stall-submissions、/image-submissions |
| 审核和日志 | /admin/moderation、/admin/moderation/{review\|dish\|stall\|photo}/{id}、/admin/moderation/logs |
| 目录维护 | /admin/catalog/{dining-halls\|stalls\|dishes}，详情/修改/删除加/{id} |
| 违规评论 | /admin/reviews；DELETE /admin/reviews/{id}必须带reason |
| 用户管理 | /admin/users；PUT /admin/users/{id}只接受现有role/status |

Cookie名为FOODTIME_SESSION。登录后重新获取CSRF；写请求带Cookie与CSRF，GET带Cookie。401清除当前会话，403/429/503显示服务端错误，写请求不自动重试。账号状态/角色从数据库重读，不能依赖客户端缓存授权。

评论先进入dish_review_submissions，最多一条pending；通过后按用户/菜品唯一更新未删除dish_reviews；首次created_at复制提交时间，重评保留首次时间。评分不由客户端重新平均，按服务器实时汇总显示。正式评论软删除并保留审核历史，同步扣除评分。

菜品/档口投稿通过后才创建正式实体；菜品投稿价格可空，通过前必须补齐。图片直接进入dish_images的pending，通过后才进图册；2026-10-14经用户确认的V4补充审核人、时间、原因及日志图片外键，日志四类外键只能填一个。历史名称来自真实关联，不在手机端伪造失效父级。

Backend完整字段与错误契约见同级仓库docs/api/README.md。本文只描述Android映射，不另行定义数据库或接口；社区数据、审核状态、点赞与收藏均以服务端为准。当前开发还包含图文社区与楼中楼，社区字段和迁移以Backend的docs/community-schema.md及docs/community-api.md为准。

## 社区接口对齐（2026-10-23）

标题1–50字、正文1–2000字、原创图文1–9张本人上传图片，图片顺序和实际宽高来自服务端。发布使用固定clientRequestId重试，内容改变时创建新编号。公开Feed按游标分页；我的食记和收藏使用服务端个人接口。

一级评论从 /community/posts/{id}/comments/cursor 获取；楼内回复从 /community/posts/{id}/comments/{rootId}/replies 获取。POST评论同时传content、clientRequestId；回复增加rootCommentId和replyToCommentId，被回复用户由服务端推导。作者可见自己的待审/驳回记录，已通过才公开。删除根评论显示占位并保留合法回复。

显式PUT/DELETE点赞收藏不反转重试状态，计数和liked/favorited以响应更新。帖子与评论软删除后，父级不可见时不再允许评论和互动。

管理入口使用 /admin/community/posts 和 /admin/community/comments 队列；详情包含完整帖子、全部图片及根楼层/实际回复对象。POST进行人工通过或驳回，DELETE带reason下架。

Debug和Release默认都使用真实社区接口。显式原型开关仅供已有原型演示；完整楼中楼及审核验收使用关闭原型的数据源。后端应包含V19–V23迁移及相应接口。

## 2026-10-16 服务器接入验证

`verifyArchitecture testDebugUnitTest lintDebug assembleDebug` 通过，8项单元测试无失败。API36.1模拟器安装后由用户登录线上账号，页面实际显示留园、明湖等服务端食堂数据，未使用ADB端口映射。仅验证登录和浏览，未在线上执行投稿、评论、审核或数据库集成测试。设备验收脚本固定构建本机API地址，以继续将写入测试限定在专用本地环境。

## 管理员评论管理补充（2026-10-16）

底部“管理”入口仅对admin/superadmin显示，进入“评论管理”可查看正式评论的用户ID、评论ID、时间、菜品归属、正文和1–5评分，支持按已加载完整记录中的用户ID过滤、数量提示和清除。删除必须填写原因并确认；失败保留输入，成功重新加载。数据源继续使用真实 /admin/reviews，后端验证管理员和CSRF，普通用户不能越权。待审核、已通过、已驳回内容在“审核投稿”按状态筛选，只有待审核项显示通过/驳回操作，筛选状态随页面保存。评分读取后端实时汇总，不在客户端编造新分数。

评论点赞：PUT /reviews/{id}/like，DELETE同路径取消；无需正文。评论列表附带likeCount/replyCount/likedByMe/counterVersion，点赞返回likedByMe/likeCount/counterVersion，客户端不自行+1。

## 点评回复（2026-10-20）

GET/POST /reviews/{id}/replies，公开列表size20与nextCursor游标；POST携带content、replyToId和clientRequestId，成功202后提示审核中。GET /users/me/review-replies?page=&size=20 获取本人审核记录；DELETE /review-replies/{id}本人软删除；DELETE /admin/review-replies/{id}携带reason管理员删除。审核类型dish-review-reply沿用admin/moderation。回复和点赞不影响评分，公开reply_count来自服务端统计。

## 回复点赞（2026-10-20）

PUT/DELETE /review-replies/{id}/like，响应likedByMe/likeCount/counterVersion。公开回复附带同样字段，直接显示；处理中禁用重复操作，失败保留状态并显示错误。只更新回复统计及按钮，不刷新整个菜品页，不改变一级评论计数或菜品评分。
