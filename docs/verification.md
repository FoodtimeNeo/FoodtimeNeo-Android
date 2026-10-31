# 验收记录

## 2026-10-31 密码输入遮罩修复

`Ui.field` 原先在设置密码输入类型后调用 `setSingleLine`，导致密码转换器被覆盖。现于单行设置完成后显式安装 `PasswordTransformationMethod`，同时覆盖登录、注册及修改密码的公共输入框；输入原值仍用于提交，密码不写入 View 状态或草稿。

架构检查、JVM 单元测试、Lint、Debug APK 和测试 APK 构建通过。Android 16 模拟器的 `PasswordInputRunner` 37 项原生检查通过，直接构建登录、注册和修改密码表单，验证五个密码字段的遮罩、密码无障碍标识、原值保留、单行类型和禁用状态保存，并确认邮箱不被遮罩。表单不提交，不登录或修改云端账号。

复现时构建 `assembleDebug assembleDebugAndroidTest -PFOODTIME_DEBUG_SUFFIX=.maskcheck -PFOODTIME_API_BASE_URL=http://127.0.0.1:8080/api/v1/ -PFOODTIME_TEST_RUNNER=cn.edu.bjtu.foodtime.presentation.PasswordInputRunner`，安装后执行 `adb shell am instrument -w cn.edu.bjtu.foodtime.maskcheck.test/cn.edu.bjtu.foodtime.presentation.PasswordInputRunner`；该隔离包不需要启动 Backend。

## 2026-10-29 IP HTTPS 连接复验

默认 API 已从 `http://154.9.252.201/api/v1/` 改为 `https://154.9.252.201/api/v1/`。服务器原来的 HTTP IP 入口返回跳转域名的 301；客户端禁止自动跳转，因此收到 HTML 而非 API JSON。现已部署匹配 IP 的可信证书与独立 HTTPS API 入口，HTTP 只跳到同一 IP 的 HTTPS 地址。旧安装包仍需更新；协议变化会使旧来源会话失效，用户需重新登录。

本轮架构检查、JVM 单元测试、Lint、Debug APK 和测试 APK 构建通过。API 36.1 模拟器运行独立 `.ipcheck` 包的 `IpConnectionRunner`，验证 Android TLS、真实 ping JSON、Android Keystore 加密 Cookie 存储、Secure/HttpOnly Cookie 以及匿名会话复用通过。仅发送 GET，无账号登录或业务写入；不将匿名会话检查等同于完整登录验收。

服务器 Nginx 配置检查、IP 公网 TLS 校验、域名入口回归及 IP 证书续期 dry-run（含 Nginx reload hook）通过。已启用每天两次的独立续期 timer。原始部署脚本、Nginx 配置与 APK 位于联合工作区 `qa/ip-connection-20261029/`。

## 2026-10-21 AI审核信息对齐

feature/moderation-ai-details补齐投稿审核及正式评论管理的服务端AI字段与只读展示。架构、11项JVM测试、lintDebug和构建通过；扩展DishReviewPagingRunner后共21项原生HTTP/UI检查通过，新增验证中文风险、置信度95%及0%、标签、上海时间、NULL结论、保留人工决策和普通页面隔离。测试使用模拟器内部受控HTTP服务，不向线上或真实本地内容写入。

## 2026-10-21 一级评论按需分页

feature/dish-review-pagination将公开菜品评论改为每页20条。架构、11项JVM测试、lintDebug和Debug/AndroidTest构建通过；DishReviewPagingRunner在API36.1模拟器通过15项真实RemoteRepository HTTP/原生界面检查，覆盖首屏仅一页、分页失败与相同页重试、末页停止、非法响应保留原缓存、点赞缓存、追加保留回复草稿、空列表与刷新后的NULL评分/0计数。HTTP来自模拟器内部受控服务，不向真实Backend写入。

## 2026-10-21 楼层读取队列回归

