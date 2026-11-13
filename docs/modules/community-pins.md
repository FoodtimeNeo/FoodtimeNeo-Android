# 社区置顶

发现页独立紧凑置顶列表展示标题、作者与标记，最多 3 条；普通列表不重复。关注／搜索／作者／收藏／我的保留原规则。详情和发现页的置顶管理入口依赖 `community.pin.manage`，默认仅超管。

`CommunityPinRepository` 独立于帖子内容，`CommunityPins` 为不可变快照。`RemoteRepository` 从同一发现响应读取清单与普通流，下一页带 `pinRevision`；收到 `PIN_SET_CHANGED` 重新读取第一页，替换旧流。管理使用全局版本，冲突要求手动刷新，不自动重复写入；切号响应丢弃，返回发现页要求新数据后显示。

管理页包含待审核／被拒的保留名额，可调整完整顺序或取消。只有已公开帖子可新增置顶，原帖 ID、发布时间和互动不变。隐藏条目审核通过后恢复原顺序，删除或下架取消置顶。作者修改另行实现，本模块提供状态联动。

后端迁移 V56。`CommunityPinJsonTest`、`CommunityPinRepositoryTest` 验证结构、顺序、版本、实际 HTTP、分页重启和切号；`CommunityPinRunner` 仅允许 `.pincheck` 独立包及回环 18104 接口，通过真实系统触摸验证原始详情入口、失败重试、冲突、排序和账号隔离。不修改正式包名或发布渠道。
