# 公告管理

仅 active superadmin 可以管理公告，普通 admin 的页面入口和 API 均拒绝。

创建公告：仅填写 content（Markdown，最多 20000 字符），支持预览。内容必须包含非空 Markdown 标题；列表名称取最高级别的第一个标题，忽略代码块、移除行内标记。支持编辑已有公告。创建不会修改当前公告。

设置公告：标题搜索、每页 6 条、按创建顺序倒序排列。搜索和翻页保留待设置项；确认栏显示待设置标题。查看全文通过独立阅读窗口完成。选择后点击确认设置才修改全局 config(id=1).notice_id；选择“暂不显示公告”将其设为 NULL。客户端不重置既有已读或不再弹出偏好，修改相同公告仍保留这些偏好。需要重新提醒所有用户时创建新公告并设置它。

后端新增接口（均 /api/v1/admin/notices 前缀）：
- GET /search?page=1&size=6&q= 标题搜索与分页（关键词为普通子串）。
- GET /settings 返回 {noticeId}。
- PUT /settings 接受 {noticeId: UUID 或 null}。
- POST /markdown 接受 {content}，保存但不启用。
- PUT /{id}/markdown 接受 {content}，更新但不改变当前配置和已读偏好。

数据库结构无新增变更，config 仍只有 id、notice_id。旧标题正文写接口保留兼容已安装客户端，新管理页面使用 Markdown 接口。
