# 公告管理与排期

仅 active superadmin 可以管理；普通 admin 的入口和 API 均拒绝。每条公告独立启停，保存草稿不会发布或弹窗。Markdown 最多 20000 字符，必须包含非空标题；列表按顺序倒序、每页 6 条，支持标题搜索、编辑与预览。

所有管理时间按固定 UTC+8 输入和展示，传输为带时区的绝对时间。发布时间为空使用服务器当前时间；开始显示时间为空沿用发布时间，不能早于发布时间。截止方式可以不设置、指定日期、指定日期和时分。日期截止包含当天，例如 11 月 15 日到 11 月 16 日 00:00 UTC+8 停止弹窗；时分截止为排他上界。截止必须晚于开始显示时间。仅启停或未修改时间输入时保留原有秒和微秒。

到发布时间，已发布公告进入用户信箱；弹窗区间为 `[开始,截止)`，到期或停用只影响弹窗，信箱仍保留。草稿与未来发布时间的内容对普通用户不可见。每类取最新有效一条，更新公告优先，再普通公告，最多两条。关闭第一条后重新向服务器查询，以免第二条已经过期；已选择不再显示时不会回退弹出同类旧公告。

接口前缀 `/api/v1/admin/notices`：

- POST `/markdown`、PUT `/{id}/markdown` 接受 `{content,schedule}`；旧的仅 content 请求仍可用。
- PUT `/{id}/schedule` 仅修改排期与启停，不改变 Markdown。
- schedule 字段：`publishedAt/displayStartsAt/displayEndsAt` 为 Instant 或 null，`expiresOn` 为日期或 null，`isUpdate/published/enabled` 为布尔值；两个截止字段不能同时提供，草稿不能 enabled=true。
- 列表和详情返回上述元数据、`createdTime`、增量 `seq` 和服务器计算的 `status`（draft/scheduled/waiting/active/paused/expired）。日期截止额外返回解析后的 displayEndsAt；客户端重新提交时仅发送 expiresOn。
- 旧 settings 和 title/body CRUD 保留兼容已安装客户端；新界面不再使用单个全局当前公告。

V45 增量迁移保留正文、阅读和免弹偏好，只启用升级前 config 中指定的当前公告，其余历史公告留档。旧公告的发布时间与显示开始时间沿用 created_time。新字段均在 V45 添加，不改写 V27。未来或草稿公告首次公开时在事务内重分配公共 feed 序号，使用事务级公告锁保证并发分配和提交顺序，避免旧客户端水位跳过排期公告。
