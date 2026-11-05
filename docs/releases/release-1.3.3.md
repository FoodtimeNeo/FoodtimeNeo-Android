# v1.3.3 发布说明

versionName 为 `1.3.3`，versionCode 为 `13`，包名仍为 `cn.edu.bjtu.foodtime`。沿用固定正式签名，通过 `dev → main` 发布 PR 触发 CD，Release 标签为 `v1.3.3`。

## 更新范围

- 修复切换账号或退出后重新登录时，社区页 5 分钟新鲜度时间戳可能沿用上一个账号的问题。
- 社区缓存现在绑定当前账号与角色；账号或角色变化后首次进入社区会立即重新加载。
- 保留 v1.3.2 的主页面静态优先加载、社区刷新入口、顶部下拉/底部上滑刷新和 5 分钟返回刷新策略。
- 继续使用应用内更新、bsdiff 增量补丁和系统安装器流程。

## 更新方式

v1.3.2 客户端通过 `APP_RELEASE_*` 检查到 v1.3.3 后，优先下载本机托管的 `12 → 13` bsdiff 补丁；补丁校验失败才回退完整 APK。不会跳转浏览器。

## 验证

- `./gradlew verifyArchitecture testDebugUnitTest lintRelease assembleRelease --no-configuration-cache`
- CD 会验证固定签名、versionCode/versionName、SHA-256，并生成 `manifest.json` 与最近版本补丁。
