# Android 端技术与运维文档导航

本文档是 `FoodtimeNeo-Android` 原生应用工程的系统级技术导航与构建运维速查中心。

当前客户端工程版本：**v1.3.1**（`versionCode 11`；日常开发主分支为 `dev`，生产发布基线为 `main`）。

---

餐厅与档口独立点评及完整互动：见 [功能与接口说明](modules/venue-reviews.md)。

管理数据看板用户总量、近 7 日／近 30 日趋势及三列新增汇总：见 [接口与页面约定](architecture/admin-dashboard-contract.md)。

原生食堂页每日推荐、详情跳转与本地联调：见 [功能与接口说明](modules/daily-recommendation.md)。

## 1. 文档结构索引矩阵

本仓库文档遵循四级核心分类体系，清晰划分运维构建、系统架构、业务模块与版本发布边界：

```
docs/
├── ops/             # 构建测试、本地联调、网络配置、数据源接入与接口契约
├── architecture/    # 系统分层架构、数据契约、设计决策与功能裁决
├── modules/         # 业务模块细节（关注粉丝、登录公告、互动点评、社区管理等）
└── releases/        # 生产版本发布说明与各阶段真实设备验收证据
```

| 模块分类 | 核心文档 | 运维与研发说明 |
| :--- | :--- | :--- |
| **运维构建与联调** (`ops/`) | [构建与测试手册](ops/build-and-test.md) | Gradle 构建参数、Release 签名配置、CI 门禁与真机设备验收标准 |
| | [本地后端联调](ops/local-backend.md) | ADB reverse 端口映射、环回地址转发、联调账号与网络排障 |
| | [真实数据源配置](ops/remote-repository.md) | `RemoteRepository` HTTP 通信层、Cookie/CSRF 会话与证书配置 |
| | [后端契约映射](ops/backend-integration.md) | 后端 API 映射表、DTO 转换、网络错误分类与离线缓存策略 |
| **系统与架构规范** (`architecture/`) | [分层系统架构](architecture/architecture.md) | MVP/MVVM 架构分层、Activity/Fragment 职责与单向数据流拓扑 |
| | [管理看板契约](architecture/admin-dashboard-contract.md) | 管理端运营看板数据模型、图表口径与解析契约 |
| | [功能与设计裁决](architecture/implementation-plan.md) | 历史架构裁决记录、约束依据与设计原则 |
| **业务功能模块** (`modules/`) | [关注与粉丝交互](modules/follow-fans.md) | 社交关注流、双向关系判断与列表渲染优化 |
| | [登录公告弹窗](modules/login-notices.md) | 首页弹窗判定、Markdown 原生富文本展示与免打扰逻辑 |
| | [公告管理界面](modules/notice-management.md) | 移动端公告发布、编辑与状态切换交互 |
| | [点评与社交互动](modules/review-interactions.md) | 点评点赞、二级回复展开、审核流联动与状态同步 |
| | [社区管理与资料](modules/community-management.md) | 动态与评论软删除、管理员原因输入与个人资料更新 |
| | [加载与骨架屏体验](modules/loading-experience.md) | 列表加载态、下拉刷新、骨架屏与缺省页设计 |
| | [媒体资源客户端缓存](modules/media-cache.md) | 图片三级缓存策略、内存复用与本地磁盘管理 |
| **版本与交付记录** (`releases/`) | [v1.0.0 发布说明](releases/release-1.0.0.md) | 基础首发版本与核心功能验收 |
| | [v1.0.1 发布说明](releases/release-1.0.1.md) | 热修补丁与稳定性改进 |
| | [v1.1.1 发布说明](releases/release-1.1.1.md) | 增强型点评与安全修复 |
| | [v1.1.2 发布说明](releases/release-1.1.2.md) | 社区互动与管理员控制台 |
| | [v1.1.3 发布说明](releases/release-1.1.3.md) | 个人资料维护与完整权限加固 |
| | [v1.2.0 发布说明](releases/release-1.2.0.md) | 菜品上传与审核流增强 |
| | [v1.3.0 发布说明](releases/release-1.3.0.md) | 访客账号、邮箱白名单、bsdiff 增量更新与 50MB 上传 |
| 1.3.1 版本与更新范围 | [1.3.1 发布说明](releases/release-1.3.1.md) |
| | [历史工程验收记录](releases/verification.md) | 真实环境端到端验证证据与设备适配覆盖报告 |

---

## 2. 客户端运维与排障速查手册 (Ops Runbook)

### 2.1 常用构建与测试命令

| 操作目标 | 推荐命令 | 说明 |
| :--- | :--- | :--- |
| **本地 Debug 打包** | `./gradlew assembleDebug` | 生成未签名测试 APK（位于 `app/build/outputs/apk/debug/`） |
| **单元测试执行** | `./gradlew test` | 运行全部无头单元测试（含 bsdiff、清单校验等） |
| **生产 Release 打包** | `./gradlew assembleRelease` | 生产签名打包（需配置 `KEYSTORE_PASSWORD` 等属性） |
| **全量质量门禁** | `./gradlew check` | 顺序执行 lint 检查与单元测试套件 |

### 2.2 本地真机/模拟器联调速查 (ADB Reverse)

当 Android 设备或模拟器需要连接本机运行的 Spring Boot 后端时，首选端口反向映射（避免配置 LAN IP 引起网络抖动）：

```bash
# 1. 建立设备 8080 端口到本机 8080 端口的反向代理
adb reverse tcp:8080 tcp:8080

# 2. 检查反向代理是否生效
adb reverse --list
# 预期输出：usb:XXXX tcp:8080 tcp:8080

# 3. 释放端口映射（联调结束时）
adb reverse --remove tcp:8080
```

### 2.3 常见移动端联调与发布故障排查

#### ① 无法连接后端网络（Cleartext HTTP Traffic Not Permitted）
- **排查原因**：Android 9+ 默认禁止明文 HTTP 请求。
- **解决方案**：
  - 确认 `app/src/main/res/xml/network_security_config.xml` 中已将 `127.0.0.1`、`localhost` 或联调测试服务器加入明文白名单。
  - 生产发布必须使用 HTTPS 域名。

#### ② 接口 403 CSRF 错误或会话丢失
- **排查原因**：`HttpTransport` 底层 OkHttp / HttpURLConnection 未正确保存服务端的 `foodtime_session` Cookie。
- **解决方案**：
  - 检查 `RemoteRepository.java` 中的 CookieJar / CookieManager 实现，确认内存与持久化 Cookie 存储正常。
  - 详见 [真实数据源配置](ops/remote-repository.md) 与 [后端接口契约](ops/backend-integration.md)。

#### ③ 应用内增量更新（bsdiff）校验失败回退
- **排查原因**：差分补丁基于特定版本 APK 生成，若基线版本与当前安装的 APK 校验和不一致，补丁合并将校验失败。
- **解决方案**：
  - 客户端内置安全机制：bsdiff 合成后自动校验 SHA-256，若失败则平滑回退下载完整 APK，不影响用户正常升级。
  - 详见 [v1.3.0 发布说明](releases/release-1.3.0.md) 与 `UpdateDownloader.java`。
