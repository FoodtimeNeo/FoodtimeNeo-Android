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
- Android Release Build：只有 Pull Request 真正合并到 main 后才运行，重新验证并生成 Release APK/AAB。
- Release 签名：在 GitHub Actions Secrets 配置 `ANDROID_KEYSTORE_BASE64`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD` 后会自动签名；缺少密钥时仅验证未签名构建。

仓库管理员还应为 dev 和 main 开启分支保护：要求 Pull Request、要求 Android CI 通过、禁止强推；main 只接受 dev 的 Pull Request。

发布产物的最终目的地需要在 GitHub Actions Artifact、GitHub Release 或 Google Play 之间确定。当前 Release 工作流只完成构建和验证，不会擅自上传二进制。
