# v1.3.1 发布说明

versionName 为 `1.3.1`，versionCode 为 `11`，包名仍为 `cn.edu.bjtu.foodtime`。沿用固定正式签名，通过 `dev → main` 发布 PR 触发 CD，Release 标签为 `v1.3.1`。

## 更新范围

- 菜品详情、菜品图册和社区图片预览统一使用 480px 缩略图。
- 点击预览后才加载原图，并在全屏查看器中支持双指缩放、拖动和前后切换。
- 降低首屏图片流量和内存占用，避免列表/图册一次性加载原图。
- 继续使用 v1.3.0 已接入的应用内更新、bsdiff 增量补丁和系统安装器流程。

## 更新方式

v1.3.0 客户端通过 `APP_RELEASE_*` 检查到 v1.3.1 后，优先下载本机托管的 `10 → 11` bsdiff 补丁；补丁校验失败才回退完整 APK。不会跳转浏览器。

## 验证

- `./gradlew verifyArchitecture testDebugUnitTest lintRelease assembleRelease --no-configuration-cache`
- CD 会验证固定签名、versionCode/versionName、SHA-256，并生成 `manifest.json` 与最近版本补丁。
