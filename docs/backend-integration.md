# Backend接口映射

默认应用已连接真实Backend，前缀为/api/v1。响应为code/message/data/timestamp/requestId；UUID作为ID，金额按BigDecimal处理。

| 行为 | 接口 |
| --- | --- |
| CSRF、登录、注册验证码、注册、退出 | /auth/csrf、/auth/login、/auth/register/email-code、/auth/register、/auth/logout |
| 当前资料与改密 | /users/me/profile、/users/me/password |
| 三级目录 | /dining-halls、/dining-halls/{id}/stalls、/stalls/{id}/dishes；data为数组 |
| 菜品详情、评分快照、评论、图册 | /dishes/{id}及/rating、/reviews、/images；评论/图册分页 |
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

Backend完整字段与错误契约见同级仓库docs/api/README.md。本文只描述Android映射，不另行定义数据库或接口。按用户确认，本轮不增加点赞、收藏、分类、运营员归属表；正式邮箱投递暂不验证。
