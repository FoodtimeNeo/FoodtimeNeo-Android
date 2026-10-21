# Backend接口映射

默认应用已连接真实Backend，前缀为/api/v1。响应为code/message/data/timestamp/requestId；UUID作为ID，金额按BigDecimal处理。

当前默认服务为 `http://154.9.252.201/api/v1/`，直接通过设备网络访问。仍可用 `FOODTIME_API_BASE_URL` 构建属性切回专用本地服务；Cookie按来源绑定，切换地址会清除不匹配的旧会话。应用不保存账号密码，也不预填线上账号。明文连接只为该指定IP和本机地址放行。

| 行为 | 接口 |
| --- | --- |
| CSRF、登录、注册验证码、注册、退出 | /auth/csrf、/auth/login、/auth/register/email-code、/auth/register、/auth/logout |
| 当前资料与改密 | /users/me/profile、/users/me/password |
| 三级目录 | /dining-halls、/dining-halls/{id}/stalls、/stalls/{id}/dishes；data为数组 |
| 菜品详情、评分快照、评论、图册 | /dishes/{id}及/rating、/reviews、/images；评论/图册分页 |
| 社区动态 | /community/feed、/community/posts/{id}及/comments；发帖POST /community/posts |
| 社区互动 | POST /community/likes、/community/favorites；状态与计数以服务端响应为准 |
| 图片文件 | POST /uploads/images；GET /media/{id} |
| 投稿 | POST /dishes/{id}/image-submissions、/stalls/{id}/submissions、/dining-halls/{id}/submissions、/dishes/{id}/reviews |
| 个人记录 | /users/me/reviews及/review-submissions、/dish-submissions、/stall-submissions、/image-submissions |
| 审核和日志 | /admin/moderation、/admin/moderation/{review\|dish\|stall\|photo}/{id}、/admin/moderation/logs |
| 目录维护 | /admin/catalog/{dining-halls\|stalls\|dishes}，详情/修改/删除加/{id} |
| 违规评论 | /admin/reviews；DELETE /admin/reviews/{id}必须带reason |
| 用户管理 | /admin/users；PUT /admin/users/{id}只接受现有role/status |

Cookie名为FOODTIME_SESSION。登录后重新获取CSRF；写请求带Cookie与CSRF，GET带Cookie。401清除当前会话，403/429/503显示服务端错误，写请求不自动重试。账号状态/角色从数据库重读，不能依赖客户端缓存授权。

评论先进入dish_review_submissions，最多一条pending；通过后按用户/菜品唯一更新dish_reviews并复制提交时间。评分不由客户端重新平均，按服务器每日物化视图显示。删除正式评论保留审核历史。

菜品/档口投稿通过后才创建正式实体；菜品投稿价格可空，通过前必须补齐。图片直接进入dish_images的pending，通过后才进图册；2026-10-14经用户确认的V4补充审核人、时间、原因及日志图片外键，日志四类外键只能填一个。历史名称来自真实关联，不在手机端伪造失效父级。

Backend完整字段与错误契约见同级仓库docs/api/README.md。本文只描述Android映射，不另行定义数据库或接口；社区数据、审核状态、点赞与收藏均以服务端为准。正式邮箱投递暂不验证。

## 社区接口对齐（2026-10-20）

Android社区实现对齐Backend `feature/community-p3` 的契约：动态正文1–2000字、最多9个已上传图片引用，公开Feed只返回approved；评论1–500字；点赞和收藏为切换接口。Android当前发帖界面先支持单图，读取端完整支持0–9张图片。动态卡片标题由正文首句生成，不向后端虚构title字段。Backend社区分支合并并部署前，线上默认服务不会提供这些接口，联调时应使用包含P1/P2/P3迁移与接口的服务环境。

后端社区版本尚未部署期间，Debug包默认启用明确标识的本地原型数据，不调用上述社区接口；原型数据不写入服务器，也不冒充线上内容。Release包默认关闭原型。后端部署完成后用`-PFOODTIME_COMMUNITY_PROTOTYPE=false`构建Debug包即可恢复真实接口联调，页面与domain模型无需替换。

## 2026-10-16 服务器接入验证

`verifyArchitecture testDebugUnitTest lintDebug assembleDebug` 通过，8项单元测试无失败。API36.1模拟器安装后由用户登录线上账号，页面实际显示留园、明湖等服务端食堂数据，未使用ADB端口映射。仅验证登录和浏览，未在线上执行投稿、评论、审核或数据库集成测试。设备验收脚本固定构建本机API地址，以继续将写入测试限定在专用本地环境。

## 管理员评论管理补充（2026-10-16）

底部“管理”入口仅对admin/superadmin显示，进入“评论管理”可查看正式评论的用户ID、评论ID、时间、菜品归属、正文和1–5评分，支持按已加载完整记录中的用户ID过滤、数量提示和清除。删除必须填写原因并确认；失败保留输入，成功重新加载。数据源继续使用真实 /admin/reviews，后端验证管理员和CSRF，普通用户不能越权。待审核、已通过、已驳回内容在“审核投稿”按状态筛选，只有待审核项显示通过/驳回操作，筛选状态随页面保存。评分快照每天刷新，不在客户端即时编造新分数。