feature/review-read-queue修复多楼恢复时后续请求被全局写互斥丢弃的问题。架构、11项JVM测试、lintDebug和Debug/AndroidTest构建通过。独立 `.completion` 包在API36.1模拟器运行ReviewReadQueueRunner，12项原生检查通过：双楼恢复、重绘订阅去重、加载反馈结束、保存状态后多个回调、失败隔离与重试、串行执行和重复写保护、读取期间导航及旧页面回调隔离。受控仓库不写Backend或用户数据，原.integration安装保留。

## 2026-10-21 Release APK 与CD

在 `origin/dev 9f04715` 基础上使用 `feature/release-apk-cd`，仅修改交付配置与文档。CD新增main手动发布入口、签名校验、APK与SHA-256双产物、30天Actions artifact和完整附件上传后公开Release的流程。

本机JDK21、SDK36下，使用首次发布的固定RSA3072证书运行 `verifyArchitecture testDebugUnitTest lintRelease assembleRelease --no-configuration-cache` 成功，11项JVM测试全部通过。生成 `app/build/outputs/apk/release/app-release.apk`，`apksigner verify --verbose --print-certs` 验证通过，APK包名 `cn.edu.bjtu.foodtime`，versionName仍为0.1.0、versionCode为1；本次要求的v1.1.0版本号仅用于Web仓库。没有改动客户端业务代码或运行线上写入测试。

actionlint 1.7.12静态校验CI及CD工作流通过。四项Actions签名Secrets已配置，签名备份被Git忽略并限制本机用户访问；不在文档中保存密码或私钥。签名证书SHA-256为 `94e7db3a30937a0cd8bc8596f80666122e3bd3f3323e55bfef14f72e9c454b92`。实际远端CD和Release下载结果以发布PR及Actions为准。

## 2026-10-16 个人记录体验

个人入口区分已发布评论和投稿审核记录；空评论页说明审核流程，并提供进入“我的提交”的按钮。投稿记录新增类型筛选、实际匹配数和清除筛选，审核状态与类型按路由恢复。筛选只处理当前用户已读取的四类真实投稿，不增加服务器接口或修改数据。

架构检查、11项JVM测试、Lint及Debug构建通过，Lint为0错误，保留原有API33属性兼容提示。安装到API36.1模拟器并保留线上会话，检查正常字体竖屏下的个人入口、空评论引导、已通过状态的零结果、切换栏目后保留筛选及清除恢复。没有在线上提交、上传、删除或审核内容；大字体、横屏和其他Android版本未在本轮重新验证。

## 2026-10-16 线上接入与目录体验

默认API已改为 `http://154.9.252.201/api/v1/`。API36.1模拟器由用户登录线上账号后显示真实4个食堂，未配置ADB端口映射。食堂、档口和菜品复用名称搜索、实际匹配计数、清除与零结果提示，关键词按路由保存；纯Java匹配支持全角、大小写和多关键词。

没有封面的目录改为文字卡片，4个食堂可在本轮竖屏首屏完整显示；有封面的档口改为80dp缩略图横排，学四食堂的长档口名称可完整换行。空宽图占位缩短为120dp。架构检查、11项JVM测试、Lint及Debug构建通过，已安装保留线上会话。线上只执行登录和浏览，没有提交评论、投稿、审核或运行会写入数据的设备集成测试；旧设备集成验收结果仍按下文历史范围说明。

## 2026-10-14 本地隔离验收

本轮应用默认使用真实Backend，经ADB reverse以127.0.0.1:8080通信。没有注入LocalRepository，旧离线仓库已移到testShared，不进入APK。

