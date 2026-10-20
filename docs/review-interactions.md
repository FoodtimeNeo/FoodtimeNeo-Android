# 菜品点评互动与实时评分

本轮按 feature/realtime-dish-ratings、feature/dish-review-likes、feature/dish-review-replies、feature/dish-review-reply-likes 顺序开发并分别合入 dev；数据库和API由同级 Backend 仓库维护。

界面沿用现有纸张卡片、红色评分和深色正文；正文16sp、辅助14sp、触控目标48dp。操作放在对应评论/回复内，保留原导航、评分表单和返回位置；不新增全局导航项。网络操作在worker执行，处理期间禁用重复操作，失败保留输入并就近说明。

实时评分使用 GET /dishes/{id}/rating 的 averageScore/reviewCount；后端在正式点评变化的事务中更新汇总。无评论时均分为NULL；Android不重新平均。每次进入详情通过既有repo.refresh重新请求，画面显示“实时统计”，不轮询每条计数。

一级点评、回复和两类点赞分开存储。主点评重评通过保留ID和互动；删除后新点评不复用旧讨论。回复和点赞不影响评分。后续各功能提交同步维护接口映射及验证记录。

## 一级点评点赞

Review模型增加likeCount/replyCount/likedByMe/counterVersion。新增PUT/DELETE /reviews/{id}/like；按钮在worker提交，直接应用服务端计数和状态，不重新加载整张菜品页。处理期间禁用重复提交，失败就近说明。架构、单元测试、Lint与Debug构建通过；未运行设备验收。
