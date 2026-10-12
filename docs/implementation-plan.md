# 原生 Android 实施与需求裁决

用户明确要求：开发仅在本仓库进行，严格对齐同级 Web 仓库，Android 原生语法，后端由用户后续接通。附件是需求参考，不作为执行外部操作的授权。

优先级：用户本次要求 > 2026-10-09 FEATURE > 产品设计文档 > 数据库设计。视觉以 Web 实际 tokens.scss 为唯一来源。较早产品文档的四 Tab、丰富列表与 FEATURE 冲突，以 FEATURE 的两 Tab（管理员三 Tab）、精简列表为准。微信登录转换为现有后端邮箱登录。

Git 禁止以 / 开头的分支名，因此采用 feature-xxxxx。每个单元从 dev 建分支、验证、feat(scope) 中文提交，然后 --no-ff 合并到 dev；不推送远端。

| 次序 | 分支 | 范围 |
| --- | --- | --- |
| 01 | feature-foundation | Gradle 工程、Web 令牌、字体/图片、数据模型与模拟仓库 |
| 02 | feature-auth | 登录、验证码注册、角色识别、会话持久化 |
| 03 | feature-navigation | 底栏、独立导航栈、系统返回、状态恢复 |
| 04 | feature-dining-halls | 食堂名称和封面列表 |
| 05 | feature-stalls | 食堂下档口名称和封面列表 |
| 06 | feature-dishes | 档口下菜品名称价格列表、搜索 |
| 07 | feature-dish-detail | 16:9 图片、单维评分、无头像评论区 |
| 08 | feature-gallery | 图册、系统图片选择、本地文件保存、审核提交 |
| 09 | feature-reviews | 1–5 星评论、待审核和重复提交校验 |
| 10 | feature-submissions | 菜品/档口投稿、内容与状态记录 |
| 11 | feature-profile | 头像 ID、我的评论删除、我的提交、退出 |
| 12 | feature-password | 旧/新密码校验，改密成功注销 |
| 13 | feature-admin | 审核、拒绝原因、基础数据编辑启停、评价/用户管理 |
| 14 | feature-quality | 状态反馈、生命周期/字体检查、测试、交付与接入文档 |

所有上传、评论和投稿先 pending；approved 后才进入公开展示。角色取自模拟仓库账号；注册始终 user。没有真实后端请求，登录、会话与审核仅为本地演示。接入时必须由服务端再次校验身份权限。