- 架构检查、8项JVM测试、Lint、Debug APK及设备测试包构建通过。
- RemoteRepositoryRunner：最终22项真实设备检查通过，覆盖JSON契约、Keystore加密Cookie恢复与服务来源绑定、三级目录、评论审批/删除、每日评分快照、图片私有读取/驳回原因、无价格菜品补价、档口投稿、日志及角色更新。
- RemoteUiRunner：正常字体及最终1.3倍字体各19项检查通过，操作实际登录/栏目导航与滚动恢复/评论提交审核删除/后台图片导入与投稿/用户禁用恢复/三级目录新增编辑删除/改密注销和新密码登录；提交过程中重建Activity，恢复结果且只写入一次。
- 已检查最终详情、资料、登录、投稿和用户管理截图，无横向溢出或文字重叠；字体设置随后恢复1.0。Lint为0错误，仅有旧版忽略API33返回属性的一条非阻断提示；API26–32另有返回兼容路径。
- 金额显示在JSON解析后仍保持两位小数；个人投稿价格为null时显示“价格未填写”，不会假设已有价格。
- 设备使用独立.integration包名，保留原来不同签名安装及数据。截图位于设备私有files/remote-ui-evidence目录，读取需adb exec-out run-as；最终本机副本在build/quality-large-font-evidence。检查APK dex确认不含LocalRepository或foodtime_mock_v1，Java新版字符串操作由构建工具生成回退实现，不是直接调用API33的String方法。

系统图片选择器供应商的具体界面仍需真机体验；自动验证了原生选图请求及回传URI后的真实导入、压缩、上传和投稿。未执行API26–32设备运行；保留对应返回路径。正式邮箱投递、点赞/收藏/分类不在本轮验收范围，依据用户明确确认。

以下2026-10-13记录仅为历史离线阶段，不能用其53项结果证明当前后端已接通：
## 2026-10-13历史记录

## 构建与测试

- Android 原生 Java 17 / View，AGP 9.0.0、Gradle 9.1.0、compile/target SDK 36、min SDK 26。
- `assembleDebug`、`assembleDebugAndroidTest`、`testDebugUnitTest` 成功。
- 5 项 JVM 单元测试全部通过：邮箱规范化与边界、域名伪装、密码规则、精确金额、加盐密码匹配。
- `lintDebug` 成功，0 错误。8 条非阻断警告：API 33 返回属性在低版本被忽略（低版本有 onBackPressed 回退）、本地图片 decode 的保守分配提示、6 处演示账号填入 setText 的国际化提示。未建立 lint baseline 或禁用错误检测。
- API 36.1 模拟器，390dp 视口，font_scale=1.0 与 1.3：53 项设备验收断言全部通过。
- 原生选图请求的 action/type/category 与回传后的图片读写/压缩已自动验证；系统文件选择器的具体供应商界面仍建议真机手动体验。
- `git diff --check` 无问题。全部功能分支按顺序以非快进合并至 dev。

## 设备验收覆盖

测试数据使用独立临时 SharedPreferences，真实体验账号数据保持完整。

1. Web 食堂素材与三级目录、无图菜品。
2. 登录密码错误、普通用户角色、持久化会话、注册后不自动登录、验证码错误。
3. 评论 pending 不公开、重复 pending 拒绝、审核发布、同一用户/菜品更新唯一、自己的评论删除及他人评论保护。
4. 图片 pending 不公开、通过后进入图册；菜品投稿通过后进入正确档口且金额精确、图片关联正确；档口投稿通过后进入正确食堂。
5. 驳回原因必填并回传、审核不能重复执行、审核日志记录。
6. 禁用父食堂同时阻断档口/菜品展示；管理员/平台管理员权限边界；停用用户不能登录。
7. 旧密码错误、改密会话失效、旧密码不可再登录。
8. 重复当前 Tab 不重置、切换 Tab 保留详情、返回保留档口上下文、草稿写入 Activity 状态。
9. 食堂、档口、菜品、详情、评论、个人中心、图片上传、个人投稿、审核、登录界面实际渲染。
10. 系统选图请求与图片 URI 回传，私有保存及尺寸限制。

## 视觉对齐

