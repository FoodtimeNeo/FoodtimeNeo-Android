# v1.4.2 发布说明

versionName 为 `1.4.2`，versionCode 为 `17`，沿用固定正式签名。`dev → main` 合并触发 CD 发布 `v1.4.2`。

## 修复

- **私信消息气泡内容不显示**：气泡内的文本与时间显式使用 `WRAP_CONTENT`，
  避免垂直布局默认 `MATCH_PARENT` 在 `wrap_content` 气泡中测得 0 宽，
  只显示一个很窄的空框。
- 设备测试增加「消息文本实际宽高大于 0」断言，防止回归。

## 升级

- v1.4.1 可直接增量更新到 v1.4.2。
- 发布后同步 Web `public/downloads`（APK、SHA-256、manifest）与 `download.js`。
