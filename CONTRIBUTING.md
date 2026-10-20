# 开发协作规范

## 分支流程

```text
dev → feature/<功能名> → dev；发布时 dev → main
```

1. 从最新 dev 创建功能分支。复用旧分支时先确认其工作已合并，再将该分支指针同步到最新dev；不要将dev合并进旧feature形成交叉历史。
2. 一个分支完成一个可审查的功能或重构，提交和合并说明使用中文。
3. 验证后一次非快进合并回dev；团队使用Pull Request时采用相同的功能边界。本轮按用户要求本地合并后同步远端。
4. dev 集成验证通过后，只通过 `dev → main` 的 Pull Request 发布。
5. 实现提交放在feature，dev只接收合并；main按发布流程处理。分支清理与改写已有远端历史须遵从项目负责人的指令。

推荐命令：

```bash
git switch dev
git pull --ff-only
git switch -c feature/<功能名>

./gradlew verifyArchitecture testDebugUnitTest lintDebug assembleDebug --no-configuration-cache

git push -u origin feature/<功能名>
```

## 自动化

- Android CI：Pull Request 指向 main/dev，以及代码进入 main/dev 后都会运行。它检查分支流、架构边界、单元测试、Lint 和 Debug 构建。
- Android CD：只有 Pull Request 真正合并到 main 后才运行，重新验证、生成已签名 Release APK，并发布到 GitHub Releases。Release 标签格式为 `android-main-<运行编号>-<短提交号>`。
- Release 签名：必须先在 GitHub Actions Secrets 配置 `ANDROID_KEYSTORE_BASE64`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD`；任何一项缺失都会中止发布，避免上传无法安装的未签名 APK。

仓库管理员还应为 dev 和 main 开启分支保护：要求 Pull Request、要求 Android CI 通过、禁止强推；main 只接受 dev 的 Pull Request。

CD 使用 GitHub 自动提供的 `GITHUB_TOKEN` 创建 Release 和上传 APK，不需要额外创建个人访问令牌。

构建参数、CI与设备验收的区别、签名属性及当前服务器切换方式集中维护在 [构建与测试](docs/build-and-test.md)，其他文档通过链接引用，避免重复维护多套启动命令。
