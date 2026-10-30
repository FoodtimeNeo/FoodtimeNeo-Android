# Android 文档导航

| 需要了解的内容 | 文档 |
| --- | --- |
| 安装、当前功能和默认服务器 | [项目 README](../README.md) |
| 1.0.1 版本与更新范围 | [1.0.1 发布说明](release-1.0.1.md) |
| 1.0.0 历史签名与完整回归 | [1.0.0 发布验收](release-1.0.0.md) |
| 工具链、构建参数、设备测试与发布 | [构建与测试](build-and-test.md) |
| 本地 Backend 与ADB映射 | [本地连接](local-backend.md) |
| 分层、导航、草稿与图片 | [架构](architecture.md) |
| 附件需求与后续确认的适用范围 | [功能范围](implementation-plan.md) |
| 实际方法、路径及业务状态 | [接口映射](backend-integration.md) |
| Superadmin 数据看板前后端约定 | [数据看板接口](admin-dashboard-contract.md) |
| 会话加密、来源绑定与网络边界 | [真实数据源](remote-repository.md) |
| 已执行检查、环境与限制 | [验收记录](verification.md) |
| 功能分支和main发布规则 | [协作规范](../CONTRIBUTING.md) |

本次文档核对基线为 `main` 的 `ec064b3`。默认服务地址以 `app/build.gradle` 为准；网络明文范围以 `network_security_config.xml` 为准；接口定义由Backend的Controller、DTO与接口文档共同核对。

2026-10-21发布补充：首次固定发布签名和CD已配置，正式产物为 `app-release.apk` 与SHA-256校验文件，下载入口见[项目README](../README.md)。本次按用户要求将最新dev通过发布PR合入main；此前不发布main的轮次说明保留为历史记录。

验收文档中的2026-10-13离线模拟、2026-10-14本地隔离联调和2026-10-16线上只读检查分别保留，不相互替代。未合入main的图册试验不算当前交付能力；文档维护不代表重新执行线上写入测试或发布Release。