已检查实际设备截图，完成正文400/衬线标题900字重修正；手写字仅用于短句。打包完整中文字体，避免用户新增名称/评论出现缺字。Android 卡片无 elevation，采用4dp实色偏移阴影、1dp墨棕描边、直角；纸张与品牌色直接来自 Web tokens。

正文≥14sp、辅助≥12sp、触控目标≥48dp，页面边距14dp、卡片内边距16dp、区块间距20dp。16:9 菜品图右上“+”、右下“图册”；未上传时有明确缺省文案。列表不额外显示评分/营业时间/介绍，菜品列表不放图片。

390dp 和1.3倍字体下检查了标题、评分、星级表单、个人ID、菜单和底栏，没有横向滚动或文本相互重叠；长页面继续纵向滚动。底栏为独立布局，内容不会被覆盖。无自动轮播或位移动画。

## 演示边界

本次没有连接真实后端。登录、验证码、审核和跨账号数据仅在一台设备模拟；没有邮件、远程图片存储或跨设备同步。后端评分物化视图24小时刷新，本地评分为即时重算。

未执行真实设备/旧版Android运行测试；API26–32在代码中保留系统返回兼容路径。首版没有扩展较早文档中的收藏、点赞、消息、社区等增强项；FEATURE规定的核心功能与管理入口已实现。

接入步骤和已存在/待新增接口见 `backend-integration.md`。可安装包为 debug 签名演示 APK，正式签名和发布由后续交付配置完成。

## 2026-10-20 实时评分

feature/realtime-dish-ratings 维持服务端评分来源并更新“实时统计”说明，进入详情继续通过worker刷新真实汇总。verifyArchitecture testDebugUnitTest lintDebug assembleDebug --no-configuration-cache 全部通过。未运行设备写入验收；本轮后端测试使用专用本地库。

## 2026-10-20 评论点赞

新增真实服务端点赞/取消入口，使用响应直接更新按钮，不刷新整页。verifyArchitecture testDebugUnitTest lintDebug assembleDebug通过；未执行设备运行或线上写入。

## 2026-10-20 点评回复

verifyArchitecture testDebugUnitTest lintDebug assembleDebug --no-configuration-cache全部通过。Lint发现的API26不支持URLEncoder Charset重载已改为服务端Base64URL游标校验，保持最低版本兼容。未运行设备写入验收。

## 2026-10-20 回复点赞与最终检查

verifyArchitecture testDebugUnitTest lintDebug assembleDebug --no-configuration-cache全部通过，11项单元测试无失败。修复Stream.toList对API34的要求，保持minSdk26；回复点赞直接更新按钮及缓存。未运行设备/模拟器写入验收。

## 2026-10-21 嵌入式楼中楼

基于 origin/dev 28f10f7，在 feature/embedded-review-replies 开发。Java/Gradle/SDK沿用本地联调环境，verifyArchitecture testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --no-configuration-cache全部通过；11项JVM测试通过，Lint无错误。

FoodtimeLocal（API36.1、411dp竖屏）安装独立.integration调试包，专用 EmbeddedRepliesRunner 完成20项原生组件检查：展开前无取数、每楼一页、停留菜品路由、删除对象占位、按需编辑器、收起保留草稿与对象、失败恢复输入、同ID重试、待审隔离、点赞不重载、折叠保留最新点赞，以及Activity保存草稿、新的一级点评回复数使旧缓存刷新。另将操作组件在248dp可用宽度及1.3倍文字尺寸下实际测量/布局，检查换行、无越界和48dp触控高度。

测试替换进程内数据源，不写后端、不修改联调账号和密码，结束后恢复真实数据源。新增测试发现快速失败可能早于输入框禁用，已将禁用放到提交前，重跑通过。已查看原生楼层和编辑器截图（gitignored同级Backend tmp/local-run/01-inline-thread.png、02-inline-composer.png），确认层级与按钮一致。测试包及截图使用明确测试内容，不能代替真实接口写入联调。本轮未执行真机、旧版系统、完整横屏或最大系统字号验收；未改数据库或后端API。

