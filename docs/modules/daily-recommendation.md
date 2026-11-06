# 原生每日推荐

食堂页在食堂列表前增加横向滑动的“今日推荐”。独立加载推荐，失败时食堂列表仍可使用；推荐区提供重试、真实空列表和加载提示。卡片使用原生布局、既有配色与图片组件，文字可随系统字号换行，卡片与重试按钮满足 48dp 点击高度。

展示层参考 Web 端的手账／杂志式编排：卡片交错排列，使用圆角纸张、评价数标签和食物涂鸦。首页只展示前 5 个推荐，不展示评分、推荐理由与业务日期；没有菜品图时显示“敬请期待”涂鸦，而不是空白框。加载与空数据状态也保持同一视觉语言，所有装饰均由原生 View 绘制，不引入 WebView 或额外位图资源。

`ContentRequest.Query.DAILY_RECOMMENDATIONS` 通过会话校验后读取 `GET /api/v1/recommendations/daily`。`RecommendationJson` 映射为不可变 `DailyRecommendations` / `RecommendedDish`，校验日期、数量、重复菜品、UUID、评分、枚举和必填字段，错误不得转成默认零分或示例推荐。

- 按服务端顺序展示前 5 个菜品的菜名、食堂／档口和实际评价数；首页不展示业务日期、评分或推荐理由，也不填入示例价格。
- 点击真实 `dishId` 进入既有菜品详情，详情与父级目录由现有仓库加载。
- 缓存按账号、角色、会话隔离，刷新失败清空推荐快照；退出、会话失效、身份变化时失效。请求代次阻止旧账号或旧请求覆盖新数据。
- 推荐并非个性化，无用户 ID 参数；访客可查看。接口显示的日期使用 `LocalDate`，不随设备时区转换。

验证命令：

```powershell
.\gradlew.bat verifyArchitecture testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --no-configuration-cache --console=plain
```

`RecommendationRepositoryTest` 通过真实回环 HTTP 验证正常／空数据、访客、异常结构、时区、错误重试、401／403、退出及并发迟到响应。原生验收使用 `DailyRecommendationIntegrationRunner`，只允许 `.dailycheck` 包及 `http://127.0.0.1:18080/api/v1/`，账号密码由运行参数提供，不连接云端。构建参数：

```powershell
.\gradlew.bat assembleDebug assembleDebugAndroidTest '-PFOODTIME_DEBUG_SUFFIX=.dailycheck' '-PFOODTIME_API_BASE_URL=http://127.0.0.1:18080/api/v1/' '-PFOODTIME_TEST_RUNNER=cn.edu.bjtu.foodtime.presentation.DailyRecommendationIntegrationRunner' --no-configuration-cache --console=plain
```

测试后端需包含每日推荐 V38 和最新管理看板变更。开发分支交付不改变版本号与生产 API 配置。验收结果见项目根目录 `docs/每日推荐接通验收-20261104.md`。
