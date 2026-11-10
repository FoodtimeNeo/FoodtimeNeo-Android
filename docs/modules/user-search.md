# 搜索用户

信箱、关注／粉丝页的“搜索用户”进入 `user-search` 路由，由 `UserSearchScreen` 管理输入和结果，不阻塞整页外壳。用户名对应现有昵称，关键词最多 50 个字符，停顿 300ms 后搜索，也可点击按钮或键盘搜索。每行头像、完整昵称、关注及私信按钮，操作目标至少 48dp。

`FoodtimeRepository.loadUserSearch(query,page)` 只由工作线程显式读取网络，`userSearchResults()` 只读取不可变快照。`RemoteRepository` 调用 `GET /users/search?q=...&page=1&size=20`，复用严格列表 JSON 校验；“加载更多”按 UUID 去重追加，失败保留原页。空输入不请求，换关键词先清除旧结果，页面版本、查询及仓库账号版本共同防止过期结果或错误覆盖当前输入。

关注复用现有写入和本人的关注数校准，仅更新结果行及关系快照，不重搜整表；取消关注需要确认。私信取得或创建现有会话后进入 `direct-chat`，不发送消息。访客可以搜索，绑定学校邮箱前禁用两个行操作。

关键词草稿按用户与角色保存，返回搜索页时恢复并重读第一页；退出和换号不显示其他身份的结果。结果快照在身份变化、退出和失效时清空。

`FollowRepositoryTest` 覆盖搜索分页、只读快照、空查询、关注局部更新、失败保留和账号切换的迟到响应。`UserSearchRunner` 是真实本地 Backend 的设备验收入口，强制独立 `.integration` 包名及 `http://127.0.0.1:18080/api/v1/`。通过 `userAccount/testPassword/query/targetId/targetName` 传入专用本地验收数据，按构建测试文档使用 `FOODTIME_TEST_RUNNER` 选择；不得映射到生产服务。

验收开始前保证隔离包没有其他账号的会话；必要时仅清除 `cn.edu.bjtu.foodtime.integration` 的数据，不操作正式包。重复运行可复用同一个验收账号，`layoutName=portrait` 可在活动内请求竖屏，覆盖按钮高度和行宽检查。
