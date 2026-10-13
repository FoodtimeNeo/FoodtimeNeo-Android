# 开发协作规范

## 分支流程

```text
feature-<功能名>  →  dev  →  main
```

1. 从最新 dev 创建一个功能分支，例如 `feature-dish-search`。Git 分支不能以斜杠开头，因此不使用 `/feature-xxxxx`。
2. 一个分支只完成一个可审查的功能或重构，提交信息使用 `feat(scope): 说明`、`fix(scope): 说明` 或 `refactor(scope): 说明`。
3. 本地执行验证后，创建 Pull Request 合并到 dev。
4. dev 集成验证通过后，只通过 `dev → main` 的 Pull Request 发布。
5. 禁止直接向 dev 或 main 提交；功能分支合并后及时删除。

推荐命令：

```bash
git switch dev
git pull --ff-only
git switch -c feature-<功能名>

./gradlew verifyArchitecture testDebugUnitTest lintDebug assembleDebug --no-configuration-cache

git push -u origin feature-<功能名>
```

## 自动化

- Android CI：Pull Request 指向 main/dev，以及代码进入 main/dev 后都会运行。它检查分支流、架构边界、单元测试、Lint 和 Debug 构建。
- Android CD：只有 Pull Request 真正合并到 main 后才运行，重新验证、生成已签名 Release APK，并发布到 GitHub Releases。Release 标签格式为 `android-main-<运行编号>-<短提交号>`。
- Release 签名：必须先在 GitHub Actions Secrets 配置 `ANDROID_KEYSTORE_BASE64`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD`；任何一项缺失都会中止发布，避免上传无法安装的未签名 APK。

仓库管理员还应为 dev 和 main 开启分支保护：要求 Pull Request、要求 Android CI 通过、禁止强推；main 只接受 dev 的 Pull Request。

CD 使用 GitHub 自动提供的 `GITHUB_TOKEN` 创建 Release 和上传 APK，不需要额外创建个人访问令牌。
