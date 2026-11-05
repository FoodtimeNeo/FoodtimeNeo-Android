# v1.3.2 发布说明

versionName 为 `1.3.2`，versionCode 为 `12`，包名仍为 `cn.edu.bjtu.foodtime`。沿用固定正式签名，通过 `dev → main` 发布 PR 触发 CD，Release 标签为 `v1.3.2`。

## 更新范围

- 管理、我的、数据、食堂、社区五个主页面改为“静态壳优先”：先显示本地缓存或页面骨架，再在后台刷新数据。
- “我的”页面只更新关注数与粉丝数，不再因为一次资料刷新重建整页。
- “管理”首页只请求待审核投稿数量，不再串行拉取全部审核状态和目录上下文。
- “数据”页首次进入先显示静态看板骨架，统计快照返回后自动替换。
- 社区页增加“↻ 刷新”入口，并支持顶部下拉、列表底部上滑刷新。
- 社区页 5 分钟新鲜度策略：5 分钟内从其他页面或应用外返回不重复请求；停留在社区页不会定时自动刷新；超过 5 分钟返回时才刷新。
- 今日推荐优先使用已有缓存，后台再更新。
- 继续使用 v1.3.0 已接入的应用内更新、bsdiff 增量补丁和系统安装器流程。

## 更新方式

v1.3.1 客户端通过 `APP_RELEASE_*` 检查到 v1.3.2 后，优先下载本机托管的 `11 → 12` bsdiff 补丁；补丁校验失败才回退完整 APK。不会跳转浏览器。

## 验证

- `./gradlew verifyArchitecture testDebugUnitTest lintRelease assembleRelease --no-configuration-cache`
- CD 会验证固定签名、versionCode/versionName、SHA-256，并生成 `manifest.json` 与最近版本补丁。