复现时构建参数增加 -PFOODTIME_TEST_RUNNER=cn.edu.bjtu.foodtime.presentation.EmbeddedRepliesRunner，并用 adb shell am instrument -w cn.edu.bjtu.foodtime.integration.test/cn.edu.bjtu.foodtime.presentation.EmbeddedRepliesRunner 执行。该runner独立于会修改账号/数据的旧全量联调runner。

## 2026-10-21 撤回后的修复与完整版本验收

首次 0.1.0 main 发布已通过保留历史的回退提交 `7d7f696` 撤回，旧 Release 转草稿并暂停 CD。开发版保留全部功能，在最新 dev 上分别完成 `feature/review-read-queue`、`feature/dish-review-pagination`、`feature/moderation-ai-details`，再以 `feature/review-release-readiness` 做累计验收、记录回退祖先及准备 0.1.1（versionCode 2）。固定签名不变。

最终 verifyArchitecture、11 项 JVM 测试、lintDebug、Debug/测试 APK 构建通过；actionlint 通过。API 36.1 独立 `.completion` 包设备结果：

- `ReviewReadQueueRunner`：12 项通过，涵盖两楼恢复、同楼去重、多个延期回调、读取失败、写互斥和旧页面回调隔离。
- `EmbeddedRepliesRunner`：20 项通过，涵盖嵌入楼层、编辑器、草稿、回复对象、失败重试、点赞、删除对象占位和保存状态。
- `DishReviewPagingRunner`：21 项真实 Android HTTP/JSON 和原生 UI 检查通过；375×667dp / 1 倍字号竖屏，以及 667×375dp / 2 倍字号横屏均通过。后者同时开启系统夜间模式、关闭 animator 动画，应用保持现有纸张主题。检查实际截图中的分页保留草稿、AI 长原因换行及层级；大字号标题增加单行省略以避免挤占导航按钮。结束恢复模拟器原尺寸、密度、字号、旋转及夜间模式。
- `ReviewInteractionsRunner`：最终版本连接独立 `foodtime_completion_lab`，21 项真实 Backend/原生交互检查通过：提交主评与回复、原生人工审核、评论/回复点赞与取消、我的回复、指定回复对象、父回复软删除、驳回原因、主评删除及实时评分重算。写入仅为本测试新建菜品和评论，不使用原本地库或生产服务。

累计回归修正了测试替身缺少首页图片空值处理，以及配置切换重建后测试仍等待旧 Activity 的问题；未把这些测试编排故障描述成业务崩溃修复。分页 runner 跟踪当前 Activity，并在保存截图前等待布局及画面呈现。

三端汇总与截图保存在工作区 `docs/review-completion-2026-10-21.md`。本地验收不能证明所有真机/API26设备、长时间压力、真实邮件或真实 AI 提供方已验证；生产应用与 V15–V18 数据迁移仍需在服务器部署时执行。记录中的“实时”是事务更新、下一次请求可见，没有引入轮询或 WebSocket。


## 2026-10-23 图文社区与紧凑楼中楼验收

从最新dev逐项开发community-posts、community-replies、community-interactions、community-moderation，并在community-validation完成联调。架构检查、16项JVM测试、lintDebug、Debug与设备测试包构建通过。

真实本机服务验收：CommunityRepositoryRunner 21项、CommunityUiRunner 12项均通过；图库只读验收2项通过。覆盖真实多图发布、去重、审核与私有可见性、楼内互相回复、单层缩进、点赞收藏、删除占位和带原因下架。截图复核修正了设备时区导致的时间差异，社区时间统一为北京时间。

设备验收参数从调用方提供，不在测试源码内嵌账号密码。截图保存在测试应用私有的community-ui-evidence目录；本次真实提供方审核以独立后端自动测试及项目已有配置说明为界。
