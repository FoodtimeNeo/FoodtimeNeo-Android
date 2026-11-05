# 1.0.1 发布说明

versionName 为 `1.0.1`，versionCode 从 3 递增至 4，包名仍为 `cn.edu.bjtu.foodtime`。正式 APK 通过 `dev → main` Pull Request 合并触发 CD，沿用既有固定签名，支持从正式 1.0.0 覆盖更新。

## 更新范围

- 修复 IP 连接：默认 API 改为 `https://154.9.252.201/api/v1/`，直接连接 IP，不依赖域名跳转；服务器已部署匹配 IP 的可信证书及自动续期。
- 合入 dev 中的公告与信箱、分级图片缓存、可取消的图片加载，以及食堂和档口的骨架屏与加载过渡优化。
- HTTP 改为 HTTPS 后来源变化，旧来源 Cookie 不复用，更新后需要重新登录。

## 验证边界

IP 连接已通过 API 36.1 模拟器的 `IpConnectionRunner`，覆盖实际 Android TLS、API JSON、加密 Secure Cookie 与匿名会话复用，见 [验收记录](verification.md)。本轮版本检查执行架构检查、JVM 单元测试、Lint 及 Debug/Release 构建；签名 APK 由 GitHub CD 校验、构建并发布，下载入口见 README。

未使用线上真实账号执行登录或业务写入验收。服务器证书与代理变更已部署，APK 的签名与发布以 CD 实际结果为准。
