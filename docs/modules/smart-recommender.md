# 精准选餐与单份预算多维搜菜（Android 端）

## 1. 功能入口与路由

- **精准选餐专页**：路由 `smart-recommend`（`SmartRecommendScreen.java`），可从食堂首页顶部「今天吃什么 · 精准选餐」入口条或统一搜索发现页直接进入。
- **统一搜菜增强**：`UnifiedSearchScreen.java` 筛选弹窗新增价格快捷预算、`仅看按份或碗计价（portionOnly）`、`品类/标签（tags）` 与 `忌口排除（excludeTags）`；筛选草稿写入 `MainActivity.drafts`，从菜品详情返回自动恢复条件与分页。
- **菜品详情标签展示**：`DishScreen.java` 在菜品详情卡片展示结构化中文标签；`UnifiedSearchScreen.java` 在搜索菜品卡展示标签摘要，并在零命中时提供「放宽筛选条件重试」与跳转精准选餐入口。

## 2. 分层与接口映射

- `domain/model/DishTagModels.java`、`domain/model/SearchModels.java`、`domain/model/Models.java`、`domain/repository/SearchRepository.java`：纯 Java 领域模型与契约，无 `android.*` 依赖。
- `data/remote/RemoteRepository.java`：对接 `GET /api/v1/dishes/tags`、`POST /api/v1/recommendations/dishes/query`，并在 `GET/POST /api/v1/search/stalls` 与 `dishes` 序列化 `portionOnly`、`tags`、`excludeTags` 且解析菜品 `tags`。
