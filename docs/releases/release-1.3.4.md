# v1.3.4 发布说明

versionName 为 `1.3.4`，versionCode 为 `14`，包名仍为 `cn.edu.bjtu.foodtime`。沿用固定正式签名，通过 `dev → main` 发布 PR 触发 CD，Release 标签为 `v1.3.4`。

## 更新范围

- 去除“我的”“管理”“食堂”等页面顶部临时出现的“正在更新…”提示，避免刷新时页面高度变化和抖动。
- 社区页刷新时仍保留顶部“正在刷新社区…”提示。
- 应用内更新进度窗口新增更新方式与包体大小：优先显示“增量补丁 · 1.8 MB”，回退完整包时显示“完整安装包 · 21.0 MB”。
- 新增实时下载速度显示，按 `KB/s` 或 `MB/s` 展示，并保留已下载大小 / 总大小和百分比进度。
- 补丁下载失败回退完整包时，窗口会同步切换为完整安装包大小和进度。
- 保留 v1.3.2/v1.3.3 的静态优先加载、社区刷新入口与 5 分钟返回刷新策略。

## 更新方式

v1.3.3 客户端通过 `APP_RELEASE_*` 检查到 v1.3.4 后，优先下载本机托管的 `13 → 14` bsdiff 补丁；补丁校验失败才回退完整 APK。不会跳转浏览器。

## 验证

- `./gradlew verifyArchitecture testDebugUnitTest lintRelease assembleRelease --no-configuration-cache`
- CD 会验证固定签名、versionCode/versionName、SHA-256，并生成 `manifest.json` 与最近版本补丁。
